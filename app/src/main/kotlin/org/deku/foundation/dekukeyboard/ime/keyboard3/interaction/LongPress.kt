/*
 * Copyright (C) 2026 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3.interaction

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastForEachIndexed
import org.deku.foundation.dekukeyboard.ime.keyboard3.touch.TouchPopupKey
import kotlinx.coroutines.Job
import org.k3lp.lib.text.K3String
import org.k3lp.lib.text.K3StringOrDescriptor

data class LongPress(
    val anchorBounds: Rect = Rect.Zero,
    val simpleBounds: Rect = Rect.Zero,
    val simpleLabel: K3StringOrDescriptor = K3String.Empty,
    val simpleIndicateExtended: Boolean = false,
    val extendedBounds: Rect = Rect.Zero,
    val extendedKeys: List<TouchPopupKey> = emptyList(),
    val extendedFocusedIndex: Int = 0,
    val extendedJob: Job? = null,
    val popupViewportBounds: Rect = Rect(0f, 0f, 1f, 1f),
    val layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    val pointerDownPosition: Offset? = null,
) {
    fun shouldShowSimplePopup(): Boolean {
        // The expanded popup already contains the anchor key; keeping the simple preview visible
        // at the same time draws two popup surfaces over one another.
        return !simpleBounds.isEmpty && !shouldShowExtendedPopup()
    }

    fun shouldShowExtendedPopup(): Boolean {
        return !extendedBounds.isEmpty
    }

    fun getNearestKeyIndex(position: Offset): Int {
        extendedKeys.fastForEachIndexed { index, extendedKey ->
            if (extendedKey.bounds.contains(position)) {
                return index
            }
        }
        return extendedFocusedIndex
    }

    companion object {
        val None = LongPress()
    }
}

/**
 * Places a popup relative to its key in the same normalized coordinate space.
 *
 * The container may start above y=0 when the IME root has drawable space above the
 * key rows. Prefer an above-key placement, reducing the gap before flipping below.
 * Oversized popups use logical start alignment (left in LTR, right in RTL) and are
 * clamped to the supplied container.
 */
internal fun calculatePopupBounds(
    keyBounds: Rect,
    containerBounds: Rect,
    popupSize: Size,
    layoutDirection: LayoutDirection,
): Rect {
    if (
        keyBounds.isEmpty || containerBounds.isEmpty ||
        keyBounds.width <= 0f || keyBounds.height <= 0f ||
        containerBounds.width <= 0f || containerBounds.height <= 0f ||
        !popupSize.width.isFinite() || !popupSize.height.isFinite() ||
        popupSize.width <= 0f || popupSize.height <= 0f ||
        !keyBounds.left.isFinite() || !keyBounds.top.isFinite() ||
        !keyBounds.right.isFinite() || !keyBounds.bottom.isFinite() ||
        !containerBounds.left.isFinite() || !containerBounds.top.isFinite() ||
        !containerBounds.right.isFinite() || !containerBounds.bottom.isFinite()
    ) {
        return Rect.Zero
    }

    val width = popupSize.width.coerceAtMost(containerBounds.width)
    val height = popupSize.height.coerceAtMost(containerBounds.height)
    val preferredLeft = if (width <= keyBounds.width) {
        keyBounds.center.x - width / 2f
    } else {
        when (layoutDirection) {
            LayoutDirection.Ltr -> keyBounds.left
            LayoutDirection.Rtl -> keyBounds.right - width
        }
    }
    val left = preferredLeft.coerceIn(containerBounds.left, containerBounds.right - width)

    val preferredTop = keyBounds.top - height * 1.2f
    val top = when {
        preferredTop >= containerBounds.top -> preferredTop
        keyBounds.top - containerBounds.top >= height -> containerBounds.top
        keyBounds.bottom + height <= containerBounds.bottom -> keyBounds.bottom
        else -> containerBounds.top
    }.coerceIn(containerBounds.top, containerBounds.bottom - height)

    return Rect(left, top, left + width, top + height)
}

/**
 * Map pointer movement relative to the original touch-down onto the popup's selection space.
 * The same transformation is used for move and release events so a release without a final move
 * event cannot commit a stale candidate.
 */
internal fun resolveLongPressPointerPosition(
    longPress: LongPress,
    normalizedPosition: Offset,
): Offset? {
    val bounds = longPress.extendedBounds
    val pointerDownPosition = longPress.pointerDownPosition
    if (bounds.isEmpty || longPress.anchorBounds.isEmpty || pointerDownPosition == null) {
        return null
    }

    // Map pointer movement relative to the original key-down to the popup's anchor center.
    // This keeps the default candidate focused while the finger is still, and tracks the same
    // movement delta when the finger slides into the popup above the keyboard.
    // Do not clamp: leaving the actual popup surface must cancel instead of committing an
    // unrelated edge candidate.
    val selectionPosition = Offset(
        x = longPress.anchorBounds.center.x + normalizedPosition.x - pointerDownPosition.x,
        y = longPress.anchorBounds.center.y + normalizedPosition.y - pointerDownPosition.y,
    )
    return selectionPosition.takeIf(bounds::contains)
}

