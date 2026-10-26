/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.ime.core.DisplayLanguageNamesIn
import org.deku.foundation.dekukeyboard.lib.DekuLocale
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.deku.foundation.dekukeyboard.lib.compose.dekuScrollbar
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

const val SelectLocaleScreenResultLanguageTag = "SelectLocaleScreen.languageTag"
const val SelectLocaleScreenResultCharacterLayoutId = "SelectLocaleScreen.characterLayoutId"

private data class LocaleOption(
    val locale: DekuLocale,
    val characterLayoutId: String?,
    val sourceLanguageTag: String?,
) {
    val stableKey: String
        get() = locale.languageTag().lowercase(Locale.ROOT) + ":" +
            (characterLayoutId ?: "automatic").lowercase(Locale.ROOT)
}

private fun layoutDisplayName(layoutId: String): String =
    layoutId.replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

@Composable
fun SelectLocaleScreen() = DekuScreen {
    title = stringRes(R.string.settings__localization__subtype_select_locale)
    scrollable = false

    val prefs by DekuPreferenceStore
    val navController = LocalNavController.current
    val context = LocalContext.current
    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    var searchTermValue by remember { mutableStateOf(TextFieldValue()) }

    // Keep locale and layout separate. Some legacy manifest tags encode layout variants that are
    // not valid BCP-47 locale variants, so normalizing the locale must not lose the selected layout.
    val bundledPresets: List<BundledLocalePreset> = remember(context) {
        runCatching {
            context.assets.open(BUNDLED_LOCALIZATION_MANIFEST)
                .bufferedReader(Charsets.UTF_8)
                .use { reader -> parseBundledLocalePresets(reader.readText()) }
        }.getOrDefault(emptyList())
    }

    val localeOptions = remember(displayLanguageNamesIn, bundledPresets) {
        val presetOptions = bundledPresets.map { preset ->
            LocaleOption(preset.locale, preset.characterLayoutId, preset.sourceLanguageTag)
        }
        val bundledLocaleTags = presetOptions.map {
            it.locale.languageTag().lowercase(Locale.ROOT)
        }.toSet()
        val systemOnlyOptions = DekuLocale.extendedAvailableLocales()
            .distinctBy { it.languageTag().lowercase(Locale.ROOT) }
            .filterNot { it.languageTag().lowercase(Locale.ROOT) in bundledLocaleTags }
            .map { LocaleOption(it, null, it.languageTag()) }

        (presetOptions + systemOnlyOptions)
            .distinctBy { it.stableKey }
            .sortedWith(
                compareBy<LocaleOption> {
                    when (displayLanguageNamesIn) {
                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> it.locale.displayName()
                        DisplayLanguageNamesIn.NATIVE_LOCALE -> it.locale.displayName(it.locale)
                    }.lowercase(Locale.ROOT)
                }.thenBy { it.characterLayoutId.orEmpty().lowercase(Locale.ROOT) }
                    .thenBy { it.locale.languageTag().lowercase(Locale.ROOT) }
            )
    }

    val filteredLocaleOptions = remember(searchTermValue, localeOptions) {
        val term = searchTermValue.text.trim().lowercase(Locale.ROOT)
        if (term.isBlank()) {
            localeOptions
        } else {
            localeOptions.filter { option ->
                option.locale.displayName().lowercase(Locale.ROOT).contains(term) ||
                    option.locale.displayName(option.locale).lowercase(Locale.ROOT).contains(term) ||
                    option.locale.displayName(DekuLocale.ENGLISH).lowercase(Locale.ROOT).contains(term) ||
                    option.locale.languageTag().lowercase(Locale.ROOT).startsWith(term) ||
                    option.locale.localeTag().lowercase(Locale.ROOT).startsWith(term) ||
                    option.sourceLanguageTag.orEmpty().lowercase(Locale.ROOT).startsWith(term) ||
                    option.characterLayoutId.orEmpty().lowercase(Locale.ROOT).contains(term)
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
                placeholder = { Text(stringRes(R.string.settings__localization__subtype_search_locale_placeholder)) },
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
            if (filteredLocaleOptions.isEmpty()) {
                Text(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.CenterHorizontally),
                    text = stringRes(
                        R.string.settings__localization__subtype_search_locale_not_found,
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
                items(filteredLocaleOptions, key = { it.stableKey }) { option ->
                    val localeName = when (displayLanguageNamesIn) {
                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> option.locale.displayName()
                        DisplayLanguageNamesIn.NATIVE_LOCALE -> option.locale.displayName(option.locale)
                    }
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(SelectLocaleScreenResultLanguageTag, option.locale.languageTag())
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(
                                        SelectLocaleScreenResultCharacterLayoutId,
                                        option.characterLayoutId.orEmpty(),
                                    )
                                navController.popBackStack()
                            },
                        headlineContent = { Text(localeName) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(option.locale.languageTag())
                                    option.characterLayoutId?.let {
                                        append(" · ")
                                        append(layoutDisplayName(it))
                                    }
                                }
                            )
                        },
                    )
                }
            }
        }
    }
}
