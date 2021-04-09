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

package com.google.mlkit.md

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.md.settings.PreferenceUtils

/** Entry activity to select the detection mode.  */
class MainActivity : AppCompatActivity() {

    private enum class DetectionMode(val titleResId: String, val subtitleResId: String, val multi: Boolean) {
        ODT_LIVE1("Objectsøger - kun det dominerende objekt", "Interaktion med kasse om dominerende objekt", false),
        ODT_LIVE2("Objectsøger - vis alle objekter", "Interaktion ved at ramme 'klatten' ved objekt", true),
        CUSTOM_MODEL_LIVE("Fugle (kun den største)", "Interaktion med kasse om dominerende objekt", false),
        CUSTOM_MODEL_LIVE2("Fugle - vis alle fugle", "Interaktion ved at ramme 'klatten' ved objekt", true),
    }

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        setContentView(R.layout.activity_main)
        findViewById<RecyclerView>(R.id.mode_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = ModeItemAdapter(DetectionMode.values())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!Utils.allPermissionsGranted(this)) {
            Utils.requestRuntimePermissions(this)
        }
    }

    private inner class ModeItemAdapter(private val detectionModes: Array<DetectionMode>) :
        RecyclerView.Adapter<ModeItemAdapter.ModeItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModeItemViewHolder {
            return ModeItemViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.detection_mode_item, parent, false
                    )
            )
        }

        override fun onBindViewHolder(modeItemViewHolder: ModeItemViewHolder, position: Int) =
            modeItemViewHolder.bindDetectionMode(detectionModes[position])

        override fun getItemCount(): Int = detectionModes.size

        private inner class ModeItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            private val titleView: TextView = view.findViewById(R.id.mode_title)
            private val subtitleView: TextView = view.findViewById(R.id.mode_subtitle)

            fun bindDetectionMode(detectionMode: DetectionMode) {
                titleView.setText(detectionMode.titleResId)
                subtitleView.setText(detectionMode.subtitleResId)
                itemView.setOnClickListener {
                    val activity = this@MainActivity
                    PreferenceUtils.setMultipleObjectsMode(activity, detectionMode.multi)
                    when (detectionMode) {
                        DetectionMode.ODT_LIVE1, DetectionMode.ODT_LIVE2 ->
                            activity.startActivity(Intent(activity, LiveObjectDetectionActivity::class.java))
                        DetectionMode.CUSTOM_MODEL_LIVE, DetectionMode.CUSTOM_MODEL_LIVE2 ->
                            activity.startActivity(Intent(activity, CustomModelObjectDetectionActivity::class.java))
                    }
                }
            }
        }
    }
}
