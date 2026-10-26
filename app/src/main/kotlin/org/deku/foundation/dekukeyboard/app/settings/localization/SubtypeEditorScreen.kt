/*
 * Copyright (C) 2021-2026 The FlorisBoard Contributors
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.app.Routes
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.lib.DekuLocale
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes
import org.deku.foundation.dekukeyboard.subtypeManager
import androidx.compose.runtime.collectAsState

@Composable
fun SubtypeEditorScreen(id: Long?) = DekuScreen {
    title = stringRes(
        if (id == null) {
            R.string.settings__localization__subtype_add_title
        } else {
            R.string.settings__localization__subtype_edit_title
        }
    )
    scrollable = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val subtypeManager by context.subtypeManager()
    val bundledCharacterLayouts = remember(context) { loadBundledCharacterLayouts(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuredSubtypes by subtypeManager.subtypesFlow.collectAsState()
    val subtypeManagerReady by subtypeManager.isInitializedFlow.collectAsState()
    val editorBackStackEntry = remember(navController) { navController.currentBackStackEntry }
    val existingSubtype = remember(id, configuredSubtypes) {
        id?.let(subtypeManager::getSubtypeById)
    }

    var selectedLanguageTag by rememberSaveable(id) {
        mutableStateOf(existingSubtype?.primaryLocale?.languageTag().orEmpty())
    }
    var selectedCharacterLayoutId by rememberSaveable(id) {
        mutableStateOf(existingSubtype?.characterLayoutId.orEmpty())
    }
    var errorMessageId by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(existingSubtype?.id, existingSubtype?.characterLayoutId) {
        if (id != null && selectedLanguageTag.isBlank() && existingSubtype != null) {
            selectedLanguageTag = existingSubtype.primaryLocale.languageTag()
            selectedCharacterLayoutId = existingSubtype.characterLayoutId.orEmpty()
        }
    }

    val selectedLocale = remember(selectedLanguageTag) {
        selectedLanguageTag.takeIf { it.isNotBlank() }?.let { DekuLocale.fromTag(it) }
    }
    val localeResult = editorBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>(SelectLocaleScreenResultLanguageTag)
    val layoutResult = editorBackStackEntry
        ?.savedStateHandle
        ?.getLiveData<String>(SelectLocaleScreenResultCharacterLayoutId)

    DisposableEffect(localeResult, layoutResult, lifecycleOwner) {
        val localeObserver = Observer<String> { languageTag ->
            selectedLanguageTag = languageTag
            errorMessageId = null
        }
        val layoutObserver = Observer<String> { layoutId ->
            selectedCharacterLayoutId = layoutId
            errorMessageId = null
        }
        localeResult?.observe(lifecycleOwner, localeObserver)
        layoutResult?.observe(lifecycleOwner, layoutObserver)
        onDispose {
            localeResult?.removeObserver(localeObserver)
            layoutResult?.removeObserver(layoutObserver)
        }
    }

    content {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.settings__localization__subtype_select_locale),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    errorMessageId = null
                    navController.navigate(Routes.Settings.SelectLocale)
                },
            ) {
                Text(
                    text = selectedLocale?.displayName()
                        ?: stringRes(R.string.settings__localization__subtype_select_locale)
                )
            }

            if (selectedLanguageTag.isNotBlank()) {
                Text(
                    text = selectedLanguageTag,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringRes(R.string.settings__localization__subtype_characters_layout),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
            val selectedLayoutLabel = bundledCharacterLayouts
                .firstOrNull { it.id == selectedCharacterLayoutId }?.label
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    editorBackStackEntry?.savedStateHandle?.set(
                        SelectCharacterLayoutScreenCurrentLayoutId,
                        selectedCharacterLayoutId,
                    )
                    navController.navigate(Routes.Settings.SelectCharacterLayout)
                },
            ) {
                Text(
                    text = when {
                        selectedCharacterLayoutId.isBlank() ->
                            stringRes(R.string.settings__localization__subtype_layout_automatic)
                        selectedLayoutLabel != null -> selectedLayoutLabel
                        else -> stringRes(
                            R.string.settings__localization__subtype_error_layout_not_installed,
                            "layout_id" to selectedCharacterLayoutId,
                        )
                    },
                )
            }
            errorMessageId?.let { messageId ->
                Text(
                    text = stringRes(messageId),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedLocale != null && subtypeManagerReady,
                onClick = {
                    val locale = selectedLocale
                    if (locale == null) {
                        errorMessageId = R.string.settings__localization__subtype_error_fields_no_value
                        return@Button
                    }

                    val current = id?.let(subtypeManager::getSubtypeById)
                    if (id != null && current == null) {
                        errorMessageId = R.string.settings__localization__subtype_error_fields_no_value
                        return@Button
                    }

                    val layoutId = selectedCharacterLayoutId.takeIf { it.isNotBlank() }
                    val candidate = current?.copy(
                        primaryLocale = locale,
                        characterLayoutId = layoutId,
                    ) ?: Subtype(
                        id = 0L,
                        primaryLocale = locale,
                        secondaryLocales = emptyList(),
                        nlpProviders = Subtype.DEFAULT.nlpProviders,
                        characterLayoutId = layoutId,
                    )
                    val duplicate = subtypeManager.subtypes.any { other ->
                        other.id != id && other.equalsExcludingId(candidate)
                    }
                    if (duplicate) {
                        errorMessageId = R.string.settings__localization__subtype_error_already_exists
                        return@Button
                    }

                    if (id == null) {
                        if (!subtypeManager.addSubtype(candidate)) {
                            errorMessageId = R.string.settings__localization__subtype_error_already_exists
                            return@Button
                        }
                        errorMessageId = null
                        // Adding a layout does not have to change the active language immediately.
                        // The localization list marks the active subtype and lets the user switch
                        // deliberately, while preserving their existing typing context.
                        navController.popBackStack()
                    } else {
                        subtypeManager.modifySubtypeWithSameId(candidate.copy(id = id))
                        errorMessageId = null
                        navController.popBackStack()
                    }
                },
            ) {
                Text(stringRes(R.string.action__save))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.popBackStack() },
            ) {
                Text(stringRes(R.string.action__cancel))
            }
        }
    }
}
