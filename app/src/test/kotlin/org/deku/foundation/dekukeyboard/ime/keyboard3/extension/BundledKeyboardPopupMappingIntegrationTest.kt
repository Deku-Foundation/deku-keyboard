package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

private fun assertBundledKeyboardXmlIsWellFormed(xml: String) {
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
}

class BundledKeyboardPopupMappingIntegrationTest : FunSpec({
    test("the shipped Arabic layout loads the locale popup map and exposes long-press alternatives") {
        val assetRoot = File("src/main/assets")
        val localeManifest = File(
            assetRoot,
            "ime/keyboard/org.deku.foundation.dekukeyboard.localization/extension.json",
        )
        val layoutJsonFile = File(
            assetRoot,
            "ime/keyboard/org.deku.foundation.dekukeyboard.layouts/layouts/characters/arabic.json",
        )
        val keyboardXmlFile = File(
            assetRoot,
            "extensions/org.deku.foundation.dekukeyboard.k3.foundation/keyboard/qwertz.xml",
        )
        localeManifest.isFile shouldBe true
        layoutJsonFile.isFile shouldBe true
        keyboardXmlFile.isFile shouldBe true

        val manifest = localeManifest.readText(Charsets.UTF_8)
        val popupMapId = resolvePopupMappingId(manifest, "ar")
        popupMapId shouldBe "ar"
        val popupMapFile = File(
            assetRoot,
            "ime/keyboard/org.deku.foundation.dekukeyboard.localization/popupMappings/$popupMapId.json",
        )
        popupMapFile.isFile shouldBe true

        val xml = buildKeyboard3Xml(
            baseXml = keyboardXmlFile.readText(Charsets.UTF_8),
            characterLayoutJson = layoutJsonFile.readText(Charsets.UTF_8),
            languageTag = "ar",
            popupMappingJson = popupMapFile.readText(Charsets.UTF_8),
        )

        assertBundledKeyboardXmlIsWellFormed(xml)
        xml shouldContain """<row keys="deku-layout-r1-k0 deku-layout-r1-k1 deku-layout-r1-k2 deku-layout-r1-k3 deku-layout-r1-k4 deku-layout-r1-k5 deku-layout-r1-k6 deku-layout-r1-k7 deku-layout-r1-k8 deku-layout-r1-k9 deku-layout-r1-k10" />"""
        xml shouldContain """longPressDefaultKeyId="deku-layout-r1-k5-popup-0""""
        xml shouldContain """longPressKeyIds="deku-layout-r1-k5-popup-0 deku-layout-r1-k5-popup-1 deku-layout-r1-k5-popup-2 deku-layout-r1-k5-popup-3 deku-layout-r1-k5-popup-4""""
        xml shouldContain """<key id="deku-layout-r1-k5-popup-0" output="أ" />"""
        xml shouldContain """<key id="deku-layout-r1-k5-popup-1" output="آ" />"""
        xml shouldContain """<key id="deku-layout-r1-k5-popup-2" output="ء" />"""
    }
})
