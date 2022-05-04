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

package im.vector.lockscreen.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import im.vector.lockscreen.R
import im.vector.lockscreen.configuration.LockScreenMode
import im.vector.lockscreen.fragments.AuthMethod
import im.vector.lockscreen.fragments.LockScreenListener
import im.vector.lockscreen.fragments.VectorLockScreenFragment
import im.vector.lockscreen.pincode.PinCodeUtils
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TestActivity: AppCompatActivity(R.layout.activity_test) {

    private val listener = object: LockScreenListener() {
        override fun onFirstCodeEntered() {
            Toast.makeText(this@TestActivity, "First code entered.", Toast.LENGTH_SHORT).show()
        }

        override fun onNewCodeValidationFailed() {
            Toast.makeText(this@TestActivity, "Codes don't match.", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationSuccess(authMethod: AuthMethod) {
            Toast.makeText(this@TestActivity, "Authenticated!", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationFailure(authMethod: AuthMethod) {
            Toast.makeText(this@TestActivity, "Authentication failed.", Toast.LENGTH_SHORT).show()
        }

        override fun onAuthenticationError(authMethod: AuthMethod, throwable: Throwable) {
            Toast.makeText(this@TestActivity, "Authentication error: ${throwable.message.orEmpty()}", Toast.LENGTH_SHORT).show()
        }

        override fun onPinCodeCreated() {
            Toast.makeText(this@TestActivity, "PIN code created.", Toast.LENGTH_SHORT).show()
        }

        override fun onBiometricKeyInvalidated() {
            println("Key invalidated!")
        }
    }

    @Inject
    lateinit var pinCodeUtils: PinCodeUtils

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isPinCodeEnabled = runBlocking { pinCodeUtils.isPinCodeEnabled() }

        val lockScreenFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? VectorLockScreenFragment ?: run {
            val mode = if (isPinCodeEnabled) LockScreenMode.VERIFY else LockScreenMode.CREATE
            val fragment = VectorLockScreenFragment(mode)
            supportFragmentManager.beginTransaction().run {
                replace(R.id.fragment_container, fragment)
                commit()
            }
            fragment
        }
        lockScreenFragment.lockScreenListener = listener
    }

}
