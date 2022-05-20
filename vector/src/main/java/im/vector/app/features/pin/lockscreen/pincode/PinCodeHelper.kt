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

package im.vector.app.features.pin.lockscreen.pincode

import im.vector.app.features.pin.lockscreen.crypto.LockScreenKeyRepository

/**
 * A helper class to manage the PIN code creation, verification, removal and migration.
 */
class PinCodeHelper(
        private val lockScreenKeyRepository: LockScreenKeyRepository,
        private val encryptedStorage: EncryptedPinCodeStorage,
) {

    /**
     * Returns if PIN code is available (both the key exists and the encrypted value is stored).
     */
    suspend fun isPinCodeAvailable() = lockScreenKeyRepository.hasPinCodeKey() && encryptedStorage.getPinCode() != null

    /**
     * Creates a PIN code key if needed and stores the PIN code encrypted with it.
     */
    suspend fun createPinCode(pinCode: String) {
        val pinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        val encryptedValue = pinCodeCrypto.encryptToString(pinCode)
        encryptedStorage.savePinCode(encryptedValue)
    }

    /**
     * Verifies the passed [pinCode] against the encrypted one.
     */
    suspend fun verifyPinCode(pinCode: String): Boolean {
        val encryptedPinCode = encryptedStorage.getPinCode() ?: return false
        val pinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        return pinCodeCrypto.decryptToString(encryptedPinCode) == pinCode
    }

    /**
     * Deletes the store PIN code as well as the associated key.
     */
    suspend fun deletePinCode(): Boolean {
        encryptedStorage.deletePinCode()
        return lockScreenKeyRepository.deletePinCodeKey()
    }

    /**
     * Migrates from the legacy PIN code key to the new one.
     */
    suspend fun migrateFromLegacyKey() {
        if (!lockScreenKeyRepository.isUsingLegacyKey()) return
        val encryptedPinCode = encryptedStorage.getPinCode() ?: return

        // Get legacy PIN code crypto helper
        val pinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        // Decrypt PIN code
        val savedPinCode = pinCodeCrypto.decryptToString(encryptedPinCode)
        // Delete old key
        lockScreenKeyRepository.deletePinCodeKey()
        // Create new helper with the new key
        val newPinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        // Encrypt PIN code with the new key
        val newEncryptedPinCode = newPinCodeCrypto.encryptToString(savedPinCode)
        // Save the new encrypted PIN code
        encryptedStorage.savePinCode(newEncryptedPinCode)
    }

}