/**
 * Keep normalized popup geometry inside the keyboard viewport. Long-press mappings may contain
 * more alternatives than fit beside an edge key; without clamping, their union can exceed the
 * keyboard bounds and create an oversized popup surface or invalid Compose constraints.
 */
internal fun clampPopupBoundsToViewport(
    bounds: Rect,
    viewportBounds: Rect = Rect(0f, 0f, 1f, 1f),
): Rect {
    if (
        bounds.isEmpty || viewportBounds.isEmpty ||
        !bounds.left.isFinite() || !bounds.top.isFinite() ||
        !bounds.right.isFinite() || !bounds.bottom.isFinite() ||
        !viewportBounds.left.isFinite() || !viewportBounds.top.isFinite() ||
        !viewportBounds.right.isFinite() || !viewportBounds.bottom.isFinite() ||
        bounds.width <= 0f || bounds.height <= 0f ||
        viewportBounds.width <= 0f || viewportBounds.height <= 0f
    ) {
        return Rect.Zero
    }
    val width = bounds.width.coerceAtMost(viewportBounds.width)
    val height = bounds.height.coerceAtMost(viewportBounds.height)
    val left = bounds.left.coerceIn(viewportBounds.left, viewportBounds.right - width)
    val top = bounds.top.coerceIn(viewportBounds.top, viewportBounds.bottom - height)
    return Rect(left, top, left + width, top + height)
}

fun layoutExtendedLongPressKeys(
    extendedKeys: List<TouchPopupKey>,
    anchorBounds: Rect,
    viewportBounds: Rect = Rect(0f, 0f, 1f, 1f),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): List<TouchPopupKey> {
    if (extendedKeys.isEmpty() || anchorBounds.isEmpty || anchorBounds.width <= 0f || anchorBounds.height <= 0f) {
        return emptyList()
    }

    val nLeftMax = ((anchorBounds.left - viewportBounds.left) / anchorBounds.width).toInt().coerceAtLeast(0)
    val nRightMax = ((viewportBounds.right - anchorBounds.right) / anchorBounds.width).toInt().coerceAtLeast(0)
    var nLeftUsed0 = 0
    var nRightUsed0 = 0
    var nLeftUsed1 = 0
    var nRightUsed1 = 0
    var nTopUsed1 = 0
    val isMultiLine = extendedKeys.size > 5
    val preferRight = layoutDirection == LayoutDirection.Ltr
    val hasRoomAbove = anchorBounds.top - viewportBounds.top >= anchorBounds.height

    return extendedKeys.mapIndexed { index, popupKey ->
        if (index == 0) {
            popupKey.withNewBounds(anchorBounds)
        } else {
            val (xFactor, yFactor) = when {
                isMultiLine && preferRight && nRightUsed0 == 0 && nRightMax > 0 -> {
                    ++nRightUsed0 to 0
                }
                isMultiLine && !preferRight && nLeftUsed0 == 0 && nLeftMax > 0 -> {
                    -(++nLeftUsed0) to 0
                }
                isMultiLine && preferRight && nLeftUsed0 == 0 && nLeftMax > 0 -> {
                    -(++nLeftUsed0) to 0
                }
                isMultiLine && !preferRight && nRightUsed0 == 0 && nRightMax > 0 -> {
                    ++nRightUsed0 to 0
                }
                isMultiLine && nTopUsed1 == 0 && hasRoomAbove -> {
                    0 to -(++nTopUsed1)
                }
                isMultiLine && preferRight && nRightUsed1 < nRightUsed0 && nRightUsed1 < nRightMax -> {
                    ++nRightUsed1 to -1
                }
                isMultiLine && !preferRight && nLeftUsed1 < nLeftUsed0 && nLeftUsed1 < nLeftMax -> {
                    -(++nLeftUsed1) to -1
                }
                isMultiLine && preferRight && nLeftUsed1 < nLeftUsed0 && nLeftUsed1 < nLeftMax -> {
                    -(++nLeftUsed1) to -1
                }
                isMultiLine && !preferRight && nRightUsed1 < nRightUsed0 && nRightUsed1 < nRightMax -> {
                    ++nRightUsed1 to -1
                }
                preferRight && ((nRightUsed0 <= nLeftUsed0 && nRightUsed0 < nRightMax) || nLeftUsed0 >= nLeftMax) && nRightUsed0 < nRightMax -> {
                    ++nRightUsed0 to 0
                }
                !preferRight && ((nLeftUsed0 <= nRightUsed0 && nLeftUsed0 < nLeftMax) || nRightUsed0 >= nRightMax) && nLeftUsed0 < nLeftMax -> {
                    -(++nLeftUsed0) to 0
                }
                nLeftUsed0 < nLeftMax -> {
                    -(++nLeftUsed0) to 0
                }
                nRightUsed0 < nRightMax -> {
                    ++nRightUsed0 to 0
                }
                hasRoomAbove -> {
                    0 to -1
                }
                else -> {
                    0 to 0
                }
            }
            popupKey.withNewBounds(
                clampPopupBoundsToViewport(
                    anchorBounds.translate(
                        translateX = xFactor * anchorBounds.width,
                        translateY = yFactor * anchorBounds.height,
                    ),
                    viewportBounds,
                )
            )
        }
    }
}
