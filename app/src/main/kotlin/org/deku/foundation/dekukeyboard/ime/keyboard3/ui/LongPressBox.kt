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

package org.deku.foundation.dekukeyboard.ime.keyboard3.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.LongPress
import org.deku.foundation.dekukeyboard.ime.theme.DekuImeUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.deku.foundation.dekukeyboard.lib.snygg.SnyggQueryAttributes
import org.deku.foundation.dekukeyboard.lib.snygg.SnyggSelector
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggBox
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggIcon
import org.k3lp.lib.text.K3StringOrDescriptor

val GlobalStateNumPopupsShowing: StateFlow<Int>
    field = MutableStateFlow(0)

@Composable
fun LongPressBox(
    longPress: LongPress,
    longPressSimpleDisplayOverride: K3StringOrDescriptor? = null,
    modifier: Modifier = Modifier,
    attributes: SnyggQueryAttributes = emptyMap(),
) {
    val showSimplePopup = longPress.shouldShowSimplePopup()
    val showExtendedPopup = longPress.shouldShowExtendedPopup()
    val isPopupShowing = showSimplePopup || showExtendedPopup

    DisposableEffect(isPopupShowing) {
        val delta = if (isPopupShowing) 1 else 0
        GlobalStateNumPopupsShowing.update { it + delta }
        onDispose {
            GlobalStateNumPopupsShowing.update { it - delta }
        }
    }

    // Keep the ordinary key preview immediate; animate only the larger long-press panel below
    // so animation never delays visual feedback while the user types rapidly.
    if (showSimplePopup) {
        SnyggBox(
            elementName = DekuImeUi.KeyPopupBox.elementName,
            attributes = attributes,
            modifier = modifier.layoutNormalized(longPress.simpleBounds),
        ) {
            Box(Modifier.layoutNormalized(longPress.anchorBounds.localTo(longPress.simpleBounds))) {
                Display3(
                    display = longPressSimpleDisplayOverride ?: longPress.simpleLabel,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .scaleToFitHorizontally(),
                )
                if (longPress.simpleIndicateExtended) {
                    SnyggIcon(
                        elementName = DekuImeUi.KeyPopupExtendedIndicator.elementName,
                        attributes = attributes,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        imageVector = Icons.Default.MoreHoriz,
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showExtendedPopup,
        modifier = modifier.layoutNormalized(longPress.extendedBounds),
        enter = fadeIn(animationSpec = tween(100)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(100)),
        exit = ExitTransition.None,
    ) {
        SnyggBox(
            elementName = DekuImeUi.KeyPopupBox.elementName,
            attributes = attributes,
            modifier = Modifier.fillMaxSize(),
        ) {
            val extendedKeys = longPress.extendedKeys
            val focusedIndex = longPress.extendedFocusedIndex
            extendedKeys.forEachIndexed { index, extendedKey ->
                val output = extendedKey.data.output
                val attributes = remember(attributes, extendedKey) {
                    if (output != null) {
                        attributes.plus(DekuImeUi.Attr.Output to output.asAttrValue())
                    } else {
                        attributes
                    }
                }
                val selector = if (focusedIndex == index) SnyggSelector.FOCUS else null
                SnyggBox(
                    elementName = DekuImeUi.KeyPopupElement.elementName,
                    attributes = attributes,
                    selector = selector,
                    modifier = modifier.layoutNormalized(extendedKey.bounds.localTo(longPress.extendedBounds)),
                ) {
                    Display3(
                        display = extendedKey.display,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .scaleToFitHorizontally(),
                    )
                }
            }
        }
    }
}

private fun Rect.localTo(parentBounds: Rect): Rect {
    return Rect(
        left = (left - parentBounds.left) / parentBounds.width,
        top = (top - parentBounds.top) / parentBounds.height,
        right = (right - parentBounds.left) / parentBounds.width,
        bottom = (bottom - parentBounds.top) / parentBounds.height,
    )
}
