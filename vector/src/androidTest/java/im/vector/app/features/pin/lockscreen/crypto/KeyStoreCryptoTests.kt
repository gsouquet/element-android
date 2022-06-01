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

import android.security.keystore.KeyPermanentlyInvalidatedException
import im.vector.app.features.pin.lockscreen.LockScreenTestConstants
import im.vector.app.features.pin.lockscreen.LockScreenTestUtils
import io.mockk.every
import io.mockk.mockk
import org.amshove.kluent.shouldBeFalse
import org.junit.Before
import org.junit.Test

class KeyStoreCryptoTests {

    @Before
    fun setup() {
        LockScreenTestUtils.deleteKeyAlias(LockScreenTestConstants.ALIAS)
    }

    @Test
    fun hasValidKeyKeyValidReturnsFalseWhenKeyPermanentlyInvalidatedExceptionIsThrown() {
        // Generate dummy key so KeyStore doesn't complain about key alias not found
        KeyStoreCryptoImpl(LockScreenTestConstants.ALIAS).initialize()

        val keyStoreCrypto = mockk<KeyStoreCryptoImpl>(relaxed = true) {
            every { hasValidKey() } answers { callOriginal() }
            every { initialize() } throws KeyPermanentlyInvalidatedException()
        }

        keyStoreCrypto.hasValidKey().shouldBeFalse()
    }
}
