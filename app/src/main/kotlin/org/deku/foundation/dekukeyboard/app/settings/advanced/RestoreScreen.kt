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
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

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

        Text(text = stringRes(R.string.backup_and_restore__restore__coming_soon))
    }
}
