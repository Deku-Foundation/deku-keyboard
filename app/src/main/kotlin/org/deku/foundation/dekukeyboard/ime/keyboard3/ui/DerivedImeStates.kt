/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.keyboard3.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.deku.foundation.dekukeyboard.clipboardManager
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeActions
import org.deku.foundation.dekukeyboard.ime.keyboard3.LocalImeController
import org.deku.foundation.dekukeyboard.subtypeManager
import org.deku.foundation.dekukeyboard.lib.android.AndroidKeyguardManager
import org.deku.foundation.dekukeyboard.lib.android.systemService
import org.k3lp.lib.text.K3Descriptor
import org.k3lp.lib.text.K3String
import org.k3lp.lib.text.K3StringOrDescriptor

@Composable
fun rememberDerivedEnabledState(
    output: K3StringOrDescriptor,
): State<Boolean> {
    val context = LocalContext.current

    val clipboardManager by context.clipboardManager()
    val primaryClip by clipboardManager.primaryClipFlow.collectAsState()

    val imeController = LocalImeController.current
    val imeState by imeController.activeState.collectAsState()

    val subtypeManager by context.subtypeManager()
    val subtypes by subtypeManager.subtypesFlow.collectAsState()

    val androidKeyguardManager = remember {
        context.systemService(AndroidKeyguardManager::class)
    }
    val isDeviceLockedState = remember(androidKeyguardManager) {
        mutableStateOf(
            isImeClipboardRestricted(
                isDeviceLocked = androidKeyguardManager.isDeviceLocked,
                isKeyguardLocked = androidKeyguardManager.isKeyguardLocked,
            ),
        )
    }
    DisposableEffect(context, androidKeyguardManager) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                isDeviceLockedState.value = when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> true
                    else -> isImeClipboardRestricted(
                        isDeviceLocked = androidKeyguardManager.isDeviceLocked,
                        isKeyguardLocked = androidKeyguardManager.isKeyguardLocked,
                    )
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    val isDeviceLocked by isDeviceLockedState

    return remember(output) {
        derivedStateOf {
            val isSelectionCollapsed = imeState.content.selection.isCollapsed()
            val isPrimaryClipPastable = clipboardManager.canBePasted(primaryClip)
            val isRichInputEditor = imeState.editor.info.isRichInputEditor
            val isMultiLingual = subtypes.size > 1
            val isDeviceLocked = isDeviceLocked
            when (output) {
                is K3Descriptor -> when (output.name) {
                    ImeActions.ClipboardCopy.name,
                    ImeActions.ClipboardCut.name -> {
                        !isDeviceLocked && !isSelectionCollapsed
                    }
                    ImeActions.ClipboardPaste.name -> {
                        !isDeviceLocked && isPrimaryClipPastable
                    }
                    ImeActions.ShowClipboardPanel.name -> {
                        !isDeviceLocked
                    }
                    ImeActions.ClipboardClearPrimaryClip.name -> {
                        isPrimaryClipPastable
                    }
                    ImeActions.SelectAll.name -> {
                        isRichInputEditor
                    }
                    ImeActions.SwitchToNextSubtype.name -> {
                        isMultiLingual
                    }
                    else -> true
                }
                is K3String -> true
            }
        }
    }
}

internal fun isImeClipboardRestricted(
    isDeviceLocked: Boolean,
    isKeyguardLocked: Boolean,
): Boolean = isDeviceLocked || isKeyguardLocked
