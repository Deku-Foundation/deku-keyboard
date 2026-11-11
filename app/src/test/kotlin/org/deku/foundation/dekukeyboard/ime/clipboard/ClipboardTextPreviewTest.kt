package org.deku.foundation.dekukeyboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class ClipboardTextPreviewTest : FunSpec({
    test("leaves short clipboard text untouched") {
        clipboardTextPreview("Arabic مرحبًا") shouldBe "Arabic مرحبًا"
    }

    test("does not append an ellipsis when text fits exactly") {
        val source = "x".repeat(CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS)

        clipboardTextPreview(source) shouldBe source
    }

    test("caps enormous text previews by Unicode code point and preserves the ellipsis") {
        val source = "a".repeat(CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS + 500)
        val preview = clipboardTextPreview(source)

        preview shouldBe "a".repeat(CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS) + "…"
        preview.codePointCount(0, preview.length) shouldBe CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS + 1
    }

    test("does not split supplementary Unicode characters") {
        val source = "😀".repeat(CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS + 4)
        val preview = clipboardTextPreview(source)

        preview shouldBe "😀".repeat(CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS) + "…"
        preview.removeSuffix("…").last().isHighSurrogate() shouldBe false
    }

    test("handles zero length limit and rejects negative limits") {
        clipboardTextPreview("clipboard", 0) shouldBe "…"
        shouldThrow<IllegalArgumentException> {
            clipboardTextPreview("clipboard", -1)
        }
    }
})
