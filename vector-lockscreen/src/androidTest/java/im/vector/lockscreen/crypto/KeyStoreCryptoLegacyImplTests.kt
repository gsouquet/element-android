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
import im.vector.lockscreen.TestConstants
import im.vector.lockscreen.TestUtils
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Before
import org.junit.Test

class KeyStoreCryptoLegacyImplTests {

    private val keyStoreCryptoLegacyImpl = KeyStoreCryptoLegacyImpl(InstrumentationRegistry.getInstrumentation().context, TestConstants.ALIAS)

    @Before
    fun resetKeyStore() = TestUtils.resetKeyStore()

    @Test
    fun checkEncryptionAndDecryptionWorkAsExpected() {
        val original = "some plain text"
        val encryptedBytes = keyStoreCryptoLegacyImpl.encrypt(original)
        val result = keyStoreCryptoLegacyImpl.decryptToString(encryptedBytes)
        result shouldBeEqualTo original
    }

}
