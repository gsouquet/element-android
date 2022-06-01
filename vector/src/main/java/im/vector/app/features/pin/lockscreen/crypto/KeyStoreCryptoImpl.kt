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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import im.vector.app.features.pin.lockscreen.crypto.KeyStoreCrypto.Companion.ANDROID_KEY_STORE
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * Implementation of [KeyStoreCrypto] for Android [Build.VERSION_CODES.M] and above.
 * @param alias Alias used to identify the generated keys in the [KeyStore].
 * @param keyGenParameterSpecBuilder Extra changes to apply to the [KeyGenParameterSpec] used to generate the keys.
 */
@RequiresApi(Build.VERSION_CODES.M)
class KeyStoreCryptoImpl(
        override val alias: String,
        private val keyGenParameterSpecBuilder: (KeyGenParameterSpec.Builder.() -> Unit) = {},
) : KeyStoreCrypto {

    private val cipherAlgorithm: String = KeyProperties.KEY_ALGORITHM_RSA
    private val cipherBlockMode: String = KeyProperties.BLOCK_MODE_ECB
    private val cipherPadding: String = ENCRYPTION_PADDING_OAEP_WITH_SHA

    private val cipherTransformation = "$cipherAlgorithm/$cipherBlockMode/$cipherPadding"

    private val privateKeyEntry by lazy { ensurePrivateKeyEntry() }

    override fun initialize() {
        ensurePrivateKeyEntry()
    }

    override fun getInitializedCipher(): Cipher {
        return Cipher.getInstance(cipherTransformation).also { initDecodeCipher(it) }
    }

    override fun encrypt(value: ByteArray): ByteArray = Cipher.getInstance(cipherTransformation).run {
        initEncodeCipher(this)
        doFinal(value)
    }

    override fun decrypt(value: ByteArray): ByteArray = Cipher.getInstance(cipherTransformation).run {
        initDecodeCipher(this)
        doFinal(value)
    }

    private fun initEncodeCipher(cipher: Cipher) {
        val publicKey = privateKeyEntry.certificate.publicKey
        val unrestrictedPubKey = KeyFactory.getInstance(publicKey.algorithm).generatePublic(X509EncodedKeySpec(publicKey.encoded))
        val spec = OAEPParameterSpec(KeyProperties.DIGEST_SHA256, "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
        cipher.init(Cipher.ENCRYPT_MODE, unrestrictedPubKey, spec)
    }

    private fun initDecodeCipher(cipher: Cipher) {
        val privateKey = privateKeyEntry.privateKey
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
    }

    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).also {
            it.load(null)
        }
    }

    private fun ensurePrivateKeyEntry(): KeyStore.PrivateKeyEntry {
        val keystore = getKeyStore()
        return if (keystore.containsAlias(alias)) {
            keystore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        } else {
            generateKeyEntry()
        }
    }

    private fun generateKeyEntry(): KeyStore.PrivateKeyEntry {
        val keyPairGenerator = KeyPairGenerator.getInstance(cipherAlgorithm, ANDROID_KEY_STORE)
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .apply(keyGenParameterSpecBuilder)
                .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
        return getKeyStore().getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    companion object {
        private const val ENCRYPTION_PADDING_OAEP_WITH_SHA = "OAEPWithSHA-256AndMGF1Padding"
    }
}
