/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.landscapeinput

import android.annotation.SuppressLint
import android.inputmethodservice.ExtractEditText
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.deku.foundation.dekukeyboard.DekuImeService
import org.deku.foundation.dekukeyboard.R
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.io.LocalStorageController
import org.deku.foundation.dekukeyboard.ime.keyboard3.LocalImeController
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.LocalInteractionController
import org.deku.foundation.dekukeyboard.ime.keyboard3.interaction.rememberAndroidInteractionController
import org.deku.foundation.dekukeyboard.ime.theme.DekuImeTheme
import org.deku.foundation.dekukeyboard.ime.theme.DekuImeUi
import org.deku.foundation.dekukeyboard.ime.theme.LocalThemeController
import org.deku.foundation.dekukeyboard.ime.window.LocalWindowController
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import org.deku.foundation.dekukeyboard.lib.compose.ProvideLocalizedResources
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggBox
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggButton
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggRow
import org.deku.foundation.dekukeyboard.lib.snygg.ui.SnyggText
import org.deku.foundation.dekukeyboard.lib.snygg.ui.rememberSnyggThemeQuery

@SuppressLint("ViewConstructor")
class ExtractedInputRootView(val ims: DekuImeService, eet: ExtractEditText?) : FrameLayout(ims) {
    val composeView: ComposeView
    val extractEditText: ExtractEditText

    init {
        isHapticFeedbackEnabled = true
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        extractEditText = (eet ?: ExtractEditText(context)).also {
            it.id = android.R.id.inputExtractEditText
            it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            it.background = null
            it.gravity = Gravity.TOP
            it.isVerticalScrollBarEnabled = true
        }
        addView(extractEditText)

        composeView = ComposeView(context).also { it.setContent { Content() } }
        addView(composeView)
    }

    @Composable
    fun Content() {
        val prefs by DekuPreferenceStore
        val interactionController = rememberAndroidInteractionController(prefs)

        CompositionLocalProvider(
            LocalImeController provides ims.imeController,
            LocalInteractionController provides interactionController,
            LocalStorageController provides ims.storageController,
            LocalThemeController provides ims.themeController,
            LocalWindowController provides ims.windowController,
        ) {
            ProvideLocalizedResources(
                ims.resourcesContext,
                appName = R.string.app_name,
                forceLayoutDirection = LayoutDirection.Ltr,
            ) {
                DekuImeTheme {
                    val imeState by ims.imeController.activeState.collectAsState()
                    val editorInfo by remember { derivedStateOf { imeState.editor.info } }
                    val rootInsets by ims.windowController.activeRootInsets.collectAsState()
                    val windowInsets by ims.windowController.activeWindowInsets.collectAsState()
                    val height by remember {
                        derivedStateOf {
                            val rootBounds = rootInsets.boundsDp
                            val windowBounds = windowInsets?.boundsDp ?: return@derivedStateOf 0.dp
                            rootBounds.height - windowBounds.height
                        }
                    }
                    SnyggBox(DekuImeUi.ExtractedLandscapeInputLayout.elementName) {
                        SnyggRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(height),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SnyggBox(
                                elementName = DekuImeUi.ExtractedLandscapeInputLayout.elementName,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(1f),
                            ) {
                                val fieldStyle =
                                    rememberSnyggThemeQuery(DekuImeUi.ExtractedLandscapeInputField.elementName)
                                val foreground = fieldStyle.foreground()
                                AndroidView(
                                    factory = { extractEditText },
                                    update = { view ->
                                        view.background = null
                                        view.backgroundTintList = null
                                        view.foregroundTintList = null
                                        view.setTextColor(foreground.toArgb())
                                        view.setHintTextColor(foreground.copy(foreground.alpha * 0.6f).toArgb())
                                        view.setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            fieldStyle.fontSize(default = 16.sp).value,
                                        )
                                    },
                                )
                            }
                            SnyggButton(
                                DekuImeUi.ExtractedLandscapeInputAction.elementName,
                                onClick = {
                                    ims.imeController.updateStateBlocking {
                                        if (editorInfo.extractedActionId != 0) {
                                            state.editor.ic.get()?.performEditorAction(editorInfo.extractedActionId)
                                        } else {
                                            state.editor.performEditorAction(editorInfo.imeOptions.action)
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                SnyggText(
                                    text = editorInfo.extractedActionLabel
                                        ?: ims.getTextForImeAction(editorInfo.imeOptions.action.toInt())
                                        ?: "ACTION",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun getAccessibilityClassName(): CharSequence {
        return javaClass.name
    }

    override fun onAttachedToWindow() {
        removeView(extractEditText)
        super.onAttachedToWindow()
        try {
            (parent as LinearLayout).let { extractEditLayout ->
                extractEditLayout.layoutParams = LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                ).also { it.setMargins(0, 0, 0, 0) }
                extractEditLayout.setPadding(0, 0, 0, 0)
            }
        } catch (e: Throwable) {
            flogError { e.message.toString() }
        }
    }
}
