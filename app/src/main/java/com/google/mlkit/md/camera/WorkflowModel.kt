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

package com.google.mlkit.md.camera

import android.app.Application
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.mlkit.md.objectdetection.DetectedObjectInfo
import java.util.*

/** View model for handling application workflow based on camera preview.  */
class WorkflowModel(application: Application) : AndroidViewModel(application) {

    val workflowState = MutableLiveData<WorkflowState>()
    val searchedObject = MutableLiveData<DetectedObjectInfo>()

    private val objectIdsToSearch = HashSet<Int>()

    var isCameraLive = false
        private set

    private var confirmedObject: DetectedObjectInfo? = null

    /**
     * State set of the application workflow.
     */
    enum class WorkflowState {
        NOT_STARTED,
        DETECTING,
        DETECTED,
        CONFIRMING,
        SEARCHING,
        SEARCHED
    }

    @MainThread
    fun setWorkflowState(workflowState: WorkflowState) {
        if (workflowState != WorkflowState.SEARCHING &&
            workflowState != WorkflowState.SEARCHED
        ) {
            confirmedObject = null
        }
        this.workflowState.value = workflowState
    }

    @MainThread
    fun confirmingObject(confirmingObject: DetectedObjectInfo, progress: Float) {
        val isConfirmed = progress.compareTo(1f) == 0
        if (isConfirmed) {
            confirmedObject = confirmingObject
            setWorkflowState(WorkflowState.SEARCHING)
            triggerSearch(confirmingObject)
        } else {
            setWorkflowState(WorkflowState.CONFIRMING)
        }
    }

    private fun triggerSearch(detectedObject: DetectedObjectInfo) {
        val objectId = detectedObject.detectedObject.trackingId ?: throw NullPointerException()
        if (objectIdsToSearch.contains(objectId)) {
            // Already in searching.
            return
        }

        objectIdsToSearch.add(objectId)
        onSearchCompleted(detectedObject)
    }

    fun markCameraLive() {
        isCameraLive = true
        objectIdsToSearch.clear()
    }

    fun markCameraFrozen() {
        isCameraLive = false
    }

    fun onSearchCompleted(detectedObject: DetectedObjectInfo) {
        val lConfirmedObject = confirmedObject
        if (detectedObject != lConfirmedObject) {
            // Drops the search result from the object that has lost focus.
            return
        }

        objectIdsToSearch.remove(detectedObject.detectedObject.trackingId)
        setWorkflowState(WorkflowState.SEARCHED)

        searchedObject.value = lConfirmedObject
    }
}
