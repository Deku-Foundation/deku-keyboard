package org.deku.foundation.dekukeyboard.app.settings.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.deku.foundation.dekukeyboard.ime.text.key.KeyCode
import org.deku.foundation.dekukeyboard.ime.text.keyboard.TextKeyData

class TextKeyPreviewLabelTest : FunSpec({
    test("uses the configured key label when available") {
        resolveTextKeyPreviewLabel(
            TextKeyData(code = KeyCode.DELETE, label = "delete"),
        ) shouldBe "delete"
    }

    test("renders the Unicode character when the configured label is empty") {
        resolveTextKeyPreviewLabel(
            TextKeyData(code = 0x0627, label = ""),
        ) shouldBe "ا"
    }

    test("does not render a blank preview for a control key without a label") {
        resolveTextKeyPreviewLabel(
            TextKeyData(code = KeyCode.UNSPECIFIED, label = ""),
        ) shouldBe null
    }
})
