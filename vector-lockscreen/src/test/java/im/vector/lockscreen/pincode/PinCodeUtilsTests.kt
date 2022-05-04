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

package im.vector.lockscreen.pincode

import im.vector.lockscreen.crypto.KeyHelper
import im.vector.lockscreen.crypto.KeyStoreCrypto
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.Before
import org.junit.Test

class PinCodeUtilsTests {

    private val keyHelper = mockk<KeyHelper>(relaxed = true)
    private val storageEncrypted = mockk<EncryptedPinCodeStorage>(relaxed = true)
    lateinit var pinCodeUtils: PinCodeUtils

    @Before
    fun setup() {
        clearAllMocks()

        pinCodeUtils = PinCodeUtils(keyHelper, storageEncrypted)
    }

    @Test
    fun `isPinCodeEnabled returns true if a pin code key exists and the pin code encrypted value is persisted`() = runTest {
        every { keyHelper.hasPinCodeKey() } returns false
        coEvery { storageEncrypted.getPinCode() } returns null

        pinCodeUtils.isPinCodeEnabled().shouldBeFalse()

        every { keyHelper.hasPinCodeKey() } returns true

        pinCodeUtils.isPinCodeEnabled().shouldBeFalse()

        coEvery { storageEncrypted.getPinCode() } returns "SOME_ENCRYPTED_VALUE"

        pinCodeUtils.isPinCodeEnabled().shouldBeTrue()
    }

    @Test
    fun `createPinCode creates a pin code key, encrypts the actual pin code and stores it`() = runTest {
        val keyStoreCrypto = mockk<KeyStoreCrypto> {
            every { encryptToString(any<String>()) } returns "SOME_ENCRYPTED_VALUE"
        }
        every { keyHelper.getPinCodeKey() } returns keyStoreCrypto

        pinCodeUtils.createPinCode("1234")

        verify { keyStoreCrypto.encryptToString(any<String>()) }
        coVerify { storageEncrypted.savePinCode(any()) }
    }

    @Test
    fun `deletePinCode removes the pin code key from the KeyStore and the pin code from the encrypted storage`() = runTest {
        pinCodeUtils.deletePinCode()

        verify { keyHelper.deletePinCodeKey() }
        coVerify { storageEncrypted.deletePinCode() }
    }

    @Test
    fun `verifyPinCode loads the encrypted pin code, decrypts it and compares it to the value provided`() = runTest {
        val originalPinCode = "1234"
        val encryptedPinCode = "SOME_ENCRYPTED_VALUE"
        coEvery { storageEncrypted.getPinCode() } returns encryptedPinCode
        val keyStoreCrypto = mockk<KeyStoreCrypto> {
            every { decryptToString(encryptedPinCode) } returns originalPinCode
        }
        every { keyHelper.getPinCodeKey() } returns keyStoreCrypto
        pinCodeUtils.verifyPinCode(originalPinCode).shouldBeTrue()

        every { keyStoreCrypto.decryptToString(encryptedPinCode) } returns "SOME_OTHER_VALUE"
        pinCodeUtils.verifyPinCode(originalPinCode).shouldBeFalse()
    }

}
