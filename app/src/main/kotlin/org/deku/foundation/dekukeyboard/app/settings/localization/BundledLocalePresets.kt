package org.deku.foundation.dekukeyboard.app.settings.localization

import java.util.Locale
import org.deku.foundation.dekukeyboard.lib.DekuLocale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Localization manifest shipped in the APK. Android's system Locale list is device-dependent and
 * may omit supported keyboard layouts, so the language picker must merge this stable bundled list.
 */
internal const val BUNDLED_LOCALIZATION_MANIFEST =
    "ime/keyboard/org.deku.foundation.dekukeyboard.localization/extension.json"

/**
 * Extract unique, valid language presets from the bundled extension manifest.
 *
 * Invalid individual records are ignored defensively; malformed top-level JSON returns an empty
 * list and lets the caller retain the platform-provided locales. The CI validation separately
 * enforces that the production manifest contains presets and all declared character layouts.
 */
internal fun parseBundledPresetLocales(manifestJson: String): List<DekuLocale> {
    val presets = runCatching {
        Json.parseToJsonElement(manifestJson)
            .jsonObject["subtypePresets"]
            ?.jsonArray
    }.getOrNull() ?: return emptyList()

    return presets.mapNotNull { preset ->
        runCatching {
            val tag = preset.jsonObject["languageTag"]?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            val locale = DekuLocale.fromTag(tag)
            locale.takeIf { it.language.isNotBlank() && it.language != "und" }
        }.getOrNull()
    }.distinctBy { it.languageTag().lowercase(Locale.ROOT) }
}
