/*
 * Copyright (C) 2026 The Deku Foundation Contributors
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

package org.deku.foundation.dekukeyboard.app.settings.localization

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal const val BUNDLED_CHARACTER_LAYOUTS_MANIFEST =
    "ime/keyboard/org.deku.foundation.dekukeyboard.layouts/extension.json"
internal const val SelectCharacterLayoutScreenCurrentLayoutId =
    "SelectCharacterLayoutScreen.currentLayoutId"

@Serializable
internal data class BundledCharacterLayout(
    val id: String,
    val label: String,
    val authors: List<String> = emptyList(),
    val direction: String = "ltr",
)

@Serializable
private data class CharacterLayoutManifest(
    val layouts: CharacterLayoutGroups = CharacterLayoutGroups(),
)

@Serializable
private data class CharacterLayoutGroups(
    val characters: List<BundledCharacterLayout> = emptyList(),
)

internal fun parseBundledCharacterLayouts(manifestJson: String): List<BundledCharacterLayout> {
    val manifest = Json { ignoreUnknownKeys = true }
        .decodeFromString<CharacterLayoutManifest>(manifestJson)
    return manifest.layouts.characters
        .filter { layout ->
            layout.id.matches(Regex("[A-Za-z0-9_-]+")) && layout.label.isNotBlank()
        }
        .distinctBy { it.id }
}

internal fun loadBundledCharacterLayouts(context: Context): List<BundledCharacterLayout> =
    runCatching {
        context.assets.open(BUNDLED_CHARACTER_LAYOUTS_MANIFEST)
            .bufferedReader(Charsets.UTF_8)
            .use { reader -> parseBundledCharacterLayouts(reader.readText()) }
    }.getOrDefault(emptyList())
