package org.deku.foundation.dekukeyboard.app.settings.localization

import java.util.Locale
import org.deku.foundation.dekukeyboard.lib.DekuLocale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Bundled localization manifest path inside the APK. */
internal const val BUNDLED_LOCALIZATION_MANIFEST =
    "ime/keyboard/org.deku.foundation.dekukeyboard.localization/extension.json"

internal data class BundledLocalePreset(
    val sourceLanguageTag: String,
    val locale: DekuLocale,
    val characterLayoutId: String,
    val displayName: String? = null,
)

private val CHARACTER_LAYOUT_ID_PATTERN = Regex("[A-Za-z0-9_-]+")

internal fun parseBundledLocalePresets(manifestJson: String): List<BundledLocalePreset> {
    val presets = runCatching {
        Json.parseToJsonElement(manifestJson).jsonObject["subtypePresets"]?.jsonArray
    }.getOrNull() ?: return emptyList()

    return presets.mapNotNull { preset ->
        runCatching {
            val obj = preset.jsonObject
            val sourceTag = obj["languageTag"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: return@runCatching null
            val canonicalTag = obj["localeTag"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: sourceTag
            val locale = DekuLocale.fromTag(canonicalTag)
            if (locale.language.isBlank() || locale.language == "und") return@runCatching null
            val layoutRef = obj["preferred"]?.jsonObject
                ?.get("characters")?.jsonPrimitive?.contentOrNull
                ?: return@runCatching null
            val layoutId = layoutRef.substringAfterLast(':')
                .takeIf { it.matches(CHARACTER_LAYOUT_ID_PATTERN) }
                ?: return@runCatching null
            val displayName = obj["displayName"]?.jsonPrimitive?.contentOrNull
                ?.trim()?.takeIf { it.isNotEmpty() }
            BundledLocalePreset(sourceTag, locale, layoutId, displayName)
        }.getOrNull()
    }.distinctBy {
        it.locale.languageTag().lowercase(Locale.ROOT) + "|" + it.characterLayoutId.lowercase(Locale.ROOT)
    }
}

/** Locale-only view retained for callers that don't need to display layout variants. */
internal fun parseBundledPresetLocales(manifestJson: String): List<DekuLocale> {
    val presets = runCatching {
        Json.parseToJsonElement(manifestJson).jsonObject["subtypePresets"]?.jsonArray
    }.getOrNull() ?: return emptyList()

    return presets.mapNotNull { preset ->
        runCatching {
            val obj = preset.jsonObject
            val tag = obj["localeTag"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: obj["languageTag"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val locale = DekuLocale.fromTag(tag)
            locale.takeIf { it.language.isNotBlank() && it.language != "und" }
        }.getOrNull()
    }.distinctBy { it.languageTag().lowercase(Locale.ROOT) }
}
