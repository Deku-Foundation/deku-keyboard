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

package org.deku.foundation.dekukeyboard.ime.keyboard3

import android.content.ClipDescription
import android.content.ContentUris
import android.os.Build
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import org.deku.foundation.dekukeyboard.DekuApplication
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ClipboardFileStorage
import org.deku.foundation.dekukeyboard.ime.clipboard.provider.ClipboardItem
import org.deku.foundation.dekukeyboard.ime.editor.DekuEditorInfo
import org.deku.foundation.dekukeyboard.ime.editor.ImeOptions
import org.k3lp.runtime.K3Editor
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Read selected text at the point of action. A missing connection or unsupported selection query
 * is represented as null; callers must not fall back to a cached selection for clipboard writes.
 */
internal fun readSelectedText(inputConnection: InputConnection?): String? {
    return inputConnection?.getSelectedText(0)?.toString()
}

/**
 * Removes one Unicode code point, not one UTF-16 code unit. Android's text APIs count
 * surrounding text in different units depending on the API, so keep the optimistic local
 * surrounding-text update consistent with code-point-aware forward deletion.
 */
internal fun removeLeadingCodePoint(text: String): String {
    if (text.isEmpty()) return text
    return text.substring(text.offsetByCodePoints(0, 1))
}

/**
 * Splits the text and selection indices returned by InputConnection.getSurroundingText().
 * Invalid indices are rejected so callers can safely fall back to individual cursor queries.
 */
internal fun splitSurroundingText(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): K3SurroundingText? {
    if (selectionStart !in 0..text.length || selectionEnd !in selectionStart..text.length) {
        return null
    }
    return K3SurroundingText(
        textBefore = text.substring(0, selectionStart),
        textSelected = text.substring(selectionStart, selectionEnd),
        textAfter = text.substring(selectionEnd),
    )
}

