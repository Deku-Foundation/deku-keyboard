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
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.ime.extension.LocalExtensionController
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import org.deku.foundation.dekukeyboard.ime.io.LocalStorageController
import org.deku.foundation.dekukeyboard.ime.io.deleteWorkspace
import org.deku.foundation.dekukeyboard.ime.io.getWorkspaceRef
import org.deku.foundation.dekukeyboard.ime.io.list
import org.deku.foundation.dekukeyboard.ime.keyboard3.extension.Keyboard3Extension
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.LocalInteractionController
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.showLongToast
import org.deku.foundation.dekukeyboard.ime.theme.ThemeExtension
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

enum class ExtensionImportScreenType(
    val id: String,
    @StringRes val titleResId: Int,
    val expectedSerialType: String?,
) {
    EXT_ANY(
        id = "ext-any",
        titleResId = R.string.ext__import__ext_any,
        expectedSerialType = null,
    ),
    EXT_KEYBOARD3(
        id = "ext-keyboard3",
        titleResId = R.string.ext__import__ext_keyboard,
        expectedSerialType = Keyboard3Extension.SERIAL_TYPE,
    ),
    EXT_THEME(
        id = "ext-theme",
        titleResId = R.string.ext__import__ext_theme,
        expectedSerialType = ThemeExtension.SERIAL_TYPE,
    );
}

@Composable
fun ExtensionImportScreen(type: ExtensionImportScreenType, initUuid: String?) = DekuScreen {
    title = stringRes(type.titleResId)
    previewFieldVisible = false

    val extensionController = LocalExtensionController.current
    val storageController = LocalStorageController.current
    val storage by storageController.activeStorage.collectAsState()
    val interactionController = LocalInteractionController.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    // External ACTION_VIEW/SEND imports are copied to an app-owned cache workspace first. Resolve
    // exactly one file from that workspace; never trust a path supplied by the incoming Intent.
    val workspaceFile = remember(initUuid, storage) {
        initUuid?.let { id ->
            runCatching {
                val workspace = storage.getWorkspaceRef(id)
                storage.list(workspace).singleOrNull()
            }.getOrNull()
        }
    }
    val selectedRef = workspaceFile ?: selectedUri?.let { DekuRef.from(it) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            selectedUri = it
            inlineError = null
        },
    )

    val successMessage = stringRes(R.string.ext__import__success)
    val failureMessage = stringRes(R.string.ext__import__failure)

    content {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Import a local .flex package. Deku validates its manifest and ZIP paths before installation. No network connection is used.",
                style = MaterialTheme.typography.bodyMedium,
            )

            selectedRef?.let { source ->
                Text(
                    text = source.pathName,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } ?: Text(
                text = stringRes(R.string.state__no_files_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting,
                onClick = {
                    launcher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
            ) {
                Text(stringRes(R.string.action__select_files))
            }

            inlineError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedRef != null && !isImporting,
                onClick = {
                    val source = selectedRef ?: return@Button
                    scope.launch {
                        isImporting = true
                        inlineError = null
                        try {
                            runCatching {
                                extensionController.importExtension(source, type.expectedSerialType)
                            }.onSuccess {
                                interactionController.showLongToast(successMessage)
                                if (initUuid != null) {
                                    runCatching { storage.deleteWorkspace(initUuid) }
                                }
                                navController.popBackStack()
                            }.onFailure { error ->
                                val message = error.localizedMessage ?: error.javaClass.simpleName
                                inlineError = message
                                interactionController.showLongToast(
                                    failureMessage,
                                    "error_message" to message,
                                )
                            }
                        } finally {
                            isImporting = false
                        }
                    }
                },
            ) {
                Text(
                    text = if (isImporting) {
                        stringRes(R.string.state__loading)
                    } else {
                        stringRes(R.string.action__import)
                    },
                )
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting,
                onClick = {
                    if (initUuid != null) runCatching { storage.deleteWorkspace(initUuid) }
                    navController.popBackStack()
                },
            ) {
                Text(stringRes(R.string.action__cancel))
            }
        }
    }
}
