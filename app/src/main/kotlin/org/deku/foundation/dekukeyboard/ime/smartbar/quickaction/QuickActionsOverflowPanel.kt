/*
 * Copyright (C) 2022-2026 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.smartbar.quickaction

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.keyboard.DekuImeSizing
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeActions
import org.deku.foundation.dekukeyboard.ime.keyboard3.LocalImeController
import org.deku.foundation.dekukeyboard.ime.theme.DekuImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.deku.foundation.dekukeyboard.lib.compose.stringRes
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggBox
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggButton
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggText

@Composable
fun QuickActionsOverflowPanel() {
    val prefs by DekuPreferenceStore
    val imeController = LocalImeController.current

    val actionArrangement by prefs.smartbar.actionArrangement.collectAsState()

    val dynamicActions = actionArrangement.dynamicActions
    val smartbarVisibleDynamicActionsCount by imeController.activeSmartbarVisibleDynamicActionsCount.collectAsState()
    val dynamicActionsCountToShow = when {
        dynamicActions.isEmpty() -> 0
        else -> {
            (dynamicActions.size - smartbarVisibleDynamicActionsCount).coerceIn(dynamicActions.indices)
        }
    }
    val visibleActions = remember(actionArrangement, dynamicActionsCountToShow) {
        actionArrangement.dynamicActions.takeLast(dynamicActionsCountToShow)
    }

    SnyggBox(
        elementName = DekuImeUi.SmartbarActionsOverflow.elementName,
        modifier = Modifier
            .fillMaxWidth()
            .height(DekuImeSizing.keyboardUiHeight()),
    ) {
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth(),
            columns = GridCells.Adaptive(DekuImeSizing.smartbarHeight.coerceAtLeast(1.dp) * 2.2f),
        ) {
            items(visibleActions) { action ->
                QuickActionButton(
                    action = action,
                    type = QuickActionBarType.INTERACTIVE_TILE,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SnyggButton(
                    elementName = DekuImeUi.SmartbarActionsOverflowCustomizeButton.elementName,
                    onClick = {
                        imeController.updateStateAsync {
                            emitDescriptor(ImeActions.ToggleActionsEditor)
                        }
                    },
                    modifier = Modifier
                        .wrapContentWidth(),
                ) {
                    SnyggText(
                        text = stringRes(R.string.quick_actions_overflow__customize_actions_button),
                    )
                }
            }
        }
    }
}
