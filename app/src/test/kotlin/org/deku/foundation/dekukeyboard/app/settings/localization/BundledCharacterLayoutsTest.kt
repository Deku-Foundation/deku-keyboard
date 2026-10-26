package org.deku.foundation.dekukeyboard.app.settings.localization

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BundledCharacterLayoutsTest : FunSpec({
    test("parses layout labels and direction metadata") {
        val manifest = """
            {
              "meta": {"id": "test-layouts", "title": "Test"},
              "layouts": {
                "characters": [
                  {"id": "arabic", "label": "Arabic", "direction": "rtl", "authors": ["Deku"]},
                  {"id": "colemak", "label": "Colemak", "direction": "ltr", "authors": ["Keyboard community"]}
                ]
              }
            }
        """.trimIndent()

        parseBundledCharacterLayouts(manifest).map { it.id to it.label } shouldBe
            listOf("arabic" to "Arabic", "colemak" to "Colemak")
        parseBundledCharacterLayouts(manifest).first().direction shouldBe "rtl"
    }

    test("filters unsafe IDs and duplicate layout IDs") {
        val manifest = """
            {
              "layouts": {
                "characters": [
                  {"id": "qwerty", "label": "QWERTY"},
                  {"id": "../outside", "label": "Invalid"},
                  {"id": "qwerty", "label": "Duplicate"}
                ]
              }
            }
        """.trimIndent()

        parseBundledCharacterLayouts(manifest).map { it.id to it.label } shouldBe
            listOf("qwerty" to "QWERTY")
    }
})
