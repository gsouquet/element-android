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

package im.vector.lockscreen.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec

/**
 * Class in charge of managing both the PIN code key and the system authentication keys.
 */
class KeyHelper(
        private val context: Context,
        baseName: String,
) {

    private val pinCodeKeyAlias = "$baseName.pin_code"
    private val systemKeyAlias = "$baseName.system"

    init {
        // Rename pin code key from PFLockScreen to use the new alias
        LegacyKeyMigrator.migrateIfNeeded(newAlias = pinCodeKeyAlias)
    }

    /**
     * Get the key associated to the PIN code. It will be created if it didn't exist before.
     */
    fun getPinCodeKey(): KeyStoreCrypto = getKeyStore(pinCodeKeyAlias)

    /**
     * Get the key associated to the system authentication (biometrics). It will be created if it didn't exist before.
     * Note: this key will be invalidated by new biometric enrollments.
     */
    fun getSystemKey(keyGenParameterSpecBuilder: (KeyGenParameterSpec.Builder.() -> Unit)? = null): KeyStoreCrypto =
        getKeyStore(systemKeyAlias, keyGenParameterSpecBuilder = keyGenParameterSpecBuilder ?: {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setUserAuthenticationRequired(true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setInvalidatedByBiometricEnrollment(true)
            }
        })

    /**
     * Returns if the PIN code key already exists.
     */
    fun hasPinCodeKey() = KeyStoreCrypto.containsKey(pinCodeKeyAlias)

    /**
     * Returns if the system authentication key already exists.
     */
    fun hasSystemKey() = KeyStoreCrypto.containsKey(systemKeyAlias)

    /**
     * Deletes the PIN code key from the KeyStore.
     */
    fun deletePinCodeKey() = KeyStoreCrypto.deleteKey(pinCodeKeyAlias)

    /**
     * Deletes the system authentication key from the KeyStore.
     */
    fun deleteSystemKey() = KeyStoreCrypto.deleteKey(systemKeyAlias)

    /**
     * Checks if the current system authentication key exists and is valid.
     */
    fun isSystemKeyValid() = hasSystemKey() && getSystemKey().hasValidKey()

    /**
     * As we support Android APIs >= 21 we need to add a 'legacy' KeyStoreCrypto for devices in APIs < 23 where the new cryptographic APIs were added.
     */
    private fun getKeyStore(alias: String, keyGenParameterSpecBuilder: KeyGenParameterSpec.Builder.() -> Unit = {}): KeyStoreCrypto =
            KeyStoreCryptoCompat.create(context, alias, keyGenParameterSpecBuilder).also { it.initialize() }
}
