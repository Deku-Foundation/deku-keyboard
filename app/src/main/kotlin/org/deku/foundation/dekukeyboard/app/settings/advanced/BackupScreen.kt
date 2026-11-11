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

package org.deku.foundation.dekukeyboard.app.settings.advanced

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.state.ToggleableState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.deku.foundation.dekukeyboard.BuildConfig
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

object Backup {
    const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.provider.file"
    const val METADATA_JSON_NAME = "backup_metadata.json"
    const val CLIPBOARD_TEXT_ITEMS_JSON_NAME = "clipboard_text_items.json"
    const val CLIPBOARD_IMAGES_JSON_NAME = "clipboard_images.json"
    const val CLIPBOARD_VIDEO_JSON_NAME = "clipboard_video.json"

    fun defaultFileName(metadata: Metadata): String {
        return "backup_${metadata.packageName}_${metadata.versionCode}_${metadata.timestamp}.zip"
    }

    enum class Destination {
        FILE_SYS,
        SHARE_INTENT;
    }

    class FilesSelector {
        var jetprefDatastore by mutableStateOf(true)
        var imeKeyboard by mutableStateOf(true)
        var imeTheme by mutableStateOf(true)
        var clipboardTextItems by mutableStateOf(false)
        var clipboardImageItems by mutableStateOf(false)
        var clipboardVideoItems by mutableStateOf(false)

        private var _clipboardData: MutableState<ToggleableState> = mutableStateOf(ToggleableState.Off)
        val clipboardData: State<ToggleableState> = _clipboardData

        fun updateCheckboxState() {
            val newValue = if (
                !clipboardVideoItems && !clipboardImageItems && !clipboardTextItems
            ) {
                ToggleableState.Off
            } else if (
                clipboardVideoItems && clipboardImageItems && clipboardTextItems
            ) {
                ToggleableState.On
            } else {
                ToggleableState.Indeterminate
            }
            _clipboardData.value = newValue
        }

        fun provideClipboardItems(): Boolean {
            return clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }

        fun atLeastOneSelected(): Boolean {
            return jetprefDatastore || imeKeyboard || imeTheme || clipboardTextItems || clipboardImageItems || clipboardVideoItems
        }
    }

    @Serializable
    data class Metadata(
        @SerialName("package")
        val packageName: String,
        val versionCode: Int,
        val versionName: String,
        val timestamp: Long,
    )
}

@Composable
fun BackupScreen() {
    DekuScreen {
        title = stringRes(R.string.backup_and_restore__back_up__title)
        previewFieldVisible = false

        Text(text = stringRes(R.string.backup_and_restore__back_up__coming_soon))
    }
}
