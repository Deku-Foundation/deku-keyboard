package org.deku.foundation.dekukeyboard.lib

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DekuLocaleTest : FunSpec({
    test("preserves BCP-47 script subtags") {
        DekuLocale.fromTag("sr-Latn-RS").languageTag() shouldBe "sr-Latn-RS"
    }

    test("accepts the legacy underscore-separated locale format") {
        DekuLocale.fromTag("en_US").languageTag() shouldBe "en-US"
    }

    test("parses a region-specific Arabic locale") {
        DekuLocale.fromTag("ar-DZ").languageTag() shouldBe "ar-DZ"
    }
})
