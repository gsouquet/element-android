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

package im.vector.app.features.pin.lockscreen.fragment

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.fragment.app.FragmentActivity
import com.airbnb.mvrx.test.MvRxTestRule
import com.airbnb.mvrx.withState
import im.vector.app.features.pin.lockscreen.biometrics.BiometricUtils
import im.vector.app.features.pin.lockscreen.configuration.LockScreenConfiguration
import im.vector.app.features.pin.lockscreen.configuration.LockScreenConfiguratorProvider
import im.vector.app.features.pin.lockscreen.configuration.LockScreenMode
import im.vector.app.features.pin.lockscreen.fragments.AuthMethod
import im.vector.app.features.pin.lockscreen.fragments.PinCodeState
import im.vector.app.features.pin.lockscreen.fragments.VectorLockScreenViewEvent
import im.vector.app.features.pin.lockscreen.fragments.VectorLockScreenViewModel
import im.vector.app.features.pin.lockscreen.fragments.VectorLockScreenViewState
import im.vector.app.features.pin.lockscreen.pincode.PinCodeUtils
import im.vector.app.features.pin.lockscreen.test.AndroidVersionTestOverrider
import im.vector.app.features.pin.lockscreen.test.test
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VectorLockScreenViewModelTests {

    @get:Rule
    val mvrxTestRule = MvRxTestRule()

    private val pinCodeUtils = mockk<PinCodeUtils>(relaxed = true)
    private val biometricUtils = mockk<BiometricUtils>(relaxed = true)

    @Before
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `when ViewModel is instantiated initialState is updated with biometric info`() {
        val initialState = createViewState()
        val configProvider = LockScreenConfiguratorProvider(createDefaultConfiguration())
        // This should set canUseBiometricAuth to true
        every { biometricUtils.isSystemAuthEnabled } returns true
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)
        val newState = withState(viewModel) { it }
        initialState shouldNotBeEqualTo newState
    }

    @Test
    fun `when onPinCodeEntered is called in VERIFY mode, the code is verified and the result is emitted as a ViewEvent`() = runTest {
        val initialState = createViewState()
        val configProvider = LockScreenConfiguratorProvider(createDefaultConfiguration())
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)
        coEvery { pinCodeUtils.verifyPinCode(any()) } returns true

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        val stateBefore = viewModel.awaitState()

        viewModel.onPinCodeEntered("1234")
        coVerify { pinCodeUtils.verifyPinCode(any()) }
        events.assertValues(VectorLockScreenViewEvent.AuthSuccessful(AuthMethod.PIN_CODE))

        coEvery { pinCodeUtils.verifyPinCode(any()) } returns false
        viewModel.onPinCodeEntered("1234")
        events.assertValues(VectorLockScreenViewEvent.AuthSuccessful(AuthMethod.PIN_CODE), VectorLockScreenViewEvent.AuthFailure(AuthMethod.PIN_CODE))

        val stateAfter = viewModel.awaitState()
        stateBefore shouldBeEqualTo stateAfter
    }

    @Test
    fun `when onPinCodeEntered is called in CREATE mode with no confirmation needed it creates the pin code`() = runTest {
        val configuration = createDefaultConfiguration(mode = LockScreenMode.CREATE, needsNewCodeValidation = false)
        val initialState = createViewState(lockScreenConfiguration = configuration)
        val configProvider = LockScreenConfiguratorProvider(configuration)
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.onPinCodeEntered("1234")
        coVerify { pinCodeUtils.createPinCode(any()) }

        events.assertValues(VectorLockScreenViewEvent.CodeCreationComplete)
    }

    @Test
    fun `when onPinCodeEntered is called twice in CREATE mode with confirmation needed it verifies and creates the pin code`() = runTest {
        val configuration = createDefaultConfiguration(mode = LockScreenMode.CREATE, needsNewCodeValidation = true)
        val configProvider = LockScreenConfiguratorProvider(configuration)
        val initialState = createViewState(lockScreenConfiguration = configuration)
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.onPinCodeEntered("1234")

        events.assertValues(VectorLockScreenViewEvent.ClearPinCode(false))
        val pinCodeState = viewModel.awaitState().pinCodeState
        pinCodeState shouldBeEqualTo PinCodeState.FirstCodeEntered

        viewModel.onPinCodeEntered("1234")
        events.assertValues(VectorLockScreenViewEvent.ClearPinCode(false), VectorLockScreenViewEvent.CodeCreationComplete)
    }

    @Test
    fun `when onPinCodeEntered is called in CREATE mode with incorrect confirmation it clears the pin code`() = runTest {
        val configuration = createDefaultConfiguration(mode = LockScreenMode.CREATE, needsNewCodeValidation = true)
        val initialState = createViewState(lockScreenConfiguration = configuration)
        val configProvider = LockScreenConfiguratorProvider(configuration)
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.onPinCodeEntered("1234")

        events.assertValues(VectorLockScreenViewEvent.ClearPinCode(false))
        val pinCodeState = viewModel.awaitState().pinCodeState
        pinCodeState shouldBeEqualTo PinCodeState.FirstCodeEntered

        viewModel.onPinCodeEntered("4321")
        events.assertValues(VectorLockScreenViewEvent.ClearPinCode(false), VectorLockScreenViewEvent.ClearPinCode(true))
        val newPinCodeState = viewModel.awaitState().pinCodeState
        newPinCodeState shouldBeEqualTo PinCodeState.Idle
    }

    @Test
    fun `onPinCodeEntered handles exceptions`() = runTest {
        val initialState = createViewState()
        val configProvider = LockScreenConfiguratorProvider(createDefaultConfiguration())
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)
        val exception = IllegalStateException("Something went wrong")
        coEvery { pinCodeUtils.verifyPinCode(any()) } throws exception

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.onPinCodeEntered("1234")

        events.assertValues(VectorLockScreenViewEvent.AuthError(AuthMethod.PIN_CODE, exception))
    }

    @Test
    fun `when showBiometricPrompt catches a KeyPermanentlyInvalidatedException it disables biometric authentication`() = runTest {
        AndroidVersionTestOverrider.override(Build.VERSION_CODES.M)

        every { biometricUtils.isSystemAuthEnabled } returns true
        every { biometricUtils.isSystemKeyValid } returns true
        val exception = KeyPermanentlyInvalidatedException()
        coEvery { biometricUtils.authenticate(any<FragmentActivity>()) } throws exception
        coEvery { biometricUtils.disableAuthentication() } coAnswers {
            every { biometricUtils.isSystemAuthEnabled } returns false
        }
        val configuration = createDefaultConfiguration(mode = LockScreenMode.VERIFY, needsNewCodeValidation = true, isBiometricsEnabled = true)
        val configProvider = LockScreenConfiguratorProvider(configuration)
        val initialState = createViewState(
                canUseBiometricAuth = true,
                isBiometricKeyInvalidated = false,
                lockScreenConfiguration = configuration
        )
        val viewModel = VectorLockScreenViewModel(initialState, pinCodeUtils, biometricUtils, configProvider)

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.showBiometricPrompt(mockk())

        events.assertValues(VectorLockScreenViewEvent.AuthError(AuthMethod.BIOMETRICS, exception))
        verify { biometricUtils.disableAuthentication() }

        // System key was deleted, biometric auth should be disabled
        val newState = viewModel.awaitState()
        newState.canUseBiometricAuth.shouldBeFalse()

        AndroidVersionTestOverrider.override(0)
    }

    @Test
    fun `when showBiometricPrompt receives an event it propagates it as a ViewEvent`() = runTest {
        val configProvider = LockScreenConfiguratorProvider(createDefaultConfiguration())
        val viewModel = VectorLockScreenViewModel(createViewState(), pinCodeUtils, biometricUtils, configProvider)
        coEvery { biometricUtils.authenticate(any<FragmentActivity>()) } returns flowOf(false, true)

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.showBiometricPrompt(mockk())

        events.assertValues(VectorLockScreenViewEvent.AuthFailure(AuthMethod.BIOMETRICS), VectorLockScreenViewEvent.AuthSuccessful(AuthMethod.BIOMETRICS))
    }

    @Test
    fun `showBiometricPrompt handles exceptions`() = runTest {
        val configProvider = LockScreenConfiguratorProvider(createDefaultConfiguration())
        val viewModel = VectorLockScreenViewModel(createViewState(), pinCodeUtils, biometricUtils, configProvider)
        val exception = IllegalStateException("Something went wrong")
        coEvery { biometricUtils.authenticate(any<FragmentActivity>()) } throws exception

        val events = viewModel.viewEvent.test(CoroutineScope(Dispatchers.Unconfined))
        events.assertNoValues()

        viewModel.showBiometricPrompt(mockk())

        events.assertValues(VectorLockScreenViewEvent.AuthError(AuthMethod.BIOMETRICS, exception))
    }

    private fun createViewState(
            lockScreenConfiguration: LockScreenConfiguration = createDefaultConfiguration(),
            canUseBiometricAuth: Boolean = false,
            showBiometricPromptAutomatically: Boolean = false,
            pinCodeState: PinCodeState = PinCodeState.Idle,
            isBiometricKeyInvalidated: Boolean = false,
    ): VectorLockScreenViewState = VectorLockScreenViewState(
            lockScreenConfiguration, canUseBiometricAuth, showBiometricPromptAutomatically, pinCodeState, isBiometricKeyInvalidated
    )

    private fun createDefaultConfiguration(
            mode: LockScreenMode = LockScreenMode.VERIFY,
            pinCodeLength: Int = 4,
            isBiometricsEnabled: Boolean = false,
            isFaceUnlockEnabled: Boolean = false,
            isDeviceCredentialUnlockEnabled: Boolean = false,
            needsNewCodeValidation: Boolean = false,
            otherChanges: LockScreenConfiguration.() -> LockScreenConfiguration = { this },
    ): LockScreenConfiguration = LockScreenConfiguration(
            mode,
            pinCodeLength,
            isBiometricsEnabled,
            isFaceUnlockEnabled,
            isDeviceCredentialUnlockEnabled,
            needsNewCodeValidation
    ).let(otherChanges)

}
