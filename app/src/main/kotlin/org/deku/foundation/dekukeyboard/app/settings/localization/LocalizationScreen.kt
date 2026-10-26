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

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.app.Routes
import org.deku.foundation.dekukeyboard.app.enumDisplayEntriesOf
import org.deku.foundation.dekukeyboard.ime.core.DisplayLanguageNamesIn
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.serialization.json.Json
import org.deku.foundation.dekukeyboard.lib.compose.DekuWarningCard
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

internal val SubtypeSaver = Saver<MutableState<Subtype?>, String>(
    save = {
        Json.encodeToString<Subtype?>(it.value)
    },
    restore = {
        mutableStateOf(Json.decodeFromString(it))
    },
)

@Composable
fun LocalizationScreen() = DekuScreen {
    title = stringRes(R.string.settings__localization__title)
    previewFieldVisible = true
    iconSpaceReserved = false

    val navController = LocalNavController.current
    val context = LocalContext.current
    val subtypeManager by context.subtypeManager()
    val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
    val subtypesReady by subtypeManager.isInitializedFlow.collectAsState()
    var chosenSubtypeToDelete: Subtype? by rememberSaveable(saver = SubtypeSaver) { mutableStateOf(null) }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            text = {
                Text(
                    text = stringRes(R.string.settings__localization__subtype_add_title),
                )
            },
            shape = FloatingActionButtonDefaults.extendedFabShape,
            onClick = { if (subtypesReady) navController.navigate(Routes.Settings.SubtypeAdd) },
        )
    }

    content {
        ListPreference(
            prefs.localization.displayLanguageNamesIn,
            title = stringRes(R.string.settings__localization__display_language_names_in__label),
            entries = enumDisplayEntriesOf(DisplayLanguageNamesIn::class),
        )
        SwitchPreference(
            prefs.localization.displayKeyboardLabelsInSubtypeLanguage,
            title = stringRes(R.string.settings__localization__display_keyboard_labels_in_subtype_language),
        )
        Preference(
            title = stringRes(R.string.settings__localization__language_pack_title),
            summary = stringRes(R.string.settings__localization__language_pack_summary),
            onClick = {
                navController.navigate(Routes.Settings.LanguagePackManager(LanguagePackManagerScreenAction.MANAGE))
            },
        )
        PreferenceGroup(title = stringRes(R.string.settings__localization__group_subtypes__label)) {
            val subtypes by subtypeManager.subtypesFlow.collectAsState()
            if (!subtypesReady) {
                Text(
                    modifier = Modifier.padding(all = 8.dp),
                    text = stringRes(R.string.settings__localization__loading),
                )
            } else if (subtypes.isEmpty()) {
                DekuWarningCard(
                    modifier = Modifier.padding(all = 8.dp),
                    text = stringRes(R.string.settings__localization__subtype_no_subtypes_configured_warning),
                )
            } else {
                val activeSubtype by subtypeManager.activeSubtypeFlow.collectAsState()
                for (subtype in subtypes) {
                    val name = when (displayLanguageNamesIn) {
                        DisplayLanguageNamesIn.SYSTEM_LOCALE -> subtype.primaryLocale.displayName()
                        DisplayLanguageNamesIn.NATIVE_LOCALE -> subtype.primaryLocale.displayName(subtype.primaryLocale)
                    }
                    androidx.compose.material3.ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { subtypeManager.switchToSubtypeById(subtype.id) },
                        leadingContent = {
                            if (activeSubtype.id == subtype.id) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = stringRes(R.string.settings__localization__active_language),
                                )
                            }
                        },
                        headlineContent = { Text(name) },
                        supportingContent = { Text(subtype.primaryLocale.languageTag()) },
                        trailingContent = {
                            Row {
                                IconButton(
                                    onClick = {
                                        navController.navigate(Routes.Settings.SubtypeEdit(subtype.id))
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringRes(R.string.settings__localization__subtype_edit_title),
                                    )
                                }
                                IconButton(onClick = { chosenSubtypeToDelete = subtype }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringRes(R.string.action__delete),
                                        tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    DeleteSubtypeConfirmationDialog(
        subtypeToDelete = chosenSubtypeToDelete,
        onDismiss = {
            chosenSubtypeToDelete = null
        },
        onConfirm = {
            chosenSubtypeToDelete?.let { subtypeManager.removeSubtype(subtypeToRemove = it) }
            chosenSubtypeToDelete = null
        }
    )

}

@Composable
fun DeleteSubtypeConfirmationDialog(
    subtypeToDelete: Subtype?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
)   {
    subtypeToDelete?.let {
        JetPrefAlertDialog(
            title = stringRes(R.string.settings__localization__subtype_delete_confirmation_title),
            confirmLabel = stringRes(R.string.action__yes),
            dismissLabel = stringRes(R.string.action__no),
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            ) {
                Text(stringRes(R.string.settings__localization__subtype_delete_confirmation_warning))
            }
    }
}
