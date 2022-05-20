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

package im.vector.app.features.pin.lockscreen.crypto

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Base64
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher

/**
 * Wrapper class to make working with KeyStore and keys easier.
 */
interface KeyStoreCrypto {

    /**
     * Alias that identifies the current keys.
     */
    val alias: String

    /**
     * Loads or creates and saves the keys needed inside the Android [KeyStore].
     * @throws KeyPermanentlyInvalidatedException if the key has been invalidated.
     */
    fun initialize()

    /**
     * Gets an initialized [Cipher] that can be wrapped using a [BiometricPrompt.CryptoObject] to be provided to [BiometricPrompt].
     */
    fun getInitializedCipher(): Cipher

    /**
     * Encrypts the [ByteArray] value passed using generated the crypto key.
     */
    fun encrypt(value: ByteArray): ByteArray

    /**
     * Encrypts the [String] value passed using generated the crypto key.
     */
    fun encrypt(value: String): ByteArray = encrypt(value.toByteArray())

    /**
     * Encrypts the [ByteArray] value passed using generated the crypto key.
     * @return A Base64 encoded String.
     */
    fun encryptToString(value: ByteArray): String = Base64.encodeToString(encrypt(value), Base64.NO_WRAP)

    /**
     * Encrypts the [String] value passed using generated the crypto key.
     * @return A Base64 encoded String.
     */
    fun encryptToString(value: String): String = Base64.encodeToString(encrypt(value), Base64.NO_WRAP)

    /**
     * Decrypts the [ByteArray] value passed using the generated crypto key.
     */
    fun decrypt(value: ByteArray): ByteArray

    /**
     * Decrypts the [String] value passed using the generated crypto key.
     */
    fun decrypt(value: String): ByteArray = decrypt(Base64.decode(value, Base64.NO_WRAP))

    /**
     * Decrypts the [ByteArray] value passed using the generated crypto key.
     * @return The decrypted contents in as a String.
     */
    fun decryptToString(value: ByteArray): String = String(decrypt(value))

    /**
     * Decrytps the [String] value passed using the generated crypto key.
     * @return The decrypted contents in as a String.
     */
    fun decryptToString(value: String): String = String(decrypt(value))

    /**
     * Check if the key associated with the [alias] is valid.
     */
    fun hasValidKey(): Boolean {
        val hasKey = keyStore.containsAlias(alias)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                initialize()
                hasKey
            } catch (e: KeyPermanentlyInvalidatedException) {
                false
            }
        } else hasKey

    }

    companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"

        /**
         * Checks if the Android KeyStore contains the passed [alias].
         */
        fun containsKey(alias: String) = runCatching {
            keyStore.containsAlias(alias)
        }.getOrDefault(false)

        /**
         * Removes the passed [alias] from the Android KeyStore.
         */
        fun deleteKey(alias: String) = runCatching {
            keyStore.deleteEntry(alias)
        }.isSuccess

        private val keyStore by lazy { KeyStore.getInstance(ANDROID_KEY_STORE).also { it.load(null) } }
    }

}
