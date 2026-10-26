package org.deku.foundation.dekukeyboard.ime.editor

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EditorCompatibilityTest : FunSpec({
    test("uses physical enter fallback for visible-password single-line fields with no action") {
        val input = InputAttributes.wrap(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        val options = ImeOptions.wrap(EditorInfo.IME_ACTION_UNSPECIFIED)

        requiresVisiblePasswordEnterFallback(input, options) shouldBe true
    }

    test("does not override an explicit editor action") {
        val input = InputAttributes.wrap(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        )
        val options = ImeOptions.wrap(EditorInfo.IME_ACTION_DONE)

        requiresVisiblePasswordEnterFallback(input, options) shouldBe false
    }

    test("does not change multiline text fields") {
        val input = InputAttributes.wrap(
            InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        val options = ImeOptions.wrap(EditorInfo.IME_ACTION_UNSPECIFIED)

        requiresVisiblePasswordEnterFallback(input, options) shouldBe false
    }
})