open class ImeEditor(
    val ic: WeakReference<InputConnection>,
    val info: DekuEditorInfo,
) : K3Editor {
    /**
     * Reads the current selection from the live InputConnection rather than from the cached
     * K3 surrounding-text snapshot. Editors can change selection text independently and may
     * expose large selections that must not be copied from a stale/truncated state snapshot.
     */
    fun getSelectedText(): String? = readSelectedText(ic.get())

    fun getSurroundingText(charsBefore: Int, charsAfter: Int): K3SurroundingText {
        val ic = ic.get() ?: return K3SurroundingText.Empty
        // Android 12+ can return one consistent surrounding-text snapshot in one IPC call.
        // Some host InputConnections do not implement the protocol, so retain the legacy fallback.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val snapshot = runCatching {
                ic.getSurroundingText(charsBefore, charsAfter, 0)
            }.getOrNull()
            snapshot?.let {
                splitSurroundingText(
                    text = it.text.toString(),
                    selectionStart = it.selectionStart,
                    selectionEnd = it.selectionEnd,
                )
            }?.let { return it }
        }

        val textBeforeCursor = ic.getTextBeforeCursor(charsBefore, 0)
            ?: return K3SurroundingText.Empty
        val textAfterCursor = ic.getTextAfterCursor(charsAfter, 0)
            ?: return K3SurroundingText.Empty
        val textSelected = ic.getSelectedText(0) ?: ""
        return K3SurroundingText(
            textBefore = textBeforeCursor.toString(),
            textSelected = textSelected.toString(),
            textAfter = textAfterCursor.toString(),
        )
    }

    // TODO this function is very heavy => we need to optimize k3lp to provide:
    //  insertAtSelection() (for simple appends)
    //  deleteAroundSelection() for bksp/fw delete
    //  in some cases replaceText() is needed due to the transform rules, but we abuse it waaaay too much
    override fun replaceText(
        range: IntRange,
        text: String,
        newSelection: K3TextRange,
        newComposition: K3TextRange?,
    ) {
        val ic = ic.get() ?: return
        ic.beginBatchEdit()
        ic.setComposingRegion(range.first, range.last + 1)
        ic.commitText(text, 1)
        ic.setSelection(newSelection.start, newSelection.end)
        setComposition(newComposition)
        ic.endBatchEdit()
    }

    fun deleteSurroundingText(charsBefore: Int, charsAfter: Int) {
        val ic = ic.get() ?: return
        ic.deleteSurroundingText(charsBefore, charsAfter)
    }

    /**
     * Deletes surrounding Unicode code points. The app's minSdk is 26, so the API 24 method is
     * available on every supported device.
     */
    fun deleteSurroundingTextInCodePoints(charsBefore: Int, charsAfter: Int) {
        val ic = ic.get() ?: return
        ic.deleteSurroundingTextInCodePoints(charsBefore, charsAfter)
    }

    fun performClipboardCopy(): Boolean {
        val ic = ic.get() ?: return false
        return ic.performContextMenuAction(android.R.id.copy)
    }

    fun performClipboardCut(): Boolean {
        val ic = ic.get() ?: return false
        return ic.performContextMenuAction(android.R.id.cut)
    }

    fun performClipboardPaste(): Boolean {
        val ic = ic.get() ?: return false
        return ic.performContextMenuAction(android.R.id.paste)
    }

    fun performClipboardPasteMedia(item: ClipboardItem): Boolean {
        val ic = ic.get() ?: return false
        val uri = item.uri ?: return false
        val mimeTypes = item.mimeTypes
        val id = ContentUris.parseId(uri)
        val appContext = DekuApplication.getHack() // TODO this is a hack
        val file = ClipboardFileStorage.getFileForId(appContext, id)
        if (!file.exists()) return false
        val inputContentInfo = InputContentInfoCompat(
            uri,
            ClipDescription("clipboard media file", mimeTypes.toTypedArray()),
            null,
        )
        ic.finishComposingText()
        val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
        return InputConnectionCompat.commitContent(ic, info.base, inputContentInfo, flags, null)
    }

    fun performSelectAll(): Boolean {
        val ic = ic.get() ?: return false
        ic.finishComposingText()
        return if (info.isRawInputEditor) {
            sendDownUpKeyEvent(KeyEvent.KEYCODE_A, meta(ctrl = true))
        } else {
            ic.performContextMenuAction(android.R.id.selectAll)
        }
    }

    fun performUndo(): Boolean {
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true))
    }

    fun performRedo(): Boolean {
        return sendDownUpKeyEvent(KeyEvent.KEYCODE_Z, meta(ctrl = true, shift = true))
    }

    fun performEditorAction(action: ImeOptions.Action) {
        val ic = ic.get() ?: return
        ic.performEditorAction(action.toInt())
    }

    private fun meta(
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
    ): Int {
        var metaState = 0
        if (ctrl) {
            metaState = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        if (shift) {
            metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        }
        return metaState
    }

    private fun InputConnection.sendDownKeyEvent(keyCode: Int, metaState: Int, downTime: Long) {
        sendKeyEvent(
            KeyEvent(
                downTime, downTime,
                KeyEvent.ACTION_DOWN, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    private fun InputConnection.sendUpKeyEvent(keyCode: Int, metaState: Int, downTime: Long, upTime: Long) {
        sendKeyEvent(
            KeyEvent(
                downTime, upTime,
                KeyEvent.ACTION_UP, keyCode, 0, metaState, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            )
        )
    }

    fun sendDownUpKeyEvent(keyCode: Int, metaState: Int = 0): Boolean {
        val ic = ic.get() ?: return false
        val downTime = SystemClock.uptimeMillis()
        ic.sendDownKeyEvent(keyCode, metaState, downTime)
        val upTime = SystemClock.uptimeMillis()
        ic.sendUpKeyEvent(keyCode, metaState, downTime, upTime)
        return true
    }

    override fun setComposition(newComposition: K3TextRange?) {
        val ic = ic.get() ?: return
        if (newComposition == null || newComposition.isCollapsed()) {
            ic.finishComposingText()
        } else {
            ic.setComposingRegion(newComposition.start, newComposition.end)
        }
    }

    private inline fun InputConnection.batchEdit(block: () -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        requestCursorUpdates(0)
        beginBatchEdit()
        block()
        endBatchEdit()
        requestCursorUpdates(CURSOR_UPDATES)
    }

    companion object {
        /**
         * The input connection update cursor flags.
         *
         * MUST NOT contain filter flags, as these behave strangely with some editors!!
         */
        const val CURSOR_UPDATES = InputConnection.CURSOR_UPDATE_MONITOR or InputConnection.CURSOR_UPDATE_IMMEDIATE

        val Disconnected = ImeEditor(
            ic = WeakReference(null),
            info = DekuEditorInfo.Unspecified,
        )
    }
}
