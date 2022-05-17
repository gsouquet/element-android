/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("DEPRECATION")

package im.vector.lockscreen.utils

import android.content.Context
import android.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import im.vector.lockscreen.pincode.EncryptedPinCodeStorage
import javax.inject.Inject

class EncryptedPinCodeSharedPreferencesStorage @Inject constructor(
        @ApplicationContext private val context: Context,
): EncryptedPinCodeStorage {

    private val sharedPreferences by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    override suspend fun getPinCode(): String? = sharedPreferences.getString(KEY_PIN_CODE, null)

    override suspend fun savePinCode(pinCode: String) {
        sharedPreferences.edit().putString(KEY_PIN_CODE, pinCode).apply()
    }

    override suspend fun deletePinCode() {
        sharedPreferences.edit().remove(KEY_PIN_CODE).apply()
    }

    override suspend fun hasEncodedPin(): Boolean {
        return sharedPreferences.contains(KEY_PIN_CODE)
    }

    companion object {
        private const val KEY_PIN_CODE = "vector.encrypted_pin_code"
    }
}
