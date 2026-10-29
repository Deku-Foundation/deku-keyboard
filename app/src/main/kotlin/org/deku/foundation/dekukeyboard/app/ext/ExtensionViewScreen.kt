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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.LocalNavController
import org.deku.foundation.dekukeyboard.app.Routes
import org.deku.foundation.dekukeyboard.ime.extension.Extension
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionMaintainer
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionMeta
import org.deku.foundation.dekukeyboard.ime.extension.LocalExtensionController
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.LocalInteractionController
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.showLongToast
import org.deku.foundation.dekukeyboard.ime.theme.ThemeExtension
import org.deku.foundation.dekukeyboard.lib.compose.DekuConfirmDeleteDialog
import org.deku.foundation.dekukeyboard.lib.compose.DekuHyperlinkText
import org.deku.foundation.dekukeyboard.lib.compose.DekuScreen
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.lib.compose.DekuOutlinedButton
import org.deku.foundation.dekukeyboard.lib.compose.defaultDekuOutlinedBox
import org.deku.foundation.dekukeyboard.lib.compose.stringRes

@Composable
fun ExtensionViewScreen(id: String) {
    val extensionController = LocalExtensionController.current
    val extensionIndex by extensionController.activeIndex.collectAsState()

    val extension = extensionIndex.extensions[id]
    if (extension != null) {
        ViewScreen(extension)
    } else {
        ExtensionNotFoundScreen(id)
    }
}

@Composable
private fun ViewScreen(extension: Extension<*>) = DekuScreen {
    title = extension.manifest.meta.title

    val extensionController = LocalExtensionController.current
    val interactionController = LocalInteractionController.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val meta = extension.manifest.meta
    var extToDelete by remember { mutableStateOf<Extension<*>?>(null) }

    content {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            meta.description?.let { Text(it) }
            Spacer(modifier = Modifier.height(16.dp))
            ExtensionMetaRowScrollableChips(
                label = stringRes(R.string.ext__meta__maintainers),
                showDividerAbove = false,
            ) {
                for ((n, maintainer) in meta.maintainers.withIndex()) {
                    if (n > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    ExtensionMaintainerChip(maintainer)
                }
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__id)) {
                Text(text = meta.id)
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__version)) {
                Text(text = meta.version)
            }
            if (!meta.keywords.isNullOrEmpty()) {
                ExtensionMetaRowScrollableChips(label = stringRes(R.string.ext__meta__keywords)) {
                    for ((n, keyword) in meta.keywords.withIndex()) {
                        if (n > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        ExtensionKeywordChip(keyword)
                    }
                }
            }
            if (!meta.homepage.isNullOrBlank()) {
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__homepage)) {
                    DekuHyperlinkText(
                        text = DekuRef.fromUrl(meta.homepage).authority,
                        url = meta.homepage,
                    )
                }
            }
            if (!meta.issueTracker.isNullOrBlank()) {
                ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__issue_tracker)) {
                    DekuHyperlinkText(
                        text = DekuRef.fromUrl(meta.issueTracker).authority,
                        url = meta.issueTracker,
                    )
                }
            }
            ExtensionMetaRowSimpleText(label = stringRes(R.string.ext__meta__license)) {
                Text(text = meta.license)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (extension.canDelete()) {
                    DekuOutlinedButton(
                        onClick = {
                            extToDelete = extension
                        },
                        icon = Icons.Default.Delete,
                        text = stringRes(R.string.action__delete),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                DekuOutlinedButton(
                    onClick = {
                        navController.navigate(Routes.Ext.Export(meta.id))
                    },
                    icon = Icons.Default.Share,
                    text = stringRes(R.string.action__export),
                )
            }
        }

        when (extension) {
            is ThemeExtension -> {
                ExtensionComponentListView(
                    title = stringRes(R.string.ext__meta__components_theme),
                    components = extension.manifest.themes,
                ) { component ->
                    ExtensionComponentView(
                        modifier = Modifier.defaultDekuOutlinedBox(),
                        meta = meta,
                        component = component,
                    )
                }
            }
            else -> Unit
        }

        val extensionToDelete = extToDelete
        if (extensionToDelete != null) {
            val snackbarMsg = stringRes(R.string.error__snackbar_message)
            DekuConfirmDeleteDialog(
                onConfirm = {
                    scope.launch {
                        runCatching {
                            extensionController.delete(extensionToDelete)
                        }.onSuccess {
                            navController.popBackStack()
                        }.onFailure { error ->
                            interactionController.showLongToast(
                                snackbarMsg,
                                "error_message" to error.localizedMessage,
                            )
                        }
                        extToDelete = null
                    }
                },
                onDismiss = { extToDelete = null },
                what = extensionToDelete.manifest.meta.title,
            )
        }
    }
}

@Composable
private fun ExtensionMetaRowSimpleText(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.padding(end = 24.dp), text = label)
        content()
    }
}

@Composable
private fun ExtensionMetaRowScrollableChips(
    label: String,
    modifier: Modifier = Modifier,
    showDividerAbove: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (showDividerAbove) {
        HorizontalDivider()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(modifier = Modifier.padding(end = 24.dp), text = label)
        Row(
            modifier = Modifier
                .weight(1.0f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun PreviewExtensionViewerScreen() {
    val testExtension = ThemeExtension(
        manifest = ThemeExtension.Manifest(
            meta = ExtensionMeta(
                id = "com.example.theme.test",
                version = "2.4.3",
                title = "Test theme",
                description = "This is a test theme to preview the extension viewer screen UI.",
                keywords = listOf("Beach", "Sea", "Sun"),
                homepage = "https://example.com",
                issueTracker = "https://git.example.com/issues",
                maintainers = listOf(
                    "Max Mustermann <max.mustermann@example.com> (maxmustermann.example.com)",
                ).map { ExtensionMaintainer.fromOrTakeRaw(it) },
                license = "apache-2.0",
            ),
            dependencies = emptyMap(),
            themes = listOf(
                ThemeExtension.ThemeComponent(
                    id = "test",
                    name = "Test",
                    authors = listOf(),
                    stylesheetPath = "test.json",
                ),
            ),
        ),
        sourceRef = DekuRef.assets("demo/com.example.theme.test"),
    )
    ViewScreen(testExtension)
}
