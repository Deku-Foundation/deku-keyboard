/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3.interaction

import androidx.compose.ui.geometry.Rect
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class LongPressAnchorBoundsTest : FunSpec({
    test("derives popup width and horizontal alignment from the physical key") {
        val keyBounds = Rect(0.22f, 0.70f, 0.31f, 0.82f)

        val anchor = calculateLongPressAnchorBounds(keyBounds)

        (abs(anchor.width - keyBounds.width) < 0.00001f) shouldBe true
        (abs(anchor.center.x - keyBounds.center.x) < 0.00001f) shouldBe true
        (anchor.bottom < keyBounds.top) shouldBe true
    }

    test("keeps a top-left edge key anchor inside the normalized viewport") {
        val anchor = calculateLongPressAnchorBounds(Rect(0f, 0f, 0.04f, 0.06f))

        anchor.left shouldBe 0f
        // At the top edge the popup is intentionally positioned below the pressed key.
        (anchor.top >= 0.06f) shouldBe true
        (anchor.right <= 1f && anchor.bottom <= 1f) shouldBe true
    }

    test("adapts to tall landscape-style keys without leaving the viewport") {
        val keyBounds = Rect(0.36f, 0.60f, 0.42f, 0.90f)

        val anchor = calculateLongPressAnchorBounds(keyBounds)

        (abs(anchor.width - keyBounds.width) < 0.00001f) shouldBe true
        (abs(anchor.height - keyBounds.height * 0.9f) < 0.00001f) shouldBe true
        (anchor.left >= 0f && anchor.top >= 0f && anchor.right <= 1f && anchor.bottom <= 1f) shouldBe true
        (anchor.bottom < keyBounds.top) shouldBe true
    }

    test("returns an empty anchor for invalid key geometry") {
        calculateLongPressAnchorBounds(Rect.Zero) shouldBe Rect.Zero
    }
})
