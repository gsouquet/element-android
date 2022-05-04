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

/**
 * Listener class to be notified of any event that could happen in the lock screen UI.
 */
open class LockScreenListener {
    /**
     * In PIN creation mode, called when the first PIN code has been entered.
     */
    open fun onFirstCodeEntered() {}

    /**
     * In PIN creation mode, called when the confirmation PIN code doesn't match the first one.
     */
    open fun onNewCodeValidationFailed() {}

    /**
     * In PIN creation mode, called when the PIN code was successfully set up.
     */
    open fun onPinCodeCreated() {}

    /**
     * In verification mode, called when the authentication succeeded.
     * @param authMethod Authentication method used ([AuthMethod.PIN_CODE] or [AuthMethod.BIOMETRICS]).
     */
    open fun onAuthenticationSuccess(authMethod: AuthMethod) {}

    /**
     * In verification mode, called when the authentication failed. At this point the user can usually still retry the authentication.
     * @param authMethod Authentication method used ([AuthMethod.PIN_CODE] or [AuthMethod.BIOMETRICS]).
     */
    open fun onAuthenticationFailure(authMethod: AuthMethod) {}

    /**
     * In verification mode, called when the authentication had a fatal error and can't continue. This is not an authentication failure.
     * @param authMethod Authentication method used ([AuthMethod.PIN_CODE] or [AuthMethod.BIOMETRICS]).
     * @param throwable The error thrown when the authentication flow was interrupted.
     */
    open fun onAuthenticationError(authMethod: AuthMethod, throwable: Throwable) {}

    /**
     * In verification mode, called when the system authentication key (used for biometrics) has been invalidated and cannot be used anymore.
     */
    open fun onBiometricKeyInvalidated() {}
}

/**
 * Enum containing the available authentication methods.
 */
enum class AuthMethod {
    PIN_CODE,
    BIOMETRICS
}
