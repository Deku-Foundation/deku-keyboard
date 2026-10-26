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

package org.deku.foundation.dekukeyboard.app.ext

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.ime.extension.Extension
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionDefaults
import org.deku.foundation.dekukeyboard.ime.extension.LocalExtensionController
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.LocalInteractionController
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.showLongToast
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

@Composable
fun ExtensionExportScreen(id: String) {
    val extensionController = LocalExtensionController.current
    val extensionIndex by extensionController.activeIndex.collectAsState()

    val extension = extensionIndex.extensions[id]
    if (extension != null) {
        ExportScreen(extension)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ExportScreen(extension: Extension<*>) = DekuScreen {
    title = extension.manifest.meta.title
    scrollable = false

    val extensionController = LocalExtensionController.current
    val interactionController = LocalInteractionController.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val successMsg = stringRes(R.string.ext__export__success)
    val failureMsg = stringRes(R.string.ext__export__failure)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(),
        onResult = { uri ->
            // If uri is null it indicates that the selection activity
            //  was cancelled (mostly by pressing the back button), so
            //  we don't display an error message here.
            if (uri == null) {
                navController.popBackStack()
                return@rememberLauncherForActivityResult
            }
            val ref = DekuRef.from(uri)
            scope.launch {
                runCatching { extensionController.export(extension, ref) }.onSuccess {
                    interactionController.showLongToast(successMsg)
                }.onFailure { error ->
                    interactionController.showLongToast(failureMsg, "error_message" to error.localizedMessage)
                }
                navController.popBackStack()
            }
        },
    )

    content {
        exportLauncher.launch(ExtensionDefaults.createFlexName(extension.manifest.meta.id))
    }
}
