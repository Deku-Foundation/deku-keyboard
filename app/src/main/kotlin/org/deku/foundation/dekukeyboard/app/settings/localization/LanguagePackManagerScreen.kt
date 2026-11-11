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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.app.Routes
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

enum class LanguagePackManagerScreenAction(val id: String) {
    MANAGE("manage-installed-language-packs");
}

@Composable
fun LanguagePackManagerScreen(action: LanguagePackManagerScreenAction?) = DekuScreen {
    title = stringRes(R.string.settings__localization__language_pack_title)
    val navController = LocalNavController.current

    content {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringRes(R.string.settings__localization__language_pack_info),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (action == LanguagePackManagerScreenAction.MANAGE) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        navController.navigate(Routes.Settings.SubtypeAdd)
                    },
                ) {
                    Text(stringRes(R.string.settings__localization__browse_languages))
                }
            }
        }
    }
}
