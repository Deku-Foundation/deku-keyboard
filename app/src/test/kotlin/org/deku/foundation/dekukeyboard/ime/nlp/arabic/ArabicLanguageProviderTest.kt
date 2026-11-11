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

    test("ranks prefix candidates and deduplicates normalized spelling variants") {
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
        ) shouldBe listOf("كتاب" to 100, "كتابه" to 60)
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
            listOf("سلام" to 100, "سلامة" to 90)
    }

    test("filters known offensive terms unless the user allows them") {
        val index = buildArabicWordPrefixIndex(listOf("كس" to 100, "كسب" to 90, "كسمك" to 80))

        findArabicWordPrefixSuggestions(index, "كس", 8, false) shouldBe
            listOf("كسب" to 90)
        findArabicWordPrefixSuggestions(index, "كس", 8, true) shouldBe
            listOf("كسب" to 90, "كسمك" to 80)
    }

    test("normalizes Arabic spelling variants for matching while preserving output spelling") {
        normalizeArabicMatchKey("أنت") shouldBe "انت"
        normalizeArabicMatchKey("على") shouldBe "علي"
        normalizeArabicMatchKey("مدرسة") shouldBe "مدرسه"

        val index = buildArabicWordPrefixIndex(listOf("إلى" to 100, "إليك" to 90))
        findArabicWordPrefixSuggestions(index, "الى", 5, false) shouldBe listOf("إليك" to 90)
    }

    test("suggests Arabic candidates from common Arabizi input locally") {
        val index = buildArabiziPrefixIndex(
            listOf(
                "سلام" to 100,
                "مرحبا" to 90,
                "مكتب" to 80,
            ),
        )

        findArabiziPrefixSuggestions(index, "salam", 5, false) shouldBe listOf("سلام" to 100)
        findArabiziPrefixSuggestions(index, "mar7aba", 5, false) shouldBe listOf("مرحبا" to 90)
        findArabiziPrefixSuggestions(index, "maktab", 5, false) shouldBe listOf("مكتب" to 80)
    }

    test("completes Arabic words when common proclitics are already typed") {
        val index = buildArabicWordPrefixIndex(
            listOf(
                "كتاب" to 100,
                "كتابة" to 90,
                "سلام" to 80,
            ),
        )

        findArabicWordPrefixSuggestions(index, "والكت", 5, false) shouldBe
            listOf("والكتاب" to 100, "والكتابة" to 90)
        findArabicWordPrefixSuggestions(index, "بالسلا", 5, false) shouldBe
            listOf("بالسلام" to 80)
    }

    test("preserves diacritics that belong to the typed proclitic") {
        val index = buildArabicWordPrefixIndex(listOf("كتاب" to 100))

        findArabicWordPrefixSuggestions(index, "وَالْكِت", 5, false) shouldBe
            listOf("وَالْكتاب" to 100)
    }

    test("does not append a clitic to a complete base word as if it were still a prefix") {
        val index = buildArabicWordPrefixIndex(listOf("كتاب" to 100, "كتابك" to 90))

        findArabicWordPrefixSuggestions(index, "والكتاب", 5, false) shouldBe emptyList()
    }

    test("completes partially typed Arabic pronoun suffixes from local dictionary words") {
        val index = buildArabicWordPrefixIndex(
            listOf(
                "كتاب" to 100,
                "مدرسة" to 90,
            ),
        )

        findArabicWordPrefixSuggestions(index, "كتابك", 5, false) shouldBe
            listOf("كتابكم" to 100, "كتابكما" to 100, "كتابكن" to 100)
        findArabicWordPrefixSuggestions(index, "مدرستك", 5, false) shouldBe
            listOf("مدرستكم" to 90, "مدرستكما" to 90, "مدرستكن" to 90)
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
