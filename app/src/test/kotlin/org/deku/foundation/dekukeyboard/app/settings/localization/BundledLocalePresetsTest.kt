package org.deku.foundation.dekukeyboard.app.settings.localization

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BundledLocalePresetsTest : FunSpec({
    test("includes locales that may not be installed on the device") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag": "en-US"},
                {"languageTag": "ar"},
                {"languageTag": "fr-FR"}
              ]
            }
        """.trimIndent()

        parseBundledPresetLocales(manifest).map { it.languageTag() } shouldBe
            listOf("en-US", "ar", "fr-FR")
    }

    test("deduplicates equivalent language tags case-insensitively") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag": "ar"},
                {"languageTag": "AR"},
                {"languageTag": "fr-FR"}
              ]
            }
        """.trimIndent()

        parseBundledPresetLocales(manifest).map { it.languageTag() } shouldBe
            listOf("ar", "fr-FR")
    }

    test("ignores invalid records and returns no locales for malformed JSON") {
        parseBundledPresetLocales("""{"subtypePresets":[{},{"languageTag":""},{"languageTag":"und"}]}""") shouldBe emptyList()
        parseBundledPresetLocales("""{"subtypePresets":[")""") shouldBe emptyList()
    }
})
