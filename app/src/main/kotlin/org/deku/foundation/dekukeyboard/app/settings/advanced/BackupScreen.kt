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

import android.content.ContentUris
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import org.deku.foundation.dekukeyboard.BuildConfig
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.DekuPreferenceModel
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.clipboardManager
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ClipboardFileStorage
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ItemType
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.material.ui.JetPrefListItem
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.deku.foundation.dekukeyboard.lib.android.writeFromFile
import org.deku.foundation.dekukeyboard.lib.compose.DekuButtonBar
import org.deku.foundation.dekukeyboard.lib.compose.DekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.defaultDekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.rippleClickable
import org.deku.foundation.dekukeyboard.lib.compose.stringRes
import org.deku.foundation.dekukeyboard.lib.kotlin.io.subDir
import org.deku.foundation.dekukeyboard.lib.kotlin.io.subFile
import org.deku.foundation.dekukeyboard.lib.kotlin.io.writeJson

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

        androidx.compose.material3.Text(
            text = stringRes(R.string.backup_and_restore__back_up__coming_soon),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
