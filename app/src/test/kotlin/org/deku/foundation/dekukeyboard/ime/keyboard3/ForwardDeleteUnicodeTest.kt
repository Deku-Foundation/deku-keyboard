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

class ForwardDeleteUnicodeTest : FunSpec({
    test("removes one BMP code point") {
        removeLeadingCodePoint("abc") shouldBe "bc"
    }

    test("removes one supplementary code point without leaving a lone surrogate") {
        removeLeadingCodePoint("😀x") shouldBe "x"
    }

    test("removes only the first code point from mixed text") {
        removeLeadingCodePoint("𐐷😀") shouldBe "😀"
    }

    test("preserves an empty surrounding-text snapshot") {
        removeLeadingCodePoint("") shouldBe ""
        firstCodePointUtf16Length("") shouldBe 0
    }

    test("reports UTF-16 length for safe compatibility fallback") {
        firstCodePointUtf16Length("xabc") shouldBe 1
        firstCodePointUtf16Length("😀x") shouldBe 2
    }
})
