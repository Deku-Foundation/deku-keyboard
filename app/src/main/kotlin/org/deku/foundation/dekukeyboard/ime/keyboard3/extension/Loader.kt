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

import android.net.Uri
import org.deku.foundation.dekukeyboard.DekuApplication
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import org.deku.foundation.dekukeyboard.ime.io.Storage
import org.deku.foundation.dekukeyboard.ime.io.readText
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeController
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import org.deku.foundation.dekukeyboard.lib.devtools.flogWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.k3lp.lib.meta.source.SourceFileRef
import org.k3lp.lib.meta.source.TextSourceFile
import org.k3lp.lib.text.WillRequireMigrationToRichErrors
import org.k3lp.model.K3CompileResult
import org.k3lp.model.K3ImpliedImports
import org.k3lp.model.K3ImportResolver
import org.k3lp.model.K3Model
import org.k3lp.model.compileToModel

private const val FOUNDATION_EXTENSION_ID = "org.deku.foundation.dekukeyboard.k3.foundation"
private const val LOCALIZATION_EXTENSION_ID = "org.deku.foundation.dekukeyboard.localization"
private const val LAYOUT_EXTENSION_ID = "org.deku.foundation.dekukeyboard.layouts"
private const val DEFAULT_CHARACTER_LAYOUT = "qwerty"

@OptIn(WillRequireMigrationToRichErrors::class)
suspend fun loadFoundationKeyboard(imeController: ImeController, storage: Storage) {
    val app = DekuApplication.getHack()

    // Do not compile the initial keyboard until preferences have loaded; otherwise a saved language
    // can be overwritten by the default English subtype during application startup.
    app.preferenceStoreLoaded.first { it }

    suspend fun loadAssetText(path: String): String = withContext(Dispatchers.IO) {
        storage.readText(DekuRef.assets(path))
    }

    fun toSourceFile(xml: String): TextSourceFile = TextSourceFile(
        object : SourceFileRef {
            override fun toString(): String = "Deku Keyboard built-in K3 keyboard"
        },
        xml,
    )

    val importResolver = K3ImportResolver { path, _ ->
        val relativePath = Uri.parse(path).path?.removePrefix("/") ?: path
        val importPath = "import/" + relativePath
        toSourceFile(
            loadAssetText("extensions/$FOUNDATION_EXTENSION_ID/$importPath")
        )
    }
    val impliedImports = K3ImpliedImports(
        displays = listOf("flex://$FOUNDATION_EXTENSION_ID/displays-implied.xml"),
        keys = listOf("flex://$FOUNDATION_EXTENSION_ID/keys-implied.xml"),
    )

    val baseKeyboardXml = loadAssetText("extensions/$FOUNDATION_EXTENSION_ID/keyboard/qwertz.xml")
    val localizationManifest = loadAssetText("ime/keyboard/$LOCALIZATION_EXTENSION_ID/extension.json")
    val layoutManifest = loadAssetText("ime/keyboard/$LAYOUT_EXTENSION_ID/extension.json")
    val availableCharacterLayoutIds = parseCharacterLayoutIds(layoutManifest)
    val fallbackLayoutJson = loadAssetText(
        "ime/keyboard/$LAYOUT_EXTENSION_ID/layouts/characters/$DEFAULT_CHARACTER_LAYOUT.json"
    )
    // Reuse small static popup-map assets on language switches; only one locale map is compiled
    // into the active model, and failed reads fall back to the built-in keyboard without crashing.
    val popupMappingJsonCache = mutableMapOf<String, String>()
    // Switching back to a recently used language should be instant: compiled K3 models are
    // immutable inputs to the IME controller, so keep a small access-ordered cache to bound memory.
    val compiledModelCache = java.util.LinkedHashMap<String, K3Model>(8, 0.75f, true)

    app.subtypeManager.value.activeSubtypeFlow.collectLatest { subtype ->
        val languageTag = subtype.primaryLocale.languageTag()
        val layoutId = resolveCharacterLayoutId(
            localizationManifest,
            languageTag,
            subtype.characterLayoutId,
            availableCharacterLayoutIds,
        )
        val popupMappingId = resolvePopupMappingId(localizationManifest, languageTag)
        val cacheKey = "$languageTag|$layoutId|${popupMappingId.orEmpty()}"
        compiledModelCache[cacheKey]?.let { cachedModel ->
            imeController.updateState {
                switchModel(cachedModel)
            }
            return@collectLatest
        }

        val layoutJson = if (layoutId == DEFAULT_CHARACTER_LAYOUT) {
            fallbackLayoutJson
        } else {
            runCatching {
                loadAssetText("ime/keyboard/$LAYOUT_EXTENSION_ID/layouts/characters/$layoutId.json")
            }.onFailure { error ->
                flogWarning {
                    "No bundled character layout '$layoutId' for $languageTag; using QWERTY: ${error.message}"
                }
            }.getOrDefault(fallbackLayoutJson)
        }

        val popupMappingJson = popupMappingId?.let { mappingId ->
            popupMappingJsonCache[mappingId] ?: runCatching {
                loadAssetText(
                    "ime/keyboard/$LOCALIZATION_EXTENSION_ID/popupMappings/$mappingId.json"
                )
            }.onFailure { error ->
                flogWarning {
                    "Unable to load popup map '$mappingId' for $languageTag: ${error.message}"
                }
            }.getOrNull()?.also { popupMappingJsonCache[mappingId] = it }
        }

        val keyboardXml = runCatching {
            buildKeyboard3Xml(baseKeyboardXml, layoutJson, languageTag, popupMappingJson)
        }.onFailure { error ->
            flogWarning {
                "Unable to build '$layoutId' keyboard for $languageTag; falling back to QWERTY: ${error.message}"
            }
        }.getOrElse {
            runCatching {
                buildKeyboard3Xml(baseKeyboardXml, fallbackLayoutJson, languageTag)
            }.getOrElse { fallbackError ->
                flogError { "Unable to build fallback QWERTY keyboard: ${fallbackError.stackTraceToString()}" }
                return@collectLatest
            }
        }

        val sourceFile = toSourceFile(keyboardXml)
        val result = runCatching {
            sourceFile.compileToModel(importResolver, impliedImports)
        }.onFailure { error ->
            flogError { "Failed to compile keyboard for $languageTag: ${error.stackTraceToString()}" }
        }.getOrNull() ?: return@collectLatest

        for (report in result.reports) {
            flogError { report.cause?.stackTraceToString() ?: report.toString() }
        }
        if (result is K3CompileResult.Success) {
            compiledModelCache[cacheKey] = result.model
            while (compiledModelCache.size > 8) {
                val eldestKey = compiledModelCache.entries.iterator().next().key
                compiledModelCache.remove(eldestKey)
            }
            imeController.updateState {
                switchModel(result.model)
            }
        } else {
            flogError { "K3 compilation failed for $languageTag; keeping the current keyboard model." }
        }
    }
}
