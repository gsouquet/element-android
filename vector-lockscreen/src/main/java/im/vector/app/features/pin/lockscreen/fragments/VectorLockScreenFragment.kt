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

package im.vector.app.features.pin.lockscreen.fragments

import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.mvrx.MavericksView
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.features.pin.lockscreen.R
import im.vector.app.features.pin.lockscreen.configuration.LockScreenConfiguration
import im.vector.app.features.pin.lockscreen.configuration.LockScreenMode
import im.vector.app.features.pin.lockscreen.databinding.FragmentLockScreenBinding
import im.vector.app.features.pin.lockscreen.utils.vibrate
import im.vector.app.features.pin.lockscreen.views.CodeView
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class VectorLockScreenFragment: Fragment(R.layout.fragment_lock_screen), MavericksView {

    var lockScreenListener: LockScreenListener? = null
    var onLeftButtonClickedListener: View.OnClickListener? = null

    private val viewModel: VectorLockScreenViewModel by fragmentViewModel()
    private var binding: FragmentLockScreenBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentLockScreenBinding.bind(view).also { setupBindings(it) }

        viewModel.viewEvent.onEach {
            handleEvent(it)
        }.launchIn(viewLifecycleOwner.lifecycleScope)

        withState(viewModel) { state ->
            if (state.lockScreenConfiguration.mode == LockScreenMode.CREATE) return@withState

            if (state.showBiometricPromptAutomatically && !state.isBiometricKeyInvalidated) {
                viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                    if (state.canUseBiometricAuth && state.isBiometricKeyInvalidated) {
                        lockScreenListener?.onBiometricKeyInvalidated()
                    } else {
                        showBiometricPrompt()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun invalidate() = withState(viewModel) { state ->
        when (state.pinCodeState) {
            is PinCodeState.FirstCodeEntered -> {
                binding?.titleTextView?.let { setupTitleView(it, true, state.lockScreenConfiguration) }
                lockScreenListener?.onFirstCodeEntered()
            }
            is PinCodeState.Idle -> {
                binding?.titleTextView?.let { setupTitleView(it, false, state.lockScreenConfiguration) }
            }
        }
        binding?.let { renderDeleteOrFingerprintButtons(it, it.codeView.enteredDigits) }
        Unit
    }

    private fun onAuthFailure(method: AuthMethod) {
        lockScreenListener?.onAuthenticationFailure(method)

        val configuration = withState(viewModel) { it.lockScreenConfiguration }
        if (configuration.vibrateOnError) {
            context?.vibrate(400)
        }

        if (configuration.animateOnError) {
            context?.let {
                val animation = AnimationUtils.loadAnimation(it, R.anim.lockscreen_shake_animation)
                binding?.codeView?.startAnimation(animation)
            }
        }
    }

    private fun onPinCodeCreated() {
        lockScreenListener?.onPinCodeCreated()
        viewModel.showBiometricPrompt(requireActivity())
    }

    private fun onAuthError(authMethod: AuthMethod, throwable: Throwable) {
        lockScreenListener?.onAuthenticationError(authMethod, throwable)
        withState(viewModel) { state ->
            if (state.lockScreenConfiguration.clearCodeOnError) {
                binding?.codeView?.clearCode()
            }
        }
    }

    private fun handleEvent(viewEvent: VectorLockScreenViewEvent) {
        when (viewEvent) {
            is VectorLockScreenViewEvent.CodeCreationComplete -> onPinCodeCreated() //lockScreenListener?.onPinCodeCreated()
            is VectorLockScreenViewEvent.ClearPinCode -> {
                if (viewEvent.confirmationFailed) {
                    lockScreenListener?.onNewCodeValidationFailed()
                }
                binding?.codeView?.clearCode()
            }
            is VectorLockScreenViewEvent.AuthSuccessful -> lockScreenListener?.onAuthenticationSuccess(viewEvent.method)
            is VectorLockScreenViewEvent.AuthFailure -> onAuthFailure(viewEvent.method)
            is VectorLockScreenViewEvent.AuthError -> onAuthError(viewEvent.method, viewEvent.throwable)
        }
    }

    private fun setupBindings(binding: FragmentLockScreenBinding) = with(binding) {
        val configuration = withState(viewModel) { it.lockScreenConfiguration }
        val lockScreenMode = configuration.mode

        configuration.title?.let { titleTextView.text = it }
        configuration.subtitle?.let {
            subtitleTextView.text = it
            subtitleTextView.isVisible = true
        }
        configuration.nextButtonTitle?.let {
            buttonNext.text = it
        }
        buttonNext.visibility = View.INVISIBLE

        setupTitleView(titleTextView, false, configuration)
        setupCodeView(codeView, configuration)
        setupCodeButton('0', button0, this)
        setupCodeButton('1', button1, this)
        setupCodeButton('2', button2, this)
        setupCodeButton('3', button3, this)
        setupCodeButton('4', button4, this)
        setupCodeButton('5', button5, this)
        setupCodeButton('6', button6, this)
        setupCodeButton('7', button7, this)
        setupCodeButton('8', button8, this)
        setupCodeButton('9', button9, this)
        setupDeleteButton(buttonDelete, this)
        setupFingerprintButton(buttonFingerPrint)
        setupLeftButton(buttonLeft, lockScreenMode, configuration)
        renderDeleteOrFingerprintButtons(this, 0)
    }

    private fun setupTitleView(titleView: TextView, isConfirmation: Boolean, configuration: LockScreenConfiguration) = with(titleView) {
        text = if (isConfirmation) {
            configuration.newCodeConfirmationTitle ?: getString(R.string.lockscreen_confirm_pin)
        } else {
            configuration.title ?: getString(R.string.lockscreen_title)
        }
    }

    private fun setupCodeView(codeView: CodeView, configuration: LockScreenConfiguration) = with(codeView) {
        codeLength = configuration.pinCodeLength
        onCodeCompleted = { code ->
            viewModel.onPinCodeEntered(code)
        }
    }

    private fun setupCodeButton(value: Char, view: View, binding: FragmentLockScreenBinding) {
        view.setOnClickListener {
            val size = binding.codeView.onCharInput(value)
            renderDeleteOrFingerprintButtons(binding, size)
        }
    }

    private fun setupDeleteButton(view: View, binding: FragmentLockScreenBinding) {
        view.setOnClickListener {
            val size = binding.codeView.deleteLast()
            renderDeleteOrFingerprintButtons(binding, size)
        }
    }

    private fun setupFingerprintButton(view: View) {
        view.setOnClickListener {
            showBiometricPrompt()
        }
    }

    private fun setupLeftButton(view: TextView, lockScreenMode: LockScreenMode, configuration: LockScreenConfiguration) = with(view) {
        isVisible = lockScreenMode == LockScreenMode.VERIFY && configuration.leftButtonVisible
        configuration.leftButtonTitle?.let { text = it }
        setOnClickListener(onLeftButtonClickedListener)
    }

    private fun renderDeleteOrFingerprintButtons(binding: FragmentLockScreenBinding, digits: Int) = withState(viewModel) { state ->
        val showFingerprintButton = state.showBiometricPromptAutomatically && digits == 0
        binding.buttonFingerPrint.isVisible = showFingerprintButton
        binding.buttonDelete.isVisible = !showFingerprintButton && digits > 0
    }

    private fun showBiometricPrompt() {
        viewModel.showBiometricPrompt(requireActivity())
    }
}

