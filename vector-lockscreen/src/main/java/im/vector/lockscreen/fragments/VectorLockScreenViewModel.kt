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

package im.vector.lockscreen.fragments

import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.fragment.app.FragmentActivity
import com.airbnb.mvrx.MavericksViewModel
import com.airbnb.mvrx.MavericksViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import com.airbnb.mvrx.withState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import im.vector.lockscreen.biometrics.BiometricUtils
import im.vector.lockscreen.configuration.LockScreenConfiguration
import im.vector.lockscreen.configuration.LockScreenConfiguratorProvider
import im.vector.lockscreen.configuration.LockScreenMode
import im.vector.app.core.di.MavericksAssistedViewModelFactory
import im.vector.app.core.di.hiltMavericksViewModelFactory
import im.vector.lockscreen.biometrics.BiometricAuthError
import im.vector.lockscreen.pincode.PinCodeUtils
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class VectorLockScreenViewModel @AssistedInject constructor(
        @Assisted val initialState: VectorLockScreenViewState,
        private val pinCodeUtils: PinCodeUtils,
        private val biometricUtils: BiometricUtils,
        configuratorProvider: LockScreenConfiguratorProvider,
): MavericksViewModel<VectorLockScreenViewState>(initialState) {

    @AssistedFactory
    interface Factory : MavericksAssistedViewModelFactory<VectorLockScreenViewModel, VectorLockScreenViewState> {
        override fun create(initialState: VectorLockScreenViewState): VectorLockScreenViewModel
    }

    companion object : MavericksViewModelFactory<VectorLockScreenViewModel, VectorLockScreenViewState> by hiltMavericksViewModelFactory() {

        override fun initialState(viewModelContext: ViewModelContext): VectorLockScreenViewState {
            return VectorLockScreenViewState(
                    lockScreenConfiguration = DUMMY_CONFIGURATION,
                    canUseBiometricAuth = false,
                    showBiometricPromptAutomatically = false,
                    pinCodeState = PinCodeState.Idle,
                    isBiometricKeyInvalidated = false,
            )
        }

        private val DUMMY_CONFIGURATION = LockScreenConfiguration(
                mode = LockScreenMode.VERIFY,
                pinCodeLength = 4,
                isStrongBiometricsEnabled = false,
                isDeviceCredentialUnlockEnabled = false,
                isWeakBiometricsEnabled = false,
                needsNewCodeValidation = false,
        )
    }

    private var firstEnteredCode: String? = null

    private val mutableViewEventsFlow = MutableSharedFlow<VectorLockScreenViewEvent>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val viewEvent: Flow<VectorLockScreenViewEvent> = mutableViewEventsFlow

    // BiometricPrompt will automatically disable system auth after too many failed auth attempts
    private var isSystemAuthTemporarilyDisabledByBiometricPrompt = false

    init {
        configuratorProvider.configurationFlow
                .onEach { updateConfiguration(it) }
                .launchIn(viewModelScope)
    }

    fun onPinCodeEntered(code: String) = flow {
        val state = awaitState()
        when (state.lockScreenConfiguration.mode) {
            LockScreenMode.CREATE -> {
                if (firstEnteredCode == null && state.lockScreenConfiguration.needsNewCodeValidation) {
                    firstEnteredCode = code
                    mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.ClearPinCode(false))
                    emit(PinCodeState.FirstCodeEntered)
                } else {
                    if (!state.lockScreenConfiguration.needsNewCodeValidation || code == firstEnteredCode) {
                        pinCodeUtils.createPinCode(code)
                        mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.CodeCreationComplete)
                        emit(null)
                    } else {
                        firstEnteredCode = null
                        mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.ClearPinCode(true))
                        emit(PinCodeState.Idle)
                    }
                }
            }
            LockScreenMode.VERIFY -> {
                if (pinCodeUtils.verifyPinCode(code)) {
                    mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.AuthSuccessful(AuthMethod.PIN_CODE))
                    emit(null)
                } else {
                    mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.AuthFailure(AuthMethod.PIN_CODE))
                    emit(null)
                }
            }
        }
    }.catch { error ->
        mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.AuthError(AuthMethod.PIN_CODE, error))
    }.onEach { newPinState ->
        newPinState?.let { setState { copy(pinCodeState = it) }}
    }.launchIn(viewModelScope)

    fun showBiometricPrompt(activity: FragmentActivity) = flow {
        emitAll(biometricUtils.authenticate(activity))
    }.catch { error ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && error is KeyPermanentlyInvalidatedException) {
            disableBiometricAuthentication()
        } else if (error is BiometricAuthError && error.isAuthDisabledError) {
            isSystemAuthTemporarilyDisabledByBiometricPrompt = true
            updateStateWithBiometricInfo()
        }
        mutableViewEventsFlow.tryEmit(VectorLockScreenViewEvent.AuthError(AuthMethod.BIOMETRICS, error))
    }.onEach { success ->
        mutableViewEventsFlow.tryEmit(
                if (success) VectorLockScreenViewEvent.AuthSuccessful(AuthMethod.BIOMETRICS)
                else VectorLockScreenViewEvent.AuthFailure(AuthMethod.BIOMETRICS)
        )
    }.launchIn(viewModelScope)

    private fun disableBiometricAuthentication() {
        biometricUtils.disableAuthentication()
        updateStateWithBiometricInfo()
    }

    private fun updateStateWithBiometricInfo() {
        val configuration = withState(this) { it.lockScreenConfiguration }
        val canUseBiometricAuth = configuration.mode == LockScreenMode.VERIFY
                && !isSystemAuthTemporarilyDisabledByBiometricPrompt
                && biometricUtils.isSystemAuthEnabled
        val isBiometricKeyInvalidated = biometricUtils.hasSystemKey && !biometricUtils.isSystemKeyValid
        val showBiometricPromptAutomatically = canUseBiometricAuth
                && configuration.autoStartBiometric
                && !isBiometricKeyInvalidated
        setState {
            copy(
                    canUseBiometricAuth = canUseBiometricAuth,
                    showBiometricPromptAutomatically = showBiometricPromptAutomatically,
                    isBiometricKeyInvalidated = isBiometricKeyInvalidated
            )
        }
    }

    private fun updateConfiguration(configuration: LockScreenConfiguration) {
        setState { copy(lockScreenConfiguration = configuration) }
        updateStateWithBiometricInfo()
    }
}

