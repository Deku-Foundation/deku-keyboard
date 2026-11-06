package org.deku.foundation.dekukeyboard.ime.nlp.latin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.lib.DekuLocale

class LatinLanguageProviderTest : FunSpec({
    test("builds a case-insensitive prefix index sorted by frequency") {
        val index = buildEnglishWordPrefixIndex(
            mapOf(
                "hello" to 200,
                "help" to 250,
                "helicopter" to 220,
                "hero" to 180,
                "he" to 255,
            ),
        )

        findEnglishWordPrefixSuggestions(index, "he", 3, allowPossiblyOffensive = false) shouldBe
            listOf("help" to 250, "helicopter" to 220, "hello" to 200)
    }

    test("does not offer the exact typed word as an autocomplete candidate") {
        val index = buildEnglishWordPrefixIndex(mapOf("hello" to 200, "help" to 250))

        findEnglishWordPrefixSuggestions(index, "hello", 5, allowPossiblyOffensive = false) shouldBe
            emptyList()
    }

    test("waits for a useful prefix and respects the candidate limit") {
        val index = buildEnglishWordPrefixIndex(
            mapOf("hello" to 200, "help" to 250, "hero" to 180, "helicopter" to 220),
        )

        findEnglishWordPrefixSuggestions(index, "h", 5, allowPossiblyOffensive = false) shouldBe emptyList()
        findEnglishWordPrefixSuggestions(index, "he", 2, allowPossiblyOffensive = false) shouldBe
            listOf("help" to 250, "helicopter" to 220)
    }

    test("filters known offensive terms unless the user allows them") {
        val index = buildEnglishWordPrefixIndex(mapOf("fun" to 220, "fuck" to 210))

        findEnglishWordPrefixSuggestions(index, "fu", 5, allowPossiblyOffensive = false) shouldBe
            listOf("fun" to 220)
        findEnglishWordPrefixSuggestions(index, "fu", 5, allowPossiblyOffensive = true) shouldBe
            listOf("fun" to 220, "fuck" to 210)
    }

    test("preserves the casing convention the user started typing") {
        preservePrefixCase("hello", "he") shouldBe "hello"
        preservePrefixCase("hello", "He") shouldBe "Hello"
        preservePrefixCase("hello", "HE") shouldBe "HELLO"
    }

    test("does not present the English dictionary as an Arabic dictionary") {
        supportsEnglishDictionary(Subtype.DEFAULT.copy(primaryLocale = DekuLocale.fromTag("ar-DZ"))) shouldBe false
        supportsEnglishDictionary(Subtype.DEFAULT.copy(primaryLocale = DekuLocale.fromTag("en-GB"))) shouldBe true
    }
})
