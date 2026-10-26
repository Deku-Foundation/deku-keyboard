package org.deku.foundation.dekukeyboard.ime.nlp.arabic

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.lib.DekuLocale

class ArabicLanguageProviderTest : FunSpec({
    test("normalizes harakat and tatweel without folding distinct alef forms") {
        normalizeArabicWord("كَتَاب") shouldBe "كتاب"
        normalizeArabicWord("كـتاب") shouldBe "كتاب"
        normalizeArabicWord("إلى") shouldBe "إلى"
        normalizeArabicWord("الى") shouldBe "الى"
    }

    test("builds prefix buckets sorted by descending corpus rank") {
        val index = buildArabicWordPrefixIndex(
            listOf(
                "كتابة" to 30,
                "كتاب" to 100,
                "كتابه" to 60,
                "بيت" to 80,
            ),
        )

        findArabicWordPrefixSuggestions(
            index = index,
            prefix = "كت",
            maxCandidateCount = 5,
            allowPossiblyOffensive = false,
        ) shouldBe listOf("كتاب" to 100, "كتابه" to 60, "كتابة" to 30)
    }

    test("does not suggest the complete current word") {
        val index = buildArabicWordPrefixIndex(listOf("كتاب" to 100, "كتابك" to 90))

        findArabicWordPrefixSuggestions(
            index = index,
            prefix = "كتاب",
            maxCandidateCount = 8,
            allowPossiblyOffensive = false,
        ) shouldBe listOf("كتابك" to 90)
    }

    test("requires an Arabic prefix of at least two characters and respects limits") {
        val index = buildArabicWordPrefixIndex(
            listOf("سلام" to 100, "سلامة" to 90, "سليم" to 80, "سلمان" to 70),
        )

        findArabicWordPrefixSuggestions(index, "س", 8, false) shouldBe emptyList()
        findArabicWordPrefixSuggestions(index, "sa", 8, false) shouldBe emptyList()
        findArabicWordPrefixSuggestions(index, "سل", 2, false) shouldBe
            listOf("سليم" to 80, "سلمان" to 70)
    }

    test("filters known offensive terms unless the user allows them") {
        val index = buildArabicWordPrefixIndex(listOf("كس" to 100, "كسب" to 90, "كسمك" to 80))

        findArabicWordPrefixSuggestions(index, "كس", 8, false) shouldBe
            listOf("كسب" to 90)
        findArabicWordPrefixSuggestions(index, "كس", 8, true) shouldBe
            listOf("كس" to 100, "كسمك" to 80, "كسب" to 90).sortedByDescending { it.second }
    }

    test("routes only Arabic primary subtypes to the Arabic dictionary") {
        supportsArabicDictionary(Subtype.DEFAULT.copy(primaryLocale = DekuLocale.fromTag("ar-DZ"))) shouldBe true
        supportsArabicDictionary(Subtype.DEFAULT.copy(primaryLocale = DekuLocale.fromTag("en-GB"))) shouldBe false
    }

    test("recognizes Arabic script but rejects a Latin-only prefix") {
        containsArabicScript("كتاب") shouldBe true
        containsArabicScript("كتابك") shouldBe true
        containsArabicScript("hello") shouldBe false
        containsArabicScript("") shouldBe false
    }
})
