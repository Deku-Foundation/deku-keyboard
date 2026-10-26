/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.media

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.keyboard.DekuImeSizing
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeActions
import org.deku.foundation.dekukeyboard.ime.keyboard3.ui.ImeKeyButton
import org.deku.foundation.dekukeyboard.ime.media.emoji.EmojiData
import org.deku.foundation.dekukeyboard.ime.media.emoji.EmojiPaletteView
import org.deku.foundation.dekukeyboard.ime.media.emoji.EmojiSearchLayout
import org.deku.foundation.dekukeyboard.ime.theme.DekuImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggButton
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggColumn
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggIcon
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggRow
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggText

@SuppressLint("MutableCollectionMutableState")
@Composable
fun MediaInputLayout(
    modifier: Modifier = Modifier,
) {
    val prefs by DekuPreferenceStore
    val context = LocalContext.current

    var emojiLayoutDataMap by remember { mutableStateOf(EmojiData.Fallback) }
    LaunchedEffect(Unit) {
        emojiLayoutDataMap = EmojiData.get(context, "ime/media/emoji/root.txt")
    }

    var isEmojiSearch by remember { mutableStateOf(false) }
    if (isEmojiSearch) {
        EmojiSearchLayout()
    } else {
        SnyggColumn(
            elementName = DekuImeUi.Media.elementName,
            modifier = modifier
                .fillMaxWidth()
                .height(DekuImeSizing.imeUiHeight()),
        ) {
            EmojiPaletteView(
                modifier = Modifier.weight(1f),
                fullEmojiMappings = emojiLayoutDataMap,
            )
            SnyggRow(
                elementName = DekuImeUi.MediaBottomRow.elementName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DekuImeSizing.keyboardRowBaseHeight * 0.8f),
            ) {
                ImeKeyButton(
                    elementName = DekuImeUi.MediaBottomRowButton.elementName,
                    output = ImeActions.ShowTextPanel,
                    modifier = Modifier.fillMaxHeight(),
                )
                Spacer(modifier = Modifier.weight(1f))
                // TODO no feature gate once emoji search PR is implemented!
                val experimentalEmojiSearch by prefs.emoji.experimentalEmojiSearchEnabled.collectAsState()
                if (experimentalEmojiSearch) {
                    SnyggButton(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .align(Alignment.CenterVertically),
                        onClick = { isEmojiSearch = true },
                    ) {
                        SnyggIcon(
                            imageVector = Icons.Default.Search,
                        )
                        SnyggText(
                            text = "Search",
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                ImeKeyButton(
                    elementName = DekuImeUi.MediaBottomRowButton.elementName,
                    output = ImeActions.Backspace,
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}
