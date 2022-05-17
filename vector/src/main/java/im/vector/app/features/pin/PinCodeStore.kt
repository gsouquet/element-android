/*
 * Copyright (c) 2020 New Vector Ltd
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

package im.vector.app.features.pin

import android.content.SharedPreferences
import androidx.core.content.edit
import im.vector.lockscreen.pincode.EncryptedPinCodeStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface PinCodeStore: EncryptedPinCodeStorage {

    /**
     * Returns the remaining PIN code attempts. When this reaches 0 the PIN code access won't be available for some time.
     */
    fun getRemainingPinCodeAttemptsNumber(): Int

    /**
     * Returns the remaining biometric auth attempts. When this reaches 0 the biometric access won't be available for some time.
     */
    fun getRemainingBiometricsAttemptsNumber(): Int

    /**
     * Should decrement the number of remaining PIN code attempts.
     * @return The remaining attempts.
     */
    fun onWrongPin(): Int

    /**
     * Should decrement the number of remaining biometric attempts.
     * @return The remaining attempts.
     */
    fun onWrongBiometrics(): Int

    /**
     * Resets the counter of attempts for PIN code and biometric access.
     */
    fun resetCounters()

    /**
     * Adds a listener to be notified when the PIN code us created or removed.
     */
    fun addListener(listener: PinCodeStoreListener)

    /**
     * Removes a listener to be notified when the PIN code us created or removed.
     */
    fun removeListener(listener: PinCodeStoreListener)
}

interface PinCodeStoreListener {
    fun onPinSetUpChange(isConfigured: Boolean)
}

@Singleton
class SharedPrefPinCodeStore @Inject constructor(private val sharedPreferences: SharedPreferences) : PinCodeStore, EncryptedPinCodeStorage {
    private val listeners = mutableSetOf<PinCodeStoreListener>()

    override suspend fun getPinCode(): String? {
        return sharedPreferences.getString(ENCODED_PIN_CODE_KEY, null)
    }

    override suspend fun savePinCode(pinCode: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit {
                putString(ENCODED_PIN_CODE_KEY, pinCode)
            }
        }
        listeners.forEach { it.onPinSetUpChange(isConfigured = true) }
    }

    override suspend fun deletePinCode() {
        withContext(Dispatchers.IO) {
            // Also reset the counters
            resetCounters()
            sharedPreferences.edit {
                remove(ENCODED_PIN_CODE_KEY)
            }
        }
        listeners.forEach { it.onPinSetUpChange(isConfigured = false) }
    }

    override suspend fun hasEncodedPin(): Boolean {
        return withContext(Dispatchers.IO) { sharedPreferences.contains(ENCODED_PIN_CODE_KEY) }
    }


    override fun getRemainingPinCodeAttemptsNumber(): Int {
        return sharedPreferences.getInt(REMAINING_PIN_CODE_ATTEMPTS_KEY, MAX_PIN_CODE_ATTEMPTS_NUMBER_BEFORE_LOGOUT)
    }

    override fun getRemainingBiometricsAttemptsNumber(): Int {
        return sharedPreferences.getInt(REMAINING_BIOMETRICS_ATTEMPTS_KEY, MAX_BIOMETRIC_ATTEMPTS_NUMBER_BEFORE_FORCE_PIN)
    }

    override fun onWrongPin(): Int {
        val remaining = getRemainingPinCodeAttemptsNumber() - 1
        sharedPreferences.edit {
            putInt(REMAINING_PIN_CODE_ATTEMPTS_KEY, remaining)
        }
        return remaining
    }

    override fun onWrongBiometrics(): Int {
        val remaining = getRemainingBiometricsAttemptsNumber() - 1
        sharedPreferences.edit {
            putInt(REMAINING_BIOMETRICS_ATTEMPTS_KEY, remaining)
        }
        return remaining
    }

    override fun resetCounters() {
        sharedPreferences.edit {
            remove(REMAINING_PIN_CODE_ATTEMPTS_KEY)
            remove(REMAINING_BIOMETRICS_ATTEMPTS_KEY)
        }
    }

    override fun addListener(listener: PinCodeStoreListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: PinCodeStoreListener) {
        listeners.remove(listener)
    }

    companion object {
        private const val ENCODED_PIN_CODE_KEY = "ENCODED_PIN_CODE_KEY"
        private const val REMAINING_PIN_CODE_ATTEMPTS_KEY = "REMAINING_PIN_CODE_ATTEMPTS_KEY"
        private const val REMAINING_BIOMETRICS_ATTEMPTS_KEY = "REMAINING_BIOMETRICS_ATTEMPTS_KEY"

        private const val MAX_PIN_CODE_ATTEMPTS_NUMBER_BEFORE_LOGOUT = 3
        private const val MAX_BIOMETRIC_ATTEMPTS_NUMBER_BEFORE_FORCE_PIN = 5
    }
}
