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

package org.deku.foundation.dekukeyboard.app.settings.theme

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionComponentName
import org.deku.foundation.dekukeyboard.ime.theme.LocalThemeController
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.lib.compose.DekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.defaultDekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.rippleClickable
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

enum class ThemeManagerScreenAction(val id: String) {
    SELECT_DAY("select-day"),
    SELECT_NIGHT("select-night");
}

@Composable
fun ThemeManagerScreen(action: ThemeManagerScreenAction?) = DekuScreen {
    title = stringRes(when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> R.string.settings__theme_manager__title_day
        ThemeManagerScreenAction.SELECT_NIGHT -> R.string.settings__theme_manager__title_night
        else -> error("Theme manager screen action must not be null")
    })
    previewFieldVisible = true

    val prefs by DekuPreferenceStore
    val themeController = LocalThemeController.current
    val scope = rememberCoroutineScope()

    val themeIndex by themeController.effectiveThemeIndex.collectAsState()
    val themeIdPref = remember(action) {
        when (action) {
            ThemeManagerScreenAction.SELECT_DAY -> prefs.theme.dayThemeId
            ThemeManagerScreenAction.SELECT_NIGHT -> prefs.theme.nightThemeId
        }
    }
    val activeThemeId by themeIdPref.collectAsState()

    fun setTheme(extId: String, componentId: String) {
        val extComponentName = ExtensionComponentName(extId, componentId)
        scope.launch {
            themeIdPref.set(extComponentName)
        }
    }

    content {
        DisposableEffect(activeThemeId) {
            themeController.activePreviewThemeId.value = activeThemeId
            onDispose {
                themeController.activePreviewThemeId.value = null
            }
        }
        val grayColor = LocalContentColor.current.copy(alpha = 0.56f)
        for ((extensionId, extension) in themeIndex.extensions) key(extensionId) {
            val components = extension.manifest.themes.sortedBy { it.name }
                DekuOutlinedBox(
                modifier = Modifier.defaultDekuOutlinedBox(),
                title = extension.manifest.meta.title,
                subtitle = extensionId,
            ) {
                for (component in components) key(extensionId, component.id) {
                    JetPrefListItem(
                        modifier = Modifier.rippleClickable {
                            setTheme(extensionId, component.id)
                        },
                        icon = {
                            RadioButton(
                                selected = activeThemeId.extensionId == extensionId &&
                                    activeThemeId.componentId == component.id,
                                onClick = null,
                            )
                        },
                        text = component.name,
                        trailing = {
                            Icon(
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                imageVector = if (component.isNightTheme) {
                                    Icons.Default.DarkMode
                                } else {
                                    Icons.Default.LightMode
                                },
                                contentDescription = null,
                                tint = grayColor,
                            )
                        },
                    )
                }
            }
        }
    }
}
