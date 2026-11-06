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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import org.deku.foundation.dekukeyboard.BuildConfig
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.DekuPreferenceModel
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.clipboardManager
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ClipboardFileStorage
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ClipboardItem
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ItemType
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import dev.patrickgold.jetpref.datastore.runtime.AndroidAppDataStorage
import dev.patrickgold.jetpref.datastore.runtime.FileBasedStorage
import dev.patrickgold.jetpref.datastore.runtime.ImportStrategy
import dev.patrickgold.jetpref.datastore.ui.Preference
import java.io.FileNotFoundException
import java.text.DateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.lib.android.readToFile
import org.deku.foundation.dekukeyboard.lib.compose.DekuButtonBar
import org.deku.foundation.dekukeyboard.lib.compose.DekuCardDefaults
import org.deku.foundation.dekukeyboard.lib.compose.DekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.DekuOutlinedButton
import org.deku.foundation.dekukeyboard.lib.compose.defaultDekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.stringRes
import org.deku.foundation.dekukeyboard.lib.kotlin.io.deleteContentsRecursively
import org.deku.foundation.dekukeyboard.lib.kotlin.io.readJson
import org.deku.foundation.dekukeyboard.lib.kotlin.io.subDir
import org.deku.foundation.dekukeyboard.lib.kotlin.io.subFile

object Restore {
    const val MIN_VERSION_CODE = 64
    const val PACKAGE_NAME = "org.deku.foundation.dekukeyboard"
    const val BACKUP_ARCHIVE_FILE_NAME = "backup.zip"
}

@Composable
fun RestoreScreen() {
    DekuScreen {
        title = stringRes(R.string.backup_and_restore__restore__title)
        previewFieldVisible = false

        androidx.compose.material3.Text(
            text = stringRes(R.string.backup_and_restore__restore__coming_soon),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
