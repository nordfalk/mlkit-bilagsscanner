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

package com.google.mlkit.md.settings

import android.content.Context
import android.preference.PreferenceManager
import androidx.annotation.StringRes
import com.google.mlkit.md.R

 /** Utility class to retrieve shared preferences.  */
object PreferenceUtils {

    fun isClassificationEnabled(context: Context): Boolean =
        getBooleanPref(context, R.string.pref_key_object_detector_enable_classification, false)

     fun getConfirmationTimeMs(context: Context): Int = getIntPref(context, R.string.pref_key_confirmation_time_in_auto_search, 1500)

    private fun getIntPref(context: Context, @StringRes prefKeyId: Int, defaultValue: Int): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(prefKeyId)
        return sharedPreferences.getInt(prefKey, defaultValue)
    }

    private fun getBooleanPref(context: Context, @StringRes prefKeyId: Int, defaultValue: Boolean): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(context.getString(prefKeyId), defaultValue)
}
