/*
 * Copyright (c) 2020 New Vector Ltd
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

package im.vector.app.features.pin

import android.app.Activity
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.airbnb.mvrx.args
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import im.vector.app.R
import im.vector.app.core.extensions.replaceFragment
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.utils.toast
import im.vector.app.databinding.FragmentPinBinding
import im.vector.app.features.MainActivity
import im.vector.app.features.MainActivityArgs
import im.vector.app.features.settings.VectorPreferences
import im.vector.lockscreen.biometrics.BiometricAuthError
import im.vector.lockscreen.configuration.LockScreenConfiguration
import im.vector.lockscreen.configuration.LockScreenConfiguratorProvider
import im.vector.lockscreen.configuration.LockScreenMode
import im.vector.lockscreen.fragments.AuthMethod
import im.vector.lockscreen.fragments.LockScreenListener
import im.vector.lockscreen.fragments.VectorLockScreenFragment
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@Parcelize
data class PinArgs(
        val pinMode: PinMode
) : Parcelable

class PinFragment @Inject constructor(
        private val pinCodeStore: PinCodeStore,
        private val vectorPreferences: VectorPreferences,
        private val defaultLockScreenConfiguration: LockScreenConfiguration,
        private val configuratorProvider: LockScreenConfiguratorProvider,
) : VectorBaseFragment<FragmentPinBinding>() {

    private val fragmentArgs: PinArgs by args()

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentPinBinding {
        return FragmentPinBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (fragmentArgs.pinMode) {
            PinMode.CREATE -> showCreateFragment()
            PinMode.AUTH   -> showAuthFragment()
            PinMode.MODIFY -> showCreateFragment() // No need to create another function for now because texts are generic
        }
    }

    private fun showCreateFragment() {
        val createFragment = VectorLockScreenFragment()
        createFragment.lockScreenListener = object: LockScreenListener() {
            override fun onNewCodeValidationFailed() {
                Toast.makeText(requireContext(), getString(R.string.create_pin_confirm_failure), Toast.LENGTH_SHORT).show()
            }

            override fun onPinCodeCreated() {
                vectorBaseActivity.setResult(Activity.RESULT_OK)
                vectorBaseActivity.finish()
            }
        }

        val configuration = defaultLockScreenConfiguration.copy(
                mode = LockScreenMode.CREATE,
                title = getString(R.string.create_pin_title),
                needsNewCodeValidation = true,
                newCodeConfirmationTitle = getString(R.string.create_pin_confirm_title),
        )
        configuratorProvider.updateConfiguration(configuration)
        replaceFragment(R.id.pinFragmentContainer, createFragment)
    }

    private fun showAuthFragment() {
        val authFragment = VectorLockScreenFragment()
        val canUseBiometrics = vectorPreferences.useBiometricsToUnlock()
        authFragment.onLeftButtonClickedListener = View.OnClickListener { displayForgotPinWarningDialog() }
        authFragment.lockScreenListener = object: LockScreenListener() {
            override fun onAuthenticationFailure(authMethod: AuthMethod) {
                when (authMethod) {
                    AuthMethod.PIN_CODE -> onWrongPin()
                    AuthMethod.BIOMETRICS -> Unit
                }
            }

            override fun onAuthenticationSuccess(authMethod: AuthMethod) {
                pinCodeStore.resetCounter()
                vectorBaseActivity.setResult(Activity.RESULT_OK)
                vectorBaseActivity.finish()
            }

            override fun onAuthenticationError(authMethod: AuthMethod, throwable: Throwable) {
                super.onAuthenticationError(authMethod, throwable)
                if (throwable is BiometricAuthError) {
                    // System disabled biometric auth, no need to do it ourselves too
                    if (throwable.isAuthDisabledError) {
                        pinCodeStore.resetCounter()
                    }
                    Toast.makeText(requireContext(), throwable.localizedMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
        val configuration = defaultLockScreenConfiguration.copy(
                mode = LockScreenMode.VERIFY,
                title = getString(R.string.auth_pin_title),
                isStrongBiometricsEnabled = defaultLockScreenConfiguration.isStrongBiometricsEnabled && canUseBiometrics,
                isWeakBiometricsEnabled = defaultLockScreenConfiguration.isWeakBiometricsEnabled && canUseBiometrics,
                isDeviceCredentialUnlockEnabled = defaultLockScreenConfiguration.isDeviceCredentialUnlockEnabled && canUseBiometrics,
                autoStartBiometric = canUseBiometrics,
                leftButtonTitle = getString(R.string.auth_pin_forgot),
                clearCodeOnError = true,
        )
        configuratorProvider.updateConfiguration(configuration)
        replaceFragment(R.id.pinFragmentContainer, authFragment)
    }

    private fun onWrongPin() {
        val remainingAttempts = pinCodeStore.onWrongPin()
        when {
            remainingAttempts > 1  ->
                requireActivity().toast(resources.getQuantityString(R.plurals.wrong_pin_message_remaining_attempts, remainingAttempts, remainingAttempts))
            remainingAttempts == 1 ->
                requireActivity().toast(R.string.wrong_pin_message_last_remaining_attempt)
            else                   -> {
                requireActivity().toast(R.string.too_many_pin_failures)
                // Logout
                MainActivity.restartApp(requireActivity(), MainActivityArgs(clearCredentials = true))
            }
        }
    }

    private fun displayForgotPinWarningDialog() {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.auth_pin_reset_title))
                .setMessage(getString(R.string.auth_pin_reset_content))
                .setPositiveButton(getString(R.string.auth_pin_new_pin_action)) { _, _ ->
                    launchResetPinFlow()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
    }

    private fun launchResetPinFlow() {
        MainActivity.restartApp(requireActivity(), MainActivityArgs(clearCredentials = true))
    }
}
