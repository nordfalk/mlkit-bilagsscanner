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

/** A processor to run object detector in prominent object only mode.  */
class ProminentObjectFrameProcessor(
        graphicOverlay: GraphicOverlay,
        private val workflowModel: WorkflowModel) :
    FrameProcessor {

    private var processing = false
    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

    /** Processes the input frame with the underlying detector.  */
    @Synchronized
    override fun process(
            frame: ByteBuffer,
            frameMetadata: FrameMetadata,
            graphicOverlay: GraphicOverlay
    ) {
        if (processing) { // skip frames if already processing
            return
        }
        processing = true
        val image = InputImage.fromByteBuffer(
                frame,
                frameMetadata.width,
                frameMetadata.height,
                frameMetadata.rotation,
                InputImage.IMAGE_FORMAT_NV21
        )
        val cameraInputInfo = CameraInputInfo(frame, frameMetadata)
        val startMs = SystemClock.elapsedRealtime()

        /*
        Log.d(TAG, "Bitmap is: ${cameraInputInfo.getBitmap()} ")

        println(cameraInputInfo.getBitmap())
        Log.d(TAG, "Bitmap Latency is: ${SystemClock.elapsedRealtime() - startMs} ")

         */

        detector.process(image)
                .addOnSuccessListener(executor) { results: List<DetectedObject> ->
                    processing = false
                    Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs} " + results)
                    this.onDetectionSuccess(cameraInputInfo, results, graphicOverlay)
                }
                .addOnFailureListener(executor) { e -> OnFailureListener {
                    processing = false
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
                    .setClassificationConfidenceThreshold(0.5f)
                    .setMaxPerObjectLabelCount(3)
                .enableClassification() // Always enable classification for custom models
                .build()
        } else {
         */
            val optionsBuilder = ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
        /*
*/
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

    @MainThread
    fun onDetectionSuccess(inputInfo: CameraInputInfo, results: List<DetectedObject>, graphicOverlay: GraphicOverlay) {
        if (!workflowModel.isCameraLive) {
            return
        }
//        inputInfo.getBitmap().scale()
        for (objectIndex in results.indices) {
            val result = results[objectIndex]
            fun l(labels: List<DetectedObject.Label>): String {
                return labels.map { l -> "l" + l.index + ":" + l.text + l.index }.toString()
            }

            Log.d("XXX", "XXX Res $objectIndex (tid=${result.trackingId}) ${result.boundingBox} lab=${l(result.labels)} ")
        }

        graphicOverlay.clear()
        if (results.isEmpty()) {
            confirmationController.reset()
            workflowModel.setWorkflowState(WorkflowState.DETECTING)
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            var objectIndex = 0
            for (oi in results.indices) {
                if (goodResult(inputInfo, results[oi])) {
                    objectIndex = oi
                    break
                }
            }

            if (objectBoxOverlapsConfirmationReticle(graphicOverlay, results[objectIndex])) {
                val result = results[objectIndex]
                // User is confirming the object selection.
                confirmationController.confirming(result.trackingId)
                workflowModel.confirmingObject(DetectedObjectInfo(result, inputInfo), confirmationController.progress)

                // User is confirming the object selection.
                cameraReticleAnimator.cancel()
                graphicOverlay.add(ObjectGraphicInProminentMode(graphicOverlay, results[objectIndex], confirmationController))
                if (!confirmationController.isConfirmed) {
                    // Shows a loading indicator to visualize the confirming progress if in auto search mode.
                    graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))
                }
            } else {
                // Object detected but user doesn't want to pick this one.
                confirmationController.reset()
                workflowModel.setWorkflowState(WorkflowState.DETECTED)

                // Object is detected but the confirmation reticle is moved off the object box, which
                // indicates user is not trying to pick this object.
                graphicOverlay.add(ObjectGraphicInProminentMode(graphicOverlay, results[0], confirmationController))
                graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
                cameraReticleAnimator.start()
            }
        }
        graphicOverlay.invalidate()
    }

    val good = HashMap<Int, Boolean>()
    private fun goodResult(inputInfo: CameraInputInfo, result: DetectedObject): Boolean {

        //DetectedObjectInfo(result, inputInfo).getBitmap()

        val res = good.get(result.trackingId)
        if (res!=null) return res


        return true
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
