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

import java.security.KeyStore

/**
 * Helper class to migrate keys from PFLockScreen and use their new aliases.
 */
object LegacyKeyMigrator {

    private const val LEGACY_KEY_ALIAS = "fp_pin_lock_screen_key_store"

    private val keyStore by lazy {
        KeyStore.getInstance(KeyStoreCrypto.ANDROID_KEY_STORE).also { it.load(null) }
    }

    fun migrateIfNeeded(newAlias: String) {
        if (!hasLegacyKey()) return

        val legacyEntry = keyStore.getEntry(LEGACY_KEY_ALIAS, null)
        legacyEntry?.let { keyStore.setEntry(newAlias, it, null) }
        keyStore.deleteEntry(LEGACY_KEY_ALIAS)
    }

    private fun hasLegacyKey() = keyStore.containsAlias(LEGACY_KEY_ALIAS)

}
