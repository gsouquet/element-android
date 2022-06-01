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

import androidx.test.platform.app.InstrumentationRegistry
import im.vector.app.features.pin.lockscreen.LockScreenTestUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class LockScreenKeyRepositoryTests {

    private lateinit var lockScreenKeyRepository: LockScreenKeyRepository

    @Before
    fun setup() {
        mockkObject(KeyStoreCryptoCompat)

        LockScreenTestUtils.deleteKeyAlias("base.pin_code")
        LockScreenTestUtils.deleteKeyAlias("base.system")

        lockScreenKeyRepository = LockScreenKeyRepository(InstrumentationRegistry.getInstrumentation().context, "base")
    }

    @After
    fun tearDown() {
        unmockkObject(KeyStoreCryptoCompat)
    }

    @Test
    fun whenLegacyPinCodeKeyIsDeletedNewAliasWillBeUsed() {
        createLegacyKey()
        KeyStoreCrypto.containsKey(LockScreenKeyRepository.LEGACY_PIN_CODE_KEY_ALIAS).shouldBeTrue()
        val pinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        pinCodeCrypto.alias shouldBeEqualTo LockScreenKeyRepository.LEGACY_PIN_CODE_KEY_ALIAS
        KeyStoreCrypto.deleteKey(pinCodeCrypto.alias)

        val newPinCodeCrypto = lockScreenKeyRepository.getPinCodeKey()
        newPinCodeCrypto.alias shouldBeEqualTo "base.pin_code"
    }

    @Test
    fun gettingKeyStoreCryptoAlsoInitializesIt() {
        val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true)
        every { KeyStoreCryptoCompat.create(any(), any(), any()) } returns keyStoreCrypto

        lockScreenKeyRepository.getSystemKey()

        verify { keyStoreCrypto.initialize() }
    }

    @Test
    fun getSystemKeyReturnsKeyWithSystemAlias() {
        val systemKey = createSystemKey()
        KeyStoreCrypto.containsKey(systemKey.alias).shouldBeTrue()
    }

    @Test
    fun getPinCodeKeyReturnsKeyWithPinCodeAlias() {
        val pinCodeKey = lockScreenKeyRepository.getPinCodeKey()
        KeyStoreCrypto.containsKey(pinCodeKey.alias).shouldBeTrue()
    }

    @Test
    fun isSystemKeyValidReturnsWhatKeyStoreCryptoReplies() {
        val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true) {
            every { hasValidKey() } returns false
        }
        every { KeyStoreCryptoCompat.create(any(), any(), any()) } returns keyStoreCrypto

        lockScreenKeyRepository.isSystemKeyValid().shouldBeFalse()
    }

    @Test
    fun hasSystemKeyReturnsTrueAfterSystemKeyIsCreated() {
        lockScreenKeyRepository.hasSystemKey().shouldBeFalse()

        createSystemKey()

        lockScreenKeyRepository.hasSystemKey().shouldBeTrue()
    }

    @Test
    fun hasPinCodeKeyReturnsTrueAfterPinCodeKeyIsCreated() {
        lockScreenKeyRepository.hasPinCodeKey().shouldBeFalse()

        lockScreenKeyRepository.getPinCodeKey()

        lockScreenKeyRepository.hasPinCodeKey().shouldBeTrue()
    }

    @Test
    fun deleteSystemKeyRemovesTheKeyFromKeyStore() {
        createSystemKey()
        lockScreenKeyRepository.hasSystemKey().shouldBeTrue()

        lockScreenKeyRepository.deleteSystemKey()

        lockScreenKeyRepository.hasSystemKey().shouldBeFalse()
    }

    @Test
    fun deletePinCodeKeyRemovesTheKeyFromKeyStore() {
        lockScreenKeyRepository.getPinCodeKey()
        lockScreenKeyRepository.hasPinCodeKey().shouldBeTrue()

        lockScreenKeyRepository.deletePinCodeKey()

        lockScreenKeyRepository.hasPinCodeKey().shouldBeFalse()
    }

    private fun createSystemKey(): KeyStoreCrypto = lockScreenKeyRepository.getSystemKey {
        // We need to disable this for UI tests since the test device probably won't have any enrolled biometric methods
        setUserAuthenticationRequired(false)
    }

    private fun createLegacyKey() {
        val legacyKeyCrypto = KeyStoreCryptoCompat.create(
                InstrumentationRegistry.getInstrumentation().context,
                LockScreenKeyRepository.LEGACY_PIN_CODE_KEY_ALIAS
        )
        legacyKeyCrypto.initialize()
    }
}
