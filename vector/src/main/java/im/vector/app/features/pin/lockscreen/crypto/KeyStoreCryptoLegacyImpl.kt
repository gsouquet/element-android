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

package im.vector.app.features.pin.lockscreen.crypto

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import im.vector.app.features.pin.lockscreen.crypto.KeyStoreCrypto.Companion.ANDROID_KEY_STORE
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal
import kotlin.math.abs

/**
 * Implementation of [KeyStoreCrypto] for Android APIs below [Build.VERSION_CODES.M].
 * @param context An Android [Context]. Used to instantiate a [KeyPairGeneratorSpec].
 * @param alias Alias used to identify the generated keys in the [KeyStore].
 */
class KeyStoreCryptoLegacyImpl(
        private val context: Context,
        override val alias: String,
): KeyStoreCrypto {

    private val privateKeyEntry: KeyStore.PrivateKeyEntry by lazy { ensurePrivateKeyEntry(context) }
    private val cipher: Cipher by lazy { Cipher.getInstance(cipherTransformation, RSA_PROVIDER) }

    private val cipherAlgorithm: String = RSA_ALGORITHM
    private val cipherBlockMode: String = CIPHER_BLOCK_MODE
    private val cipherPadding: String = CIPHER_PADDING

    private val cipherTransformation = "$cipherAlgorithm/$cipherBlockMode/$cipherPadding"

    override fun initialize() {
        ensurePrivateKeyEntry(context)
    }

    override fun getInitializedCipher(): Cipher {
        return cipher.also { initEncodeCipher(it) }
    }

    override fun encrypt(value: ByteArray): ByteArray = cipher.run {
        initEncodeCipher(this)
        doFinal(value)
    }

    override fun decrypt(value: ByteArray): ByteArray = cipher.run {
        initDecodeCipher(this)
        doFinal(value)
    }

    private fun initEncodeCipher(cipher: Cipher) {
        cipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.certificate.publicKey)
    }

    private fun initDecodeCipher(cipher: Cipher) {
        cipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.privateKey)
    }

    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).also {
            it.load(null)
        }
    }

    private fun ensurePrivateKeyEntry(context: Context): KeyStore.PrivateKeyEntry {
        val keyStore = getKeyStore()
        return if (keyStore.containsAlias(alias)) {
            keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry
        } else {
            generateKey(context)
        }
    }

    private fun generateKey(context: Context): KeyStore.PrivateKeyEntry {
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance().also {
            it.add(Calendar.YEAR, 25)
        }

        val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(alias)
                .setSubject(X500Principal("CN=$alias"))
                .setSerialNumber(BigInteger.valueOf(abs(alias.hashCode()).toLong()))
                .setStartDate(startDate.time)
                .setEndDate(endDate.time)
                .build()

        val keyGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM, ANDROID_KEY_STORE)
        keyGenerator.initialize(spec)
        keyGenerator.generateKeyPair()
        return getKeyStore().getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }

    companion object {
        // These constants need to be declared here since KeyProperties is only available on APIs >= Android M
        const val RSA_ALGORITHM = "RSA"
        const val CIPHER_BLOCK_MODE = "ECB"
        const val CIPHER_PADDING = "PKCS1Padding"
        val RSA_PROVIDER = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) "AndroidOpenSSL" else "AndroidKeyStoreBCWorkaround"
    }

}
