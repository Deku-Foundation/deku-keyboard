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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
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

    test("keeps a top-row popup above the key when the IME root has space above the keyboard") {
        val keyBounds = Rect(0f, 0f, 0.04f, 0.06f)
        val viewportBounds = Rect(0f, -0.20f, 1f, 1f)
        val anchor = calculateLongPressAnchorBounds(
            keyBounds = keyBounds,
            viewportBounds = viewportBounds,
            layoutDirection = LayoutDirection.Ltr,
        )

        anchor.left shouldBe 0f
        (anchor.top < keyBounds.top) shouldBe true
        (anchor.bottom < keyBounds.top) shouldBe true
        (anchor.top >= viewportBounds.top) shouldBe true
        (anchor.right <= viewportBounds.right && anchor.bottom <= viewportBounds.bottom) shouldBe true
    }

    test("fits the top-row preview into a short toolbar region instead of flipping below") {
        val keyBounds = Rect(0.2f, 0f, 0.3f, 0.25f)
        val toolbarViewport = Rect(0f, -0.15f, 1f, 1f)
        val anchor = calculateLongPressAnchorBounds(
            keyBounds = keyBounds,
            viewportBounds = toolbarViewport,
            layoutDirection = LayoutDirection.Ltr,
        )

        (anchor.top >= toolbarViewport.top) shouldBe true
        (anchor.bottom < keyBounds.top) shouldBe true
        (anchor.height < keyBounds.height * 0.9f) shouldBe true
    }

    test("flips below only when the available container has no room above the top-row key") {
        val keyBounds = Rect(0.2f, 0f, 0.3f, 0.1f)
        val anchor = calculateLongPressAnchorBounds(
            keyBounds = keyBounds,
            viewportBounds = Rect(0f, 0f, 1f, 1f),
            layoutDirection = LayoutDirection.Ltr,
        )

        (anchor.top >= keyBounds.bottom) shouldBe true
    }

    test("keeps a wider popup aligned to the logical start of its key") {
        val keyBounds = Rect(0.45f, 0.4f, 0.55f, 0.5f)
        val containerBounds = Rect(0f, -0.3f, 1f, 1f)
        val popupSize = Size(0.3f, 0.1f)

        val ltr = calculatePopupBounds(keyBounds, containerBounds, popupSize, LayoutDirection.Ltr)
        val rtl = calculatePopupBounds(keyBounds, containerBounds, popupSize, LayoutDirection.Rtl)

        (abs(ltr.left - 0.45f) < 0.00001f) shouldBe true
        (abs(rtl.right - 0.55f) < 0.00001f) shouldBe true
        (ltr.top < keyBounds.top && rtl.top < keyBounds.top) shouldBe true
    }

    test("clamps a wider popup to the viewport at the physical right edge") {
        val keyBounds = Rect(0.94f, 0.4f, 1f, 0.5f)
        val popup = calculatePopupBounds(
            keyBounds = keyBounds,
            containerBounds = Rect(0f, -0.3f, 1f, 1f),
            popupSize = Size(0.3f, 0.1f),
            layoutDirection = LayoutDirection.Ltr,
        )

        (abs(popup.right - 1f) < 0.00001f) shouldBe true
        (abs(popup.width - 0.3f) < 0.00001f) shouldBe true
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

    test("shows simple popup only before expanded popup is visible") {
        LongPress(simpleBounds = Rect(0f, 0f, 0.1f, 0.1f)).shouldShowSimplePopup() shouldBe true
        LongPress(
            simpleBounds = Rect(0f, 0f, 0.1f, 0.1f),
            extendedBounds = Rect(0.1f, 0.1f, 0.4f, 0.4f),
        ).shouldShowSimplePopup() shouldBe false
    }

    test("uses the release coordinate to resolve expanded popup selection") {
        val longPress = LongPress(
            anchorBounds = Rect(0.4f, 0.6f, 0.5f, 0.7f),
            extendedBounds = Rect(0.3f, 0.4f, 0.6f, 0.7f),
            pointerDownPosition = Offset(0.55f, 0.62f),
        )
        val position = resolveLongPressPointerPosition(longPress, Offset(0.55f, 0.68f))
            ?: error("Expected the release point to resolve inside the popup")

        (abs(position.x - 0.45f) < 0.00001f) shouldBe true
        (abs(position.y - 0.51f) < 0.00001f) shouldBe true
    }

    test("rejects a pointer release outside the expanded popup instead of clamping to an edge key") {
        val longPress = LongPress(
            anchorBounds = Rect(0.4f, 0.4f, 0.5f, 0.5f),
            extendedBounds = Rect(0.3f, 0.3f, 0.6f, 0.6f),
            pointerDownPosition = Offset(0.55f, 0.45f),
        )

        resolveLongPressPointerPosition(longPress, Offset(0.55f, 0.95f)) shouldBe null
    }

    test("clamps overflowing popup bounds to the normalized keyboard viewport") {
        clampPopupBoundsToViewport(Rect(-0.2f, -0.1f, 1.2f, 1.1f)) shouldBe
            Rect(0f, 0f, 1f, 1f)
    }

    test("preserves popup size while moving overflow back inside right and bottom edges") {
        val clamped = clampPopupBoundsToViewport(Rect(0.9f, 0.9f, 1.1f, 1.1f))
        val expected = Rect(0.8f, 0.8f, 1f, 1f)

        (abs(clamped.left - expected.left) < 0.00001f) shouldBe true
        (abs(clamped.top - expected.top) < 0.00001f) shouldBe true
        (abs(clamped.right - expected.right) < 0.00001f) shouldBe true
        (abs(clamped.bottom - expected.bottom) < 0.00001f) shouldBe true
    }

    test("rejects non-finite popup geometry") {
        clampPopupBoundsToViewport(Rect(Float.NaN, 0f, 1f, 1f)) shouldBe Rect.Zero
    }
})
