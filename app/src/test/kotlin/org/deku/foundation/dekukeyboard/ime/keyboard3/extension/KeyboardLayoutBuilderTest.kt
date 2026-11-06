package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private fun assertWellFormedXml(xml: String) {
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
}

class KeyboardLayoutBuilderTest : FunSpec({
    test("uses exact region-specific preset before language fallback") {
        val manifest = """
            {
              "subtypePresets": [
                {"languageTag":"ar","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic"}},
                {"languageTag":"ar-DZ","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:arabic_dz"}},
                {"languageTag":"en-US","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:qwerty"}}
              ]
            }
        """.trimIndent()

        resolveCharacterLayoutId(manifest, "ar-DZ") shouldBe "arabic_dz"
        resolveCharacterLayoutId(manifest, "ar-MA") shouldBe "arabic"
    }

    test("falls back to QWERTY if a locale has no preset") {
        resolveCharacterLayoutId("""{"subtypePresets":[]}""", "xx-ZZ") shouldBe "qwerty"
    }

    test("does not mirror Arabic keys or control positions for right-to-left labels") {
        val baseXml = """
            <keyboard3 locale="en">
              <keys>
                <key id="base-to-shift" output="@k3:action/shift" />
                <key id="shift-to-base" output="@k3:action/shift" />
                <key id="bksp" output="@k3:action/backspace" />
              </keys>
              <layers formId="touch">
                <layer id="base"><row keys="a"/><row keys="b"/><row keys="c"/><row keys="symbol-1 comma space enter"/></layer>
                <layer id="shift"><row keys="A"/><row keys="B"/><row keys="C"/><row keys="symbol-1 comma space enter"/></layer>
              </layers>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """[[{"label":"ض"},{"label":"ص"}],[{"label":"ش"},{"label":"س"}],[{"label":"ذ"},{"label":"ء"}]]"""
        val xml = buildKeyboard3Xml(baseXml, layoutJson, "ar-DZ")

        assertWellFormedXml(xml)
        xml shouldContain """<key id="deku-layout-r0-k0" output="ض" />"""
        xml shouldContain """<key id="deku-layout-r0-k1" output="ص" />"""
        xml shouldContain """<row keys="base-to-shift deku-layout-r2-k0 deku-layout-r2-k1 bksp" />"""
        Regex("""<row\s+keys="symbol-1 comma space enter"\s*/>""").containsMatchIn(xml) shouldBe true
    }

    test("uses the Arabic symbol-toggle label for Arabic layouts") {
        val baseXml = """
            <keyboard3 locale="en">
              <keys></keys>
              <layers formId="touch">
                <layer id="base"><row keys="a"/><row keys="b"/><row keys="c"/><row keys="control"/></layer>
                <layer id="shift"><row keys="A"/><row keys="B"/><row keys="C"/><row keys="control"/></layer>
              </layers>
              <variables><string id="symbol_1_display" value="?123" /></variables>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """[[{"label":"ض"}],[{"label":"ص"}],[{"label":"ط"}]]"""

        buildKeyboard3Xml(baseXml, layoutJson, "ar-DZ") shouldContain """value="٣٢١؟""""
    }

    test("generates base and shifted keys and retains the bottom control row") {
        val baseXml = """
            <keyboard3 locale="de">
              <keys>
                <key id="base-to-shift" output="@k3:action/shift" />
                <key id="shift-to-base" output="@k3:action/shift" />
                <key id="bksp" output="@k3:action/backspace" />
              </keys>
              <layers formId="touch">
                <layer id="base">
                  <row keys="old-1" />
                  <row keys="old-2" />
                  <row keys="old-3" />
                  <row keys="symbol-1 comma fn-key space period enter" />
                </layer>
                <layer id="shift">
                  <row keys="old-1" />
                  <row keys="old-2" />
                  <row keys="old-3" />
                  <row keys="symbol-1 comma fn-key space period enter" />
                </layer>
              </layers>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """
            [
              [{"code":113,"label":"q"},{"code":119,"label":"w"}],
              [{"code":97,"label":"a"},{"code":115,"label":"s"}],
              [{"code":122,"label":"z"},{"code":120,"label":"x"}]
            ]
        """.trimIndent()

        val xml = buildKeyboard3Xml(baseXml, layoutJson, "en-US")
        assertWellFormedXml(xml)
        xml shouldContain """<keyboard3 locale="en-US">"""
        xml shouldContain """<key id="deku-layout-r0-k0" output="q" />"""
        xml shouldContain """<key id="deku-layout-r0-k0-shift" output="Q" />"""
        xml shouldContain """<row keys="deku-layout-r0-k0 deku-layout-r0-k1" />"""
        xml shouldContain """<row keys="base-to-shift deku-layout-r2-k0 deku-layout-r2-k1 bksp" />"""
        xml shouldContain """<row keys="symbol-1 comma fn-key space period enter" />"""
    }

    test("reuses existing key definitions and preserves long-press metadata") {
        val baseXml = """
            <keyboard3 locale="en">
              <keys>
                <key id="base-to-shift" output="@k3:action/shift" />
                <key id="shift-to-base" output="@k3:action/shift" />
                <key id="bksp" output="@k3:action/backspace" />
                <key id="q" output="q" longPressKeyIds="q-accent" />
                <key id="q-accent" output="ǫ" />
                <key id="Q" output="Q" />
                <key id="w" output="w" />
                <key id="W" output="W" />
              </keys>
              <layers formId="touch">
                <layer id="base"><row keys="old1"/><row keys="old2"/><row keys="old3"/><row keys="symbol-1 comma space enter"/></layer>
                <layer id="shift"><row keys="old1"/><row keys="old2"/><row keys="old3"/><row keys="symbol-1 comma space enter"/></layer>
              </layers>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """[[{"label":"q"},{"label":"w"}],[{"label":"q"},{"label":"w"}],[{"label":"q"},{"label":"w"}]]"""

        val xml = buildKeyboard3Xml(baseXml, layoutJson, "en-US")
        xml shouldContain """<row keys="q w" />"""
        xml shouldContain """longPressKeyIds="q-accent""""
        xml shouldContain """<row keys="shift-to-base Q W bksp" />"""
        xml.contains("""<key id="deku-layout-r0-k0" output="q" />""") shouldBe false
    }

    test("escapes XML metacharacters and uses Unicode uppercase for German sharp s") {
        val baseXml = """
            <keyboard3 locale="en">
              <keys></keys>
              <layers formId="touch">
                <layer id="base"><row keys="a"/><row keys="b"/><row keys="c"/><row keys="control"/></layer>
                <layer id="shift"><row keys="A"/><row keys="B"/><row keys="C"/><row keys="control"/></layer>
              </layers>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """[[{"label":"&","code":38}],[{"label":"<","code":60}],[{"label":"ß","code":223}]]"""

        val xml = buildKeyboard3Xml(baseXml, layoutJson, "de-DE")
        assertWellFormedXml(xml)
        xml shouldContain """output="&amp;""""
        xml shouldContain """output="&lt;""""
        xml shouldContain """output="ẞ""""
    }
    test("accepts a declared alternative layout not referenced by a locale preset") {
        val localizationManifest = """
            {
              "subtypePresets": [
                {"languageTag":"en-US","preferred":{"characters":"org.deku.foundation.dekukeyboard.layouts:qwerty"}}
              ]
            }
        """.trimIndent()

        resolveCharacterLayoutId(
            localizationManifest,
            "en-US",
            "colemak",
            setOf("qwerty", "colemak"),
        ) shouldBe "colemak"

        resolveCharacterLayoutId(
            localizationManifest,
            "en-US",
            "../colemak",
            setOf("qwerty", "colemak"),
        ) shouldBe "qwerty"
    }


    test("resolves an exact locale popup mapping before falling back to language") {
        val manifest = """
            {
              "popupMappings": [{"id":"default"}, {"id":"ar"}, {"id":"ar-dz"}, {"id":"en"}],
              "subtypePresets": [
                {"languageTag":"ar","popupMapping":"org.deku.foundation.dekukeyboard.localization:ar"},
                {"languageTag":"ar-DZ","popupMapping":"org.deku.foundation.dekukeyboard.localization:ar-dz"},
                {"languageTag":"en-US","popupMapping":"org.deku.foundation.dekukeyboard.localization:en"}
              ]
            }
        """.trimIndent()

        resolvePopupMappingId(manifest, "ar-DZ") shouldBe "ar-dz"
        resolvePopupMappingId(manifest, "ar-MA") shouldBe "ar"
        resolvePopupMappingId(manifest, "fr-FR") shouldBe "default"
    }

    test("wires Arabic popup variants to base and shifted keys without mirroring rows") {
        val baseXml = """
            <keyboard3 locale="en">
              <keys>
                <key id="base-to-shift" output="@k3:action/shift" />
                <key id="shift-to-base" output="@k3:action/shift" />
                <key id="bksp" output="@k3:action/backspace" />
              </keys>
              <layers formId="touch">
                <layer id="base"><row keys="a"/><row keys="b"/><row keys="c"/><row keys="symbol-1 comma space enter"/></layer>
                <layer id="shift"><row keys="A"/><row keys="B"/><row keys="C"/><row keys="symbol-1 comma space enter"/></layer>
              </layers>
            </keyboard3>
        """.trimIndent()
        val layoutJson = """
            [[{"label":"ا"},{"label":"ب"}],[{"label":"س"},{"label":"ش"}],[{"label":"ل"},{"label":"ي"}]]
        """.trimIndent()
        val popupJson = """
            {
              "all": {
                "ا": {
                  "main": {"code": 1571, "label": "أ"},
                  "relevant": [
                    {"code": 1570, "label": "آ"},
                    {"code": 1569, "label": "ء"},
                    {"code": 1573, "label": "إ"}
                  ]
                },
                "ب": {
                  "relevant": [{"code": 1662, "label": "پ"}]
                }
              },
              "uri": {"~right": {"main": {"code": -255, "label": ".sa"}}}
            }
        """.trimIndent()

        val xml = buildKeyboard3Xml(baseXml, layoutJson, "ar", popupJson)

        assertWellFormedXml(xml)
        xml shouldContain """<key id="deku-layout-r0-k0-popup-0" output="أ" />"""
        xml shouldContain """<key id="deku-layout-r0-k0-popup-1" output="آ" />"""
        xml shouldContain """<key id="deku-layout-r0-k0-popup-2" output="ء" />"""
        xml shouldContain """longPressDefaultKeyId="deku-layout-r0-k0-popup-0""""
        xml shouldContain """longPressKeyIds="deku-layout-r0-k0-popup-0 deku-layout-r0-k0-popup-1 deku-layout-r0-k0-popup-2 deku-layout-r0-k0-popup-3""""
        xml shouldContain """<row keys="base-to-shift deku-layout-r2-k0 deku-layout-r2-k1 bksp" />"""
        Regex("""<row\s+keys="symbol-1 comma space enter"\s*/>""").containsMatchIn(xml) shouldBe true
        xml.contains("output=\".sa\"") shouldBe false
    }

})
