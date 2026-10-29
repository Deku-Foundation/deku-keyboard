package org.deku.foundation.dekukeyboard.ime.theme

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DekuAssetResolverTest : FunSpec({
    test("parses relative paths from flex asset URIs") {
        parseThemeAssetRelativePath("flex:/images/background.png") shouldBe "images/background.png"
        parseThemeAssetRelativePath("flex:/fonts/arabic/NotoSansArabic.ttf") shouldBe
            "fonts/arabic/NotoSansArabic.ttf"
    }

    test("rejects unsupported URI forms and path traversal") {
        listOf(
            "https:/image.png",
            "flex://other-theme/image.png",
            "flex:/../private/image.png",
            "flex:/images/%2e%2e/private.png",
            "flex:/image.png?download=1",
            "flex:/image.png#preview",
            "flex:/",
            "flex:/images//image.png",
        ).forEach { uri ->
            shouldThrow<IllegalArgumentException> {
                parseThemeAssetRelativePath(uri)
            }
        }
    }
})
