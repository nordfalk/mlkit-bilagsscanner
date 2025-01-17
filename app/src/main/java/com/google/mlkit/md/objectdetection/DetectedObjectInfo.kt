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

import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.util.Log
import com.google.mlkit.md.CameraInputInfo
import com.google.mlkit.vision.objects.DetectedObject
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Holds the detected object and its related image info.
 */
class DetectedObjectInfo(
    val detectedObject: DetectedObject,
    val inputInfo: CameraInputInfo
) {
    private var jpegBytes: ByteArray? = null

    override fun toString(): String {
        return "" + detectedObject.boundingBox  + " " +  detectedObject.labels + " " + inputInfo.getBitmap().width + "x" + inputInfo.getBitmap().height
    }

    @Suppress("unused")
    val imageData: ByteArray?
        @Synchronized get() {
            if (jpegBytes == null) {
                try {
                    ByteArrayOutputStream().use { stream ->
                        getBitmap().compress(CompressFormat.JPEG, /* quality= */ 100, stream)
                        jpegBytes = stream.toByteArray()
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Error getting object image data!")
                }
            }
            return jpegBytes
        }

    @Synchronized
    fun getBitmap(): Bitmap {

        var createdBitmap = Bitmap.createBitmap(
                inputInfo.getBitmap(),
                detectedObject.boundingBox.left,
                detectedObject.boundingBox.top,
                detectedObject.boundingBox.width(),
                detectedObject.boundingBox.height()
        )

        if (createdBitmap.width > MAX_IMAGE_WIDTH) {
            val dstHeight = (MAX_IMAGE_WIDTH.toFloat() / createdBitmap.width * createdBitmap.height).toInt()
            createdBitmap = Bitmap.createScaledBitmap(createdBitmap, MAX_IMAGE_WIDTH, dstHeight, /* filter= */ false)
        }

        if (false) {
            val lilleBm = Bitmap.createScaledBitmap(createdBitmap, 20, 20, /* filter= */ true)
            val dstHeight = (MAX_IMAGE_WIDTH.toFloat() / createdBitmap.width * createdBitmap.height).toInt()
            createdBitmap = Bitmap.createScaledBitmap(lilleBm, MAX_IMAGE_WIDTH, dstHeight, /* filter= */ false)
        }
        return createdBitmap
    }

    companion object {
        private const val TAG = "DetectedObject"
        private const val MAX_IMAGE_WIDTH = 640
    }
}
