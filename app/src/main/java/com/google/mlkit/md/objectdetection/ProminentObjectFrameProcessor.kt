/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md.objectdetection

import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.md.*
import com.google.mlkit.md.camera.*
import com.google.mlkit.md.camera.WorkflowModel.WorkflowState
import com.google.mlkit.md.settings.PreferenceUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*

/** A processor to run object detector in prominent object only mode.  */
class ProminentObjectFrameProcessor(
        graphicOverlay: GraphicOverlay,
        private val workflowModel: WorkflowModel) :
    FrameProcessor {

    // To keep the latest frame and its metadata.
    private var latestFrame: ByteBuffer? = null
    private var latestFrameMetaData: FrameMetadata? = null

    // To keep the frame and metadata in process.
    private var processingFrame: ByteBuffer? = null
    private var processingFrameMetaData: FrameMetadata? = null

    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

    /** Processes the input frame with the underlying detector.  */
    @Synchronized
    override fun process(
            data: ByteBuffer,
            frameMetadata: FrameMetadata,
            graphicOverlay: GraphicOverlay
    ) {
        latestFrame = data
        latestFrameMetaData = frameMetadata
        if (processingFrame == null && processingFrameMetaData == null) {
            processLatestFrame(graphicOverlay)
        }
    }

    @Synchronized
    private fun processLatestFrame(graphicOverlay: GraphicOverlay) {
        processingFrame = latestFrame
        processingFrameMetaData = latestFrameMetaData
        latestFrame = null
        latestFrameMetaData = null
        val frame = processingFrame ?: return
        val frameMetaData = processingFrameMetaData ?: return
        val image = InputImage.fromByteBuffer(
                frame,
                frameMetaData.width,
                frameMetaData.height,
                frameMetaData.rotation,
                InputImage.IMAGE_FORMAT_NV21
        )
        val startMs = SystemClock.elapsedRealtime()
        detectInImage(image)
                .addOnSuccessListener(executor) { results: List<DetectedObject> ->
                    Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs} " + results)
                    this.onSuccess(CameraInputInfo(frame, frameMetaData), results, graphicOverlay)
                    processLatestFrame(graphicOverlay)
                }
                .addOnFailureListener(executor) { e -> OnFailureListener {
                    Log.e(TAG, "Object detection failed!", it)
                } }
    }



    private val detector: ObjectDetector
    private val confirmationController: ObjectConfirmationController = ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val reticleOuterRingRadius: Int = graphicOverlay
            .resources
            .getDimensionPixelOffset(R.dimen.object_reticle_outer_ring_stroke_radius)

    init {
        val options: ObjectDetectorOptionsBase
        val isClassificationEnabled = PreferenceUtils.isClassificationEnabled(graphicOverlay.context)
        /*
        if (customModelPath != null) {
            val localModel = LocalModel.Builder()
                .setAssetFilePath(customModelPath)
                .build()
            options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification() // Always enable classification for custom models
                .build()
        } else {
         */
            val optionsBuilder = ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            if (isClassificationEnabled) {
                optionsBuilder.enableClassification()
            }
            options = optionsBuilder.build()
        //}

        this.detector = ObjectDetection.getClient(options)
    }

    override fun stop() {
        executor.shutdown()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }
    }

    fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    @MainThread
    fun onSuccess(
        inputInfo: InputInfo,
        results: List<DetectedObject>,
        graphicOverlay: GraphicOverlay
    ) {
        var objects = results
        if (!workflowModel.isCameraLive) {
            return
        }
        for (i in objects.indices) {
            val result = objects[i]

            fun l(labels: List<DetectedObject.Label>): String {
                return labels.map { l -> "l" + l.index + ":" + l.text + l.index }.toString()
            }

            Log.d("XXX", "XXX Res $i ${result.trackingId} ${result.boundingBox} lab=${l(result.labels)} ")
        }

        if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            val qualifiedObjects = ArrayList<DetectedObject>()
            qualifiedObjects.addAll(objects)
            objects = qualifiedObjects
        }

        val objectIndex = 0
        val hasValidObjects = objects.isNotEmpty()
        if (!hasValidObjects) {
            confirmationController.reset()
            workflowModel.setWorkflowState(WorkflowState.DETECTING)
        } else {
            val visionObject = objects[objectIndex]
            if (objectBoxOverlapsConfirmationReticle(graphicOverlay, visionObject)) {
                // User is confirming the object selection.
                confirmationController.confirming(visionObject.trackingId)
                workflowModel.confirmingObject(
                        DetectedObjectInfo(visionObject, inputInfo), confirmationController.progress
                )
            } else {
                // Object detected but user doesn't want to pick this one.
                confirmationController.reset()
                workflowModel.setWorkflowState(WorkflowState.DETECTED)
            }
        }

        graphicOverlay.clear()
        if (!hasValidObjects) {
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            if (objectBoxOverlapsConfirmationReticle(graphicOverlay, objects[0])) {
                // User is confirming the object selection.
                cameraReticleAnimator.cancel()
                graphicOverlay.add(
                        ObjectGraphicInProminentMode(
                                graphicOverlay, objects[0], confirmationController
                        )
                )
                if (!confirmationController.isConfirmed) {
                    // Shows a loading indicator to visualize the confirming progress if in auto search mode.
                    graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))
                }
            } else {
                // Object is detected but the confirmation reticle is moved off the object box, which
                // indicates user is not trying to pick this object.
                graphicOverlay.add(
                        ObjectGraphicInProminentMode(
                                graphicOverlay, objects[0], confirmationController
                        )
                )
                graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
                cameraReticleAnimator.start()
            }
        }
        graphicOverlay.invalidate()
    }

    private fun objectBoxOverlapsConfirmationReticle(
        graphicOverlay: GraphicOverlay,
        visionObject: DetectedObject
    ): Boolean {
        val boxRect = graphicOverlay.translateRect(visionObject.boundingBox)
        val reticleCenterX = graphicOverlay.width / 2f
        val reticleCenterY = graphicOverlay.height / 2f
        val reticleRect = RectF(
                reticleCenterX - reticleOuterRingRadius,
                reticleCenterY - reticleOuterRingRadius,
                reticleCenterX + reticleOuterRingRadius,
                reticleCenterY + reticleOuterRingRadius
        )
        return reticleRect.intersect(boxRect)
    }

    companion object {
        private const val TAG = "ProminentObjProcessor"
    }
}
