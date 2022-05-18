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

package im.vector.app.features.pin.lockscreen.test

import android.os.Build
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Used to override [Build.VERSION.SDK_INT]. Ideally an interface should be used instead, but that approach forces us to either add suppress lint annotations
 * and potentially miss an API version issue or write a custom lint rule, which seems like an overkill.
 */
object AndroidVersionTestOverrider {

    fun override(newVersion: Int) {
        val field = Build.VERSION::class.java.getField("SDK_INT")
        setStaticField(field, newVersion)
    }

    private fun setStaticField(field: Field, value: Any) {
        field.isAccessible = true

        val modifiersField = Field::class.java.getDeclaredField("modifiers")
        modifiersField.isAccessible = true
        modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())

        field.set(null, value)
    }

}
