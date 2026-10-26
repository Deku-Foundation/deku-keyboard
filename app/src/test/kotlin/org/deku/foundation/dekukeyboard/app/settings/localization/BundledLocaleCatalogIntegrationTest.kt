package org.deku.foundation.dekukeyboard.app.settings.localization

import java.io.File
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BundledLocaleCatalogIntegrationTest : FunSpec({
    test("the shipped locale manifest produces a usable bundled locale catalog") {
        val manifestFile = File(
            "src/main/assets/ime/keyboard/org.deku.foundation.dekukeyboard.localization/extension.json",
        )
        manifestFile.isFile shouldBe true

        val presets = parseBundledLocalePresets(manifestFile.readText(Charsets.UTF_8))
        presets.size shouldNotBe 0
        (presets.size >= 50) shouldBe true

        val tags = presets.map { it.locale.languageTag().lowercase(java.util.Locale.ROOT) }
        tags shouldContainAll listOf("en-us", "fr-fr", "ar", "de-de", "es-es")
        presets.any { it.characterLayoutId == "arabic" } shouldBe true
        presets.all { it.characterLayoutId.isNotBlank() } shouldBe true
    }

    test("every locale preset layout ID resolves to a declared character layout asset") {
        val assetsRoot = File("src/main/assets/ime/keyboard")
        val localeManifest = File(
            assetsRoot,
            "org.deku.foundation.dekukeyboard.localization/extension.json",
        )
        val layoutManifest = File(
            assetsRoot,
            "org.deku.foundation.dekukeyboard.layouts/extension.json",
        )
        localeManifest.isFile shouldBe true
        layoutManifest.isFile shouldBe true

        val presets = parseBundledLocalePresets(localeManifest.readText(Charsets.UTF_8))
        val layouts = parseBundledCharacterLayouts(layoutManifest.readText(Charsets.UTF_8))
            .map { it.id }
            .toSet()

        presets.forEach { preset ->
            layouts.contains(preset.characterLayoutId) shouldBe true
            File(
                assetsRoot,
                "org.deku.foundation.dekukeyboard.layouts/layouts/characters/${preset.characterLayoutId}.json",
            ).isFile shouldBe true
        }
    }
})
