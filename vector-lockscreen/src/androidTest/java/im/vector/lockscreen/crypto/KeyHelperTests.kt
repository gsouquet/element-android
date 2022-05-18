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

import androidx.test.platform.app.InstrumentationRegistry
import im.vector.lockscreen.TestUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.security.KeyStore

class KeyHelperTests {

    private lateinit var keyHelper: KeyHelper

    @Before
    fun setup() {
        mockkObject(KeyStoreCryptoCompat)

        TestUtils.deleteKeyAlias("base.pin_code")
        TestUtils.deleteKeyAlias("base.system")

        keyHelper = KeyHelper(InstrumentationRegistry.getInstrumentation().context, "base")
    }

    @After
    fun tearDown() {
        unmockkObject(KeyStoreCryptoCompat)
    }

    @Test
    fun whenLegacyPinCodeKeyIsDeletedNewAliasWillBeUsed() {
        createLegacyKey()
        KeyStoreCrypto.containsKey(KeyHelper.LEGACY_PIN_CODE_KEY_ALIAS).shouldBeTrue()
        val systemKeyCrypto = keyHelper.getSystemKey()
        systemKeyCrypto.alias shouldBeEqualTo KeyHelper.LEGACY_PIN_CODE_KEY_ALIAS
        KeyStoreCrypto.deleteKey(systemKeyCrypto.alias)

        val newSystemCrypto = keyHelper.getSystemKey()
        newSystemCrypto.alias shouldBeEqualTo "base.system"
    }

    @Test
    fun gettingKeyStoreCryptoAlsoInitializesIt() {
        val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true)
        every { KeyStoreCryptoCompat.create(any(), any(), any()) } returns keyStoreCrypto

        keyHelper.getSystemKey()

        verify { keyStoreCrypto.initialize() }
    }

    @Test
    fun getSystemKeyReturnsKeyWithSystemAlias() {
        val systemKey = createSystemKey()
        KeyStoreCrypto.containsKey(systemKey.alias).shouldBeTrue()
    }

    @Test
    fun getPinCodeKeyReturnsKeyWithPinCodeAlias() {
        val pinCodeKey = keyHelper.getPinCodeKey()
        KeyStoreCrypto.containsKey(pinCodeKey.alias).shouldBeTrue()
    }

    @Test
    fun isSystemKeyValidReturnsWhatKeyStoreCryptoReplies() {
        val keyStoreCrypto = mockk<KeyStoreCrypto>(relaxed = true) {
            every { hasValidKey() } returns false
        }
        every { KeyStoreCryptoCompat.create(any(), any(), any()) } returns keyStoreCrypto

        keyHelper.isSystemKeyValid().shouldBeFalse()
    }

    @Test
    fun hasSystemKeyReturnsTrueAfterSystemKeyIsCreated() {
        keyHelper.hasSystemKey().shouldBeFalse()

        createSystemKey()

        keyHelper.hasSystemKey().shouldBeTrue()
    }

    @Test
    fun hasPinCodeKeyReturnsTrueAfterPinCodeKeyIsCreated() {
        keyHelper.hasPinCodeKey().shouldBeFalse()

        keyHelper.getPinCodeKey()

        keyHelper.hasPinCodeKey().shouldBeTrue()
    }

    @Test
    fun deleteSystemKeyRemovesTheKeyFromKeyStore() {
        createSystemKey()
        keyHelper.hasSystemKey().shouldBeTrue()

        keyHelper.deleteSystemKey()

        keyHelper.hasSystemKey().shouldBeFalse()
    }

    @Test
    fun deletePinCodeKeyRemovesTheKeyFromKeyStore() {
        keyHelper.getPinCodeKey()
        keyHelper.hasPinCodeKey().shouldBeTrue()

        keyHelper.deletePinCodeKey()

        keyHelper.hasPinCodeKey().shouldBeFalse()
    }

    private fun createSystemKey(): KeyStoreCrypto = keyHelper.getSystemKey {
        // We need to disable this for UI tests since the test device probably won't have any enrolled biometric methods
        setUserAuthenticationRequired(false)
    }

    private fun createLegacyKey() {
        val legacyKeyCrypto = KeyStoreCryptoCompat.create(
                InstrumentationRegistry.getInstrumentation().context,
                KeyHelper.LEGACY_PIN_CODE_KEY_ALIAS
        )
        legacyKeyCrypto.initialize()
    }

}
