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

package im.vector.app.features.pin.lockscreen.views

import androidx.test.platform.app.InstrumentationRegistry
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CodeViewTests {

    lateinit var codeView: CodeView

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        codeView = CodeView(context).apply { codeLength = 4 }
    }

    @Test
    fun addingCharactersChangesEnteredDigits() {
        codeView.onCharInput('A')
        codeView.enteredDigits shouldBeEqualTo 1
    }

    @Test
    fun onCharInputReturnsUpdatedDigitCount() {
        val digits = codeView.onCharInput('1')
        codeView.enteredDigits shouldBeEqualTo digits
    }

    @Test
    fun whenDigitsEqualCodeLengthCompletionCallbackIsCalled() {
        val latch = CountDownLatch(1)
        codeView.onCodeCompleted = { latch.countDown() }

        codeView.onCharInput('1')
        codeView.onCharInput('1')
        codeView.onCharInput('1')
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 4
        latch.await(1, TimeUnit.SECONDS)
    }

    @Test
    fun whenCodeIsCompletedCannotAddMoreDigits() {
        codeView.onCharInput('1')
        codeView.onCharInput('1')
        codeView.onCharInput('1')
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 4

        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 4
    }

    @Test
    fun whenChangingCodeLengthCodeIsReset() {
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 1

        codeView.codeLength = 10

        codeView.enteredDigits shouldBeEqualTo 0
    }

    @Test
    fun changingCodeLengthToTheSameValueDoesNothing() {
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 1

        codeView.codeLength = codeView.codeLength

        codeView.enteredDigits shouldBeEqualTo 1
    }

    @Test
    fun clearResetsEnteredDigits() {
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 1

        codeView.clearCode()

        codeView.enteredDigits shouldBeEqualTo 0
    }

    @Test
    fun deleteLastRemovesLastDigit() {
        codeView.onCharInput('1')
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 2

        codeView.deleteLast()

        codeView.enteredDigits shouldBeEqualTo 1
    }

    @Test
    fun deleteLastReturnsUpdatedDigitCount() {
        codeView.onCharInput('1')
        val digits = codeView.deleteLast()
        codeView.enteredDigits shouldBeEqualTo digits
    }

    @Test
    fun deleteLastCannotRemoveDigitIfCodeIsEmpty() {
        codeView.onCharInput('1')

        codeView.enteredDigits shouldBeEqualTo 1

        codeView.deleteLast()
        codeView.deleteLast()

        codeView.enteredDigits shouldBeEqualTo 0
    }
}
