/*
 * Copyright (C) 2026 The Deku Foundation Contributors
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

package org.deku.foundation.dekukeyboard.app.settings.localization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.dekuScrollbar
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

@Composable
fun SelectCharacterLayoutScreen() = DekuScreen {
    title = stringRes(R.string.settings__localization__subtype_characters_layout)
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val editorBackStackEntry = remember(navController) { navController.previousBackStackEntry }
    val currentLayoutId = remember(editorBackStackEntry) {
        editorBackStackEntry?.savedStateHandle
            ?.get<String>(SelectCharacterLayoutScreenCurrentLayoutId)
            .orEmpty()
    }
    val characterLayouts = remember(context) {
        loadBundledCharacterLayouts(context).sortedBy { it.label.lowercase(Locale.ROOT) }
    }
    var searchTermValue by remember { mutableStateOf(TextFieldValue()) }

    val filteredLayouts = remember(searchTermValue.text, characterLayouts) {
        val term = searchTermValue.text.trim().lowercase(Locale.ROOT)
        if (term.isBlank()) {
            characterLayouts
        } else {
            characterLayouts.filter { layout ->
                layout.label.lowercase(Locale.ROOT).contains(term) ||
                    layout.id.lowercase(Locale.ROOT).contains(term) ||
                    layout.authors.any { it.lowercase(Locale.ROOT).contains(term) }
            }
        }
    }

    content {
        val state = rememberLazyListState()
        Column(modifier = Modifier.fillMaxSize()) {
            TextField(
                modifier = Modifier.fillMaxWidth(),
                value = searchTermValue,
                onValueChange = { searchTermValue = it },
                placeholder = {
                    Text(stringRes(R.string.settings__localization__subtype_search_layout_placeholder))
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RectangleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )

            if (characterLayouts.isEmpty()) {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringRes(R.string.settings__localization__subtype_layout_catalog_unavailable),
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                )
            } else if (filteredLayouts.isEmpty()) {
                Text(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally),
                    text = stringRes(
                        R.string.settings__localization__subtype_search_layout_not_found,
                        "search_term" to searchTermValue.text,
                    ),
                    color = LocalContentColor.current.copy(alpha = 0.54f),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .dekuScrollbar(state, isVertical = true),
                state = state,
            ) {
                item(key = "automatic-layout") {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editorBackStackEntry?.savedStateHandle?.set(
                                    SelectLocaleScreenResultCharacterLayoutId,
                                    "",
                                )
                                navController.popBackStack()
                            },
                        leadingContent = {
                            if (currentLayoutId.isBlank()) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                )
                            }
                        },
                        headlineContent = {
                            Text(stringRes(R.string.settings__localization__subtype_layout_automatic))
                        },
                        supportingContent = {
                            Text(stringRes(R.string.settings__localization__subtype_layout_automatic_summary))
                        },
                    )
                }
                items(filteredLayouts, key = { it.id }) { layout ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editorBackStackEntry?.savedStateHandle?.set(
                                    SelectLocaleScreenResultCharacterLayoutId,
                                    layout.id,
                                )
                                navController.popBackStack()
                            },
                        leadingContent = {
                            if (currentLayoutId == layout.id) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                )
                            }
                        },
                        headlineContent = { Text(layout.label) },
                        supportingContent = { Text(layout.id) },
                    )
                }
            }
        }
    }
}
