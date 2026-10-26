/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import org.deku.foundation.dekukeyboard.ime.extension.Extension
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionComponent
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionDependencyDecl
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionManifest
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionMeta
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Keyboard3Extension(
    override val manifest: Manifest,
    override val sourceRef: DekuRef,
) : Extension<Keyboard3Extension.Manifest> {
    companion object {
        const val SERIAL_TYPE = "ime.extension.keyboard3"
    }

    @SerialName(SERIAL_TYPE)
    @Serializable
    data class Manifest(
        override val meta: ExtensionMeta,
        override val dependencies: ExtensionDependencyDecl = emptyMap(),
        val imports: List<ImportComponent>,
        val keyboards: List<KeyboardComponent>,
        val tests: List<TestComponent>,
    ) : ExtensionManifest

    /**
     * Describes an importable XML element in the `import` directory.
     */
    @Serializable
    data class ImportComponent(
        override val id: String,
        val rootElementName: String,
    ) : ExtensionComponent {
        override val name: String
            get() = id

        override val authors: List<String>
            get() = emptyList()
    }

    /**
     * Describes a keyboard3 component in the `keyboard` directory.
     *
     * See: https://unicode.org/reports/tr35/tr35-keyboards.html#element-info
     */
    @Serializable
    data class KeyboardComponent(
        override val id: String,
        override val name: String,
        override val authors: List<String> = emptyList(),
        val layout: String? = null,
        val indicator: String? = null,
        val attribution: String? = null,
    ) : ExtensionComponent

    /**
     * Describes a keyboardTest3 component in the `test` directory.
     *
     * See: https://github.com/unicode-org/cldr/tree/main/keyboards/test#test-element-info
     */
    @Serializable
    data class TestComponent(
        override val id: String,
        override val name: String,
        override val authors: List<String> = emptyList(),
        val keyboard: String,
    ) : ExtensionComponent
}
