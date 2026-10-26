/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ImeEditorSurroundingTextTest : FunSpec({
    test("splits text around a non-empty selection") {
        val result = splitSurroundingText("beforeSELECTafter", 6, 12)

        result?.textBefore shouldBe "before"
        result?.textSelected shouldBe "SELECT"
        result?.textAfter shouldBe "after"
    }

    test("splits a collapsed cursor position") {
        val result = splitSurroundingText("abcde", 3, 3)

        result?.textBefore shouldBe "abc"
        result?.textSelected shouldBe ""
        result?.textAfter shouldBe "de"
    }

    test("preserves UTF-16 surrogate pairs when selection boundaries are valid") {
        val result = splitSurroundingText("a😀bc", 1, 3)

        result?.textBefore shouldBe "a"
        result?.textSelected shouldBe "😀"
        result?.textAfter shouldBe "bc"
    }

    test("supports a reversed selection") {
        val result = splitSurroundingText("abcde", 4, 1)

        result?.textBefore shouldBe "a"
        result?.textSelected shouldBe "bcd"
        result?.textAfter shouldBe "e"
    }

    test("rejects offsets outside the returned text so the caller can use the compatibility fallback") {
        splitSurroundingText("abc", -1, 2) shouldBe null
        splitSurroundingText("abc", 0, 4) shouldBe null
    }
})
