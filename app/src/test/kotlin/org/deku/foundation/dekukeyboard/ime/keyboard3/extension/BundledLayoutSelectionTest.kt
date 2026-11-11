package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.deku.foundation.dekukeyboard.app.settings.localization.parseBundledLocalePresets
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.lib.DekuLocale

class BundledLayoutSelectionTest : FunSpec({
    test("retains multiple layout choices for the same normalized locale") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag":"ar","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic"}},
                {"languageTag":"AR","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic_pc"}},
                {"languageTag":"ar-DZ","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic_dz"}}
              ]
            }
        """.trimIndent()

        parseBundledLocalePresets(manifest).map { it.locale.languageTag() to it.characterLayoutId } shouldBe
            listOf("ar" to "arabic", "ar" to "arabic_pc", "ar-DZ" to "arabic_dz")
    }

    test("uses canonical locale metadata for legacy tags and custom labels for non-language layouts") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag":"en-UK","localeTag":"en-GB","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:qwerty"}},
                {"languageTag":"fa-FA","localeTag":"fa-IR","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:persian"}},
                {"languageTag":"IPA-IPA","displayName":"International Phonetic Alphabet","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:ipa"}}
              ]
            }
        """.trimIndent()

        val presets = parseBundledLocalePresets(manifest)
        presets.map { it.locale.languageTag() } shouldBe listOf("en-GB", "fa-IR", "ipa")
        presets.map { it.characterLayoutId } shouldBe listOf("qwerty", "persian", "ipa")
        presets.map { it.displayName } shouldBe listOf(null, null, "International Phonetic Alphabet")
    }

    test("prefers a valid explicit layout over the locale default") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag":"ar","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic"}},
                {"languageTag":"ar-DZ","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic_dz"}}
              ]
            }
        """.trimIndent()

        resolveCharacterLayoutId(manifest, "ar-DZ", "arabic") shouldBe "arabic"
        resolveCharacterLayoutId(manifest, "ar-DZ", "not_shipped") shouldBe "arabic_dz"
    }

    test("older persisted subtype preferences remain readable without a layout field") {
        val oldJson = """{"id":42,"primaryLocale":"en-US","secondaryLocales":[]}"""
        val subtype = Json { ignoreUnknownKeys = true }.decodeFromString<Subtype>(oldJson)
        subtype.characterLayoutId shouldBe null
        subtype.primaryLocale.languageTag() shouldBe "en-US"
    }

    test("different explicit layouts are not considered duplicate subtypes") {
        val first = Subtype(
            id = 1L,
            primaryLocale = DekuLocale.fromTag("ar"),
            secondaryLocales = emptyList(),
            characterLayoutId = "arabic",
        )
        val second = first.copy(id = 2L, characterLayoutId = "arabic_pc")
        first.equalsExcludingId(second) shouldBe false
    }
})
