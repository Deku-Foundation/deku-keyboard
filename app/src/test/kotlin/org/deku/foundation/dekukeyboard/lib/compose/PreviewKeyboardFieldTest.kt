package org.deku.foundation.dekukeyboard.lib.compose

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PreviewKeyboardFieldTest : FunSpec({
    test("keeps a preview value within the configured code-point limit") {
        val value = TextFieldValue("hello", selection = TextRange(5))

        boundPreviewFieldValue(value, maxCodePoints = 5) shouldBe value
    }

    test("truncates oversized preview text without splitting surrogate pairs") {
        val value = TextFieldValue(
            text = "a".repeat(10) + "😀" + "b".repeat(10),
            selection = TextRange(24, 10),
            composition = TextRange(12, 20),
        )

        val bounded = boundPreviewFieldValue(value, maxCodePoints = 11)

        bounded.text shouldBe "a".repeat(10) + "😀"
        bounded.text.codePointCount(0, bounded.text.length) shouldBe 11
        bounded.selection shouldBe TextRange(12, 10)
        bounded.composition shouldBe null
    }

    test("handles a zero-length preview limit") {
        val value = TextFieldValue("keyboard", selection = TextRange(8))

        val bounded = boundPreviewFieldValue(value, maxCodePoints = 0)

        bounded.text shouldBe ""
        bounded.selection shouldBe TextRange.Zero
    }
})
