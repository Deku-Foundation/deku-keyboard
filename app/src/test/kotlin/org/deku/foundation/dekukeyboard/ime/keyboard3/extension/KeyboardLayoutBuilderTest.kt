package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

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
        xml shouldContain """<keyboard3 locale="en-US">"""
        xml shouldContain """<key id="deku-r0-k0" output="q" />"""
        xml shouldContain """<key id="deku-r0-k0-shift" output="Q" />"""
        xml shouldContain """<row keys="deku-r0-k0 deku-r0-k1" />"""
        xml shouldContain """<row keys="base-to-shift deku-r2-k0 deku-r2-k1 bksp" />"""
        xml shouldContain """<row keys="symbol-1 comma fn-key space period enter" />"""
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
        xml shouldContain """output="&amp;""""
        xml shouldContain """output="&lt;""""
        xml shouldContain """output="ẞ""""
    }
})
