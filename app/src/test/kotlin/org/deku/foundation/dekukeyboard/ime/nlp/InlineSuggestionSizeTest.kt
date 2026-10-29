package org.deku.foundation.dekukeyboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class InlineSuggestionSizeTest : FunSpec({
    test("keeps a measured height when it is within provider bounds") {
        resolveInlineSuggestionHeight(requestedHeight = 48, minHeight = 0, maxHeight = 120) shouldBe 48
    }

    test("uses wrap content when the height has not been measured") {
        resolveInlineSuggestionHeight(requestedHeight = 0, minHeight = 0, maxHeight = 120) shouldBe WRAP_CONTENT
    }

    test("uses wrap content for a negative layout height") {
        resolveInlineSuggestionHeight(requestedHeight = -1, minHeight = 0, maxHeight = Int.MAX_VALUE) shouldBe WRAP_CONTENT
    }

    test("uses wrap content when a stale height is outside provider bounds") {
        resolveInlineSuggestionHeight(requestedHeight = 140, minHeight = 0, maxHeight = 120) shouldBe WRAP_CONTENT
    }

    test("uses wrap content when provider bounds are inconsistent") {
        resolveInlineSuggestionHeight(requestedHeight = 48, minHeight = 120, maxHeight = 0) shouldBe WRAP_CONTENT
    }
})
