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

package im.vector.app.features.pin.lockscreen.biometrics

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import im.vector.app.features.pin.lockscreen.configuration.LockScreenConfiguration
import im.vector.app.features.pin.lockscreen.configuration.LockScreenConfiguratorProvider
import im.vector.app.features.pin.lockscreen.configuration.LockScreenMode
import im.vector.app.features.pin.lockscreen.crypto.KeyHelper
import im.vector.app.features.pin.lockscreen.fragments.FallbackBiometricDialogFragment
import im.vector.app.features.pin.lockscreen.tests.TestActivity
import im.vector.app.features.pin.lockscreen.utils.DevicePromptCheck
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BiometricUtilsTests {

    private val biometricManager = mockk<BiometricManager>(relaxed = true)
    private val keyHelper = mockk<KeyHelper>(relaxed = true)

    @Before
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun canUseWeakBiometricAuthReturnsTrueIfIsFaceUnlockEnabledAndCanAuthenticate() {
        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BIOMETRIC_SUCCESS
        val configuration = createDefaultConfiguration(isFaceUnlockEnabled = true)
        val biometricUtils = createBiometricUtils(configuration)

        biometricUtils.canUseWeakBiometricAuth.shouldBeTrue()

        val biometricUtilsWithDisabledAuth = createBiometricUtils(createDefaultConfiguration(isFaceUnlockEnabled = false))
        biometricUtilsWithDisabledAuth.canUseWeakBiometricAuth.shouldBeFalse()

        every { biometricManager.canAuthenticate(BIOMETRIC_WEAK) } returns BIOMETRIC_ERROR_NONE_ENROLLED
        biometricUtils.canUseWeakBiometricAuth.shouldBeFalse()
    }

    @Test
    fun canUseStrongBiometricAuthReturnsTrueIfIsBiometricsEnabledAndCanAuthenticate() {
        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BIOMETRIC_SUCCESS
        val configuration = createDefaultConfiguration(isBiometricsEnabled = true)
        val biometricUtils = createBiometricUtils(configuration)

        biometricUtils.canUseStrongBiometricAuth.shouldBeTrue()

        val biometricUtilsWithDisabledAuth = createBiometricUtils(createDefaultConfiguration(isBiometricsEnabled = false))
        biometricUtilsWithDisabledAuth.canUseStrongBiometricAuth.shouldBeFalse()

        every { biometricManager.canAuthenticate(BIOMETRIC_STRONG) } returns BIOMETRIC_ERROR_NONE_ENROLLED
        biometricUtils.canUseStrongBiometricAuth.shouldBeFalse()
    }

    @Test
    fun canUseDeviceCredentialAuthReturnsTrueIfIsDeviceCredentialsUnlockEnabledAndCanAuthenticate() {
        every { biometricManager.canAuthenticate(DEVICE_CREDENTIAL) } returns BIOMETRIC_SUCCESS
        val configuration = createDefaultConfiguration(isDeviceCredentialUnlockEnabled = true)
        val biometricUtils = createBiometricUtils(configuration)

        biometricUtils.canUseDeviceCredentialsAuth.shouldBeTrue()

        val biometricUtilsWithDisabledAuth = createBiometricUtils(createDefaultConfiguration(isDeviceCredentialUnlockEnabled = false))
        biometricUtilsWithDisabledAuth.canUseDeviceCredentialsAuth.shouldBeFalse()

        every { biometricManager.canAuthenticate(DEVICE_CREDENTIAL) } returns BIOMETRIC_ERROR_NONE_ENROLLED
        biometricUtils.canUseDeviceCredentialsAuth.shouldBeFalse()
    }

    @Test
    fun isSystemAuthEnabledReturnsTrueIfAnyAuthenticationMethodIsAvailableAndEnabledAndSystemKeyExists() {
        val biometricUtils = mockk<BiometricUtils>(relaxed = true) {
            every { hasSystemKey } returns true
            every { canUseAnySystemAuth } answers { callOriginal() }
            every { isSystemAuthEnabled } answers { callOriginal() }
        }
        biometricUtils.isSystemAuthEnabled.shouldBeFalse()

        every { biometricUtils.canUseWeakBiometricAuth } returns true
        biometricUtils.isSystemAuthEnabled.shouldBeTrue()

        every { biometricUtils.canUseWeakBiometricAuth } returns false
        every { biometricUtils.canUseStrongBiometricAuth } returns true
        biometricUtils.isSystemAuthEnabled.shouldBeTrue()

        every { biometricUtils.canUseStrongBiometricAuth } returns false
        every { biometricUtils.canUseDeviceCredentialsAuth } returns true
        biometricUtils.isSystemAuthEnabled.shouldBeTrue()

        every { biometricUtils.hasSystemKey } returns false
        biometricUtils.isSystemAuthEnabled.shouldBeFalse()
    }

    @Test
    fun hasSystemKeyReturnsKeyHelperHasSystemKey() {
        val biometricUtils = createBiometricUtils(createDefaultConfiguration())
        every { keyHelper.hasSystemKey() } returns true
        biometricUtils.hasSystemKey.shouldBeTrue()

        every { keyHelper.hasSystemKey() } returns false
        biometricUtils.hasSystemKey.shouldBeFalse()
    }

    @Test
    fun isSystemKeyValidReturnsKeyHelperIsSystemKeyValid() {
        val biometricUtils = createBiometricUtils(createDefaultConfiguration())
        every { keyHelper.isSystemKeyValid() } returns true
        biometricUtils.isSystemKeyValid.shouldBeTrue()

        every { keyHelper.isSystemKeyValid() } returns false
        biometricUtils.isSystemKeyValid.shouldBeFalse()
    }

    @Test
    fun disableAuthenticationDeletesSystemKeyAndCancelsPrompt() {
        val biometricUtils = spyk(createBiometricUtils(createDefaultConfiguration()))
        biometricUtils.disableAuthentication()

        verify { keyHelper.deleteSystemKey() }
        verify { biometricUtils.cancelPrompt() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun authenticateShowsPrompt() = runTest {
        val biometricUtils = createBiometricUtils(createDefaultConfiguration(isBiometricsEnabled = true))
        every { keyHelper.hasSystemKey() } returns true
        val latch = CountDownLatch(1)
        with(ActivityScenario.launch(TestActivity::class.java)) {
            onActivity { activity ->
                biometricUtils.authenticate(activity)
                launch {
                    activity.supportFragmentManager.fragments.isNotEmpty().shouldBeTrue()
                    close()
                    latch.countDown()
                }
            }
        }
        latch.await(1, TimeUnit.SECONDS)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun authenticateInDeviceWithIssuesShowsFallbackPromptDialog() = runTest {
        val biometricUtils = createBiometricUtils(createDefaultConfiguration(isBiometricsEnabled = true))
        mockkObject(DevicePromptCheck)
        every { DevicePromptCheck.isDeviceWithNoBiometricUI } returns true
        every { keyHelper.hasSystemKey() } returns true
        val latch = CountDownLatch(1)
        with(ActivityScenario.launch(TestActivity::class.java)) {
            onActivity { activity ->
                biometricUtils.authenticate(activity)
                launch {
                    activity.supportFragmentManager.fragments.any { it is FallbackBiometricDialogFragment }.shouldBeTrue()
                    close()
                    latch.countDown()
                }
            }
        }
        latch.await(1, TimeUnit.SECONDS)
        unmockkObject(DevicePromptCheck)
    }

    private fun createBiometricUtils(configuration: LockScreenConfiguration): BiometricUtils {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configProvider = LockScreenConfiguratorProvider(configuration)
        return BiometricUtils(context, keyHelper, configProvider, biometricManager)
    }

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
