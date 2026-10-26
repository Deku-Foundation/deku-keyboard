/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.app.ext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.ime.extension.LocalExtensionController
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

@Composable
fun CheckUpdatesScreen() = DekuScreen {
    title = stringRes(R.string.ext__check_updates__title)

    val extensionController = LocalExtensionController.current
    val extensionIndex by extensionController.activeIndex.collectAsState()

    content {
        UpdateBox(extensionIndex)
    }
}
