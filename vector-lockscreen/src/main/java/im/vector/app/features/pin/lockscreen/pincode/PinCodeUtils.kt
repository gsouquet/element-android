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

import im.vector.app.features.pin.lockscreen.crypto.KeyHelper

/**
 * Manages the PIN code related actions.
 */
class PinCodeUtils(
        private val keyHelper: KeyHelper,
        private val storageEncrypted: EncryptedPinCodeStorage,
) {

    suspend fun isPinCodeEnabled() = keyHelper.hasPinCodeKey() && storageEncrypted.getPinCode() != null

    suspend fun createPinCode(pinCode: String) {
        val pinCodeCrypto = keyHelper.getPinCodeKey()
        val encryptedValue = pinCodeCrypto.encryptToString(pinCode)
        storageEncrypted.savePinCode(encryptedValue)
    }

    suspend fun verifyPinCode(pinCode: String): Boolean {
        val encryptedPinCode = storageEncrypted.getPinCode() ?: return false
        val pinCodeCrypto = keyHelper.getPinCodeKey()
        return pinCodeCrypto.decryptToString(encryptedPinCode) == pinCode
    }

    suspend fun deletePinCode(): Boolean {
        storageEncrypted.deletePinCode()
        return keyHelper.deletePinCodeKey()
    }

}

