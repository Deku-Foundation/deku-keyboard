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

package org.deku.foundation.dekukeyboard

import android.content.Context
import android.content.res.Resources
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.inputmethodservice.ExtractEditText
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.widget.inline.InlinePresentationSpec
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import org.deku.foundation.dekukeyboard.app.DekuAppActivity
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.ImeUiMode
import org.deku.foundation.dekukeyboard.ime.editor.DekuEditorInfo
import org.deku.foundation.dekukeyboard.ime.keyboard3.isFullscreenInputRequired
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeEditor
import org.deku.foundation.dekukeyboard.ime.landscapeinput.ExtractedInputRootView
import org.deku.foundation.dekukeyboard.ime.landscapeinput.LandscapeInputUiMode
import org.deku.foundation.dekukeyboard.ime.lifecycle.LifecycleInputMethodService
import org.deku.foundation.dekukeyboard.ime.nlp.NlpInlineAutofill
import org.deku.foundation.dekukeyboard.ime.smartbar.createInlineSuggestionUiStyleBundle
import org.deku.foundation.dekukeyboard.ime.theme.WallpaperChangeReceiver
import org.deku.foundation.dekukeyboard.ime.window.ImeRootView
import org.deku.foundation.dekukeyboard.ime.window.ImeWindowController
import org.deku.foundation.dekukeyboard.lib.devtools.LogTopic
import org.deku.foundation.dekukeyboard.lib.devtools.flogDebug
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import org.deku.foundation.dekukeyboard.lib.devtools.flogInfo
import org.deku.foundation.dekukeyboard.lib.devtools.flogWarning
import org.deku.foundation.dekukeyboard.lib.util.InputMethodUtils
import org.deku.foundation.dekukeyboard.lib.util.debugSummarize
import org.deku.foundation.dekukeyboard.lib.util.launchActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.deku.foundation.dekukeyboard.lib.android.AndroidInternalR
import org.deku.foundation.dekukeyboard.lib.android.AndroidVersion
import org.deku.foundation.dekukeyboard.lib.android.systemServiceOrNull
import org.deku.foundation.dekukeyboard.lib.kotlin.collectIn
import org.deku.foundation.dekukeyboard.lib.kotlin.collectLatestIn
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [DekuImeService] class. This is needed as certain actions (request hide, switch to
 * another input method, getting the editor instance / input connection, etc.) can only be performed by an IME
 * service class and no context-bound managers. This reference is exclusively used by the companion helper methods
 * of [DekuImeService], which provide a safe and memory-leak-free way of performing certain actions on the Deku
 * input method service instance.
 */
private var DekuImeServiceReference = WeakReference<DekuImeService?>(null)

/**
 * Core class responsible for linking together all managers and UI composables to provide an IME service. Sets
 * up the window and context to be lifecycle-aware, so LiveData and Jetpack Compose can be used without issues.
 */
class DekuImeService : LifecycleInputMethodService() {
    companion object {
        private val InlineSuggestionUiSmallestSize = Size(0, 0)
        private val InlineSuggestionUiBiggestSize = Size(Int.MAX_VALUE, Int.MAX_VALUE)

        fun currentInputConnection(): InputConnection? {
            return DekuImeServiceReference.get()?.currentInputConnection
        }

        /**
         * Hides the IME and launches [DekuAppActivity].
         */
        fun launchSettings() {
            val ims = DekuImeServiceReference.get() ?: return
            ims.requestHideSelf(0)
            ims.launchActivity(DekuAppActivity::class) {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        fun showUi() {
            val ims = DekuImeServiceReference.get() ?: return
            ims.showUi()
        }

        fun hideUi() {
            val ims = DekuImeServiceReference.get() ?: return
            ims.hideUi()
        }

        fun switchToPrevInputMethod(): Boolean {
            val ims = DekuImeServiceReference.get() ?: return false
            return ims.switchToPrevInputMethod()
        }

        fun switchToNextInputMethod(): Boolean {
            val ims = DekuImeServiceReference.get() ?: return false
            return ims.switchToNextInputMethod()
        }

        fun switchToVoiceInputMethod(): Boolean {
            val ims = DekuImeServiceReference.get() ?: return false
            return ims.switchToVoiceInputMethod()
        }

        fun showImePicker(): Boolean {
            val ims = DekuImeServiceReference.get() ?: return false
            return InputMethodUtils.showImePicker(ims)
        }

        fun windowControllerOrNull(): ImeWindowController? {
            val ims = DekuImeServiceReference.get() ?: return null
            return ims.windowController
        }
    }

    fun hideUi() {
        requestHideSelf(0)
    }

    /**
     * Show the Ime UI
     *
     * Note: This function can be replaced with a `requestShowSelf(0)`
     * call once we've set the minApiLevel to 28 (Android 9)
     */
    fun showUi() {
        if (AndroidVersion.ATLEAST_API28_P) {
            requestShowSelf(0)
        } else {
            @Suppress("DEPRECATION")
            systemServiceOrNull(InputMethodManager::class)
                ?.showSoftInputFromInputMethod(currentInputBinding.connectionToken, 0)
        }
    }


    /**
     * Switch to previous input method
     *
     * Note: This function can be replaced with a `switchToPreviousInputMethod()`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToPrevInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToPreviousInputMethod()
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToLastInputMethod(window.attributes.token) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the previous IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: This function can be replaced with a `switchToNextInputMethod(false)`
     * call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToNextInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class)
        try {
            if (AndroidVersion.ATLEAST_API28_P) {
                return switchToNextInputMethod(false)
            } else {
                window.window?.let { window ->
                    @Suppress("DEPRECATION")
                    return imm?.switchToNextInputMethod(window.attributes.token, false) == true
                }
            }
        } catch (e: Exception) {
            flogError { "Unable to switch to the next IME" }
            imm?.showInputMethodPicker()
        }
        return false
    }

    /**
     * Switch to next input method
     *
     * Note: The inner part of this function can be replaced with a
     *
     * `switchInputMethod(el.id, el.getSubtypeAt(i))` call once we've set the minApiLevel to 28 (Android 9)
     *
     * @return true if the switch was successful
     */
    fun switchToVoiceInputMethod(): Boolean {
        val imm = systemServiceOrNull(InputMethodManager::class) ?: return false
        val list: List<InputMethodInfo> = imm.enabledInputMethodList
        for (el in list) {
            for (i in 0 until el.subtypeCount) {
                // Check if the subtype is a voice input method.
                // We need to hardcode 'voice' here because the SUBTYPE_MODE_VOICE constant is private.
                // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/view/inputmethod/InputMethodManager.java;drc=2b278ab3ac73bb5596327aac1298df85cd94e454;l=309
                if (el.getSubtypeAt(i).mode != "voice") continue
                if (AndroidVersion.ATLEAST_API28_P) {
                    switchInputMethod(el.id, el.getSubtypeAt(i))
                    return true
                } else {
                    window.window?.let { window ->
                        @Suppress("DEPRECATION")
                        imm.setInputMethod(window.attributes.token, el.id)
                        return true
                    }
                }
            }
        }
        Toast.makeText(
            this,
            R.string.ime__voice_input_unavailable,
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }

    private val prefs by DekuPreferenceStore
    private val appContext = inferDekuApplication()
    val storageController = appContext.storageController
    val imeController = appContext.imeController
    val themeController = appContext.themeController

    private val nlpManager by nlpManager()
    private val subtypeManager by subtypeManager()

    val windowController = ImeWindowController(prefs, lifecycleScope)

    private val systemLocalesFlow = MutableStateFlow(LocaleList())
    var resourcesContext by mutableStateOf(this as Context)
        private set

    private val wallpaperChangeReceiver = WallpaperChangeReceiver()
    private var imeRootView: ImeRootView? = null

    init {
        setTheme(R.style.DekuImeTheme)
    }

    override fun onCreate() {
        super.onCreate()
        DekuImeServiceReference = WeakReference(this)
        systemLocalesFlow.value = resources.configuration.locales

        WindowCompat.setDecorFitsSystemWindows(window.window!!, false)
        windowController.onConfigurationChanged(resources.configuration)
        combine(
            systemLocalesFlow,
            subtypeManager.activeSubtypeFlow,
            prefs.localization.displayKeyboardLabelsInSubtypeLanguage.asFlow(),
        ) { systemLocales, subtype, shouldUseSubtypeLanguage ->
            systemLocales to (if (shouldUseSubtypeLanguage) subtype.primaryLocale else null)
        }.collectIn(lifecycleScope) { (systemLocales, subtypeLocale) ->
            val config = Configuration().apply {
                setToDefaults()
                if (subtypeLocale != null) {
                    setLocale(subtypeLocale.base)
                } else {
                    setLocales(systemLocales)
                }
            }
            resourcesContext = createConfigurationContext(config)
        }

        prefs.physicalKeyboard.showOnScreenKeyboard.asFlow().collectIn(lifecycleScope) {
            updateInputViewShown()
        }

        // InputMethodService does not automatically reevaluate fullscreen mode when this
        // preference changes while the IME service remains alive.
        prefs.keyboard.landscapeInputUiMode.asFlow().collectIn(lifecycleScope) {
            updateFullscreenMode()
        }

        @Suppress("DEPRECATION") // We do not retrieve the wallpaper but only listen to changes
        registerReceiver(wallpaperChangeReceiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
    }

    override fun onCreateInputView(): View? {
        super.installViewTreeOwners()
        val content = window.window!!.findViewById<ViewGroup>(android.R.id.content)

        // Android may recreate the input view while this service is still alive. Because the
        // custom root is attached directly to the window content (and the default input view is
        // disabled below), remove any prior root before attaching a new Compose tree.
        for (index in content.childCount - 1 downTo 0) {
            if (content.getChildAt(index) is ImeRootView) {
                content.removeViewAt(index)
            }
        }

        ImeRootView(this).also { root ->
            imeRootView = root
            content.addView(root)
        }
        // Disable the default input view placement
        return null
    }

    override fun onCreateCandidatesView(): View? {
        // Disable the default candidates view
        return null
    }

    override fun onCreateExtractTextView(): View {
        super.installViewTreeOwners()
        // Consider adding a fallback to the default extract edit layout if user reports come
        // that this causes a crash, especially if the device manufacturer of the user device
        // is a known one to break AOSP standards...
        val defaultExtractView = super.onCreateExtractTextView()
        if (defaultExtractView == null || defaultExtractView !is ViewGroup) {
            return ExtractedInputRootView(this, null)
        }
        val extractEditText = defaultExtractView.findViewById<ExtractEditText>(android.R.id.inputExtractEditText)
        (extractEditText?.parent as? ViewGroup)?.removeView(extractEditText)
        defaultExtractView.let {
            it.removeAllViews()
            it.addView(ExtractedInputRootView(this, extractEditText))
        }
        return defaultExtractView
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemLocalesFlow.value = newConfig.locales
        windowController.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        imeRootView?.let { root ->
            (root.parent as? ViewGroup)?.removeView(root)
        }
        imeRootView = null
        super.onDestroy()
        unregisterReceiver(wallpaperChangeReceiver)
        DekuImeServiceReference = WeakReference(null)
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "info=${info?.debugSummarize()} restarting=$restarting" }
        super.onStartInput(info, restarting)
        if (info == null) return
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        flogInfo { "info=${info?.debugSummarize()} restarting=$restarting" }
        super.onStartInputView(info, restarting)
        if (info == null) return
        currentInputConnection?.requestCursorUpdates(ImeEditor.CURSOR_UPDATES)
        val ic = WeakReference(currentInputConnection)
        val editorInfo = DekuEditorInfo.wrap(info)
        imeController.updateStateAsync {
            handleStartInputView(ic, editorInfo)
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        val config = resources.configuration
        return super.onEvaluateInputViewShown()
            || config.keyboard == Configuration.KEYBOARD_NOKEYS
            || prefs.physicalKeyboard.showOnScreenKeyboard.get()
    }

    override fun onUpdateCursorAnchorInfo(cursorAnchorInfo: CursorAnchorInfo?) {
        if (cursorAnchorInfo == null) {
            return
        }
        val newSelection = K3TextRange(
            start = cursorAnchorInfo.selectionStart,
            end = cursorAnchorInfo.selectionEnd,
        )
        flogInfo { "new=$newSelection" }
        imeController.updateStateAsync {
            handleUpdateSelection(newSelection)
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        flogInfo { "finishing=$finishingInput" }
        super.onFinishInputView(finishingInput)
        currentInputConnection?.requestCursorUpdates(0)
        imeController.updateStateAsync {
            handleFinishInputView()
        }
    }

    override fun onFinishInput() {
        flogInfo { "(no args)" }
        super.onFinishInput()
        NlpInlineAutofill.clearInlineSuggestions()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (windowController.onWindowShown()) {
            flogInfo(LogTopic.IMS_EVENTS)
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already shown)" }
        }
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (windowController.onWindowHidden()) {
            flogInfo(LogTopic.IMS_EVENTS)
            imeController.updateStateAsync {
                state = state.copy(
                    flags = state.flags
                        .withImeUiMode(ImeUiMode.TEXT)
                        .withActionsEditorVisible(false)
                        .withActionsOverflowVisible(false),
                )
            }
        } else {
            flogWarning(LogTopic.IMS_EVENTS) { "Ignoring (is already hidden)" }
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        val config = resources.configuration
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return false
        }
        return when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onEvaluateFullscreenMode()
            LandscapeInputUiMode.NEVER_SHOW -> false
            LandscapeInputUiMode.ALWAYS_SHOW -> true
        }
    }

    override fun onUpdateExtractingVisibility(info: EditorInfo?) {
        if (info != null) {
            val ic = WeakReference(currentInputConnection)
            val editorInfo = DekuEditorInfo.wrap(info)
            imeController.updateStateAsync {
                handleStartInputView(ic, editorInfo)
            }
        }
        when (prefs.keyboard.landscapeInputUiMode.get()) {
            LandscapeInputUiMode.DYNAMICALLY_SHOW -> super.onUpdateExtractingVisibility(info)
            LandscapeInputUiMode.NEVER_SHOW -> isExtractViewShown = false
            LandscapeInputUiMode.ALWAYS_SHOW -> isExtractViewShown = true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        if (!prefs.smartbar.enabled.get() || !prefs.suggestion.api30InlineSuggestionsEnabled.get()) {
            flogInfo(LogTopic.IMS_EVENTS) {
                "Ignoring inline suggestions request because Smartbar and/or inline suggestions are disabled."
            }
            return null
        }

        flogInfo(LogTopic.IMS_EVENTS) { "Creating inline suggestions request" }
        val stylesBundle = createInlineSuggestionUiStyleBundle(this)
        if (stylesBundle == null) {
            flogWarning(LogTopic.IMS_EVENTS) { "Failed to retrieve inline suggestions style bundle" }
            return null
        }
        val spec = InlinePresentationSpec.Builder(
            InlineSuggestionUiSmallestSize,
            InlineSuggestionUiBiggestSize,
        ).run {
            setStyle(stylesBundle)
            build()
        }

        return InlineSuggestionsRequest.Builder(listOf(spec)).run {
            setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        val inlineSuggestions = response.inlineSuggestions
        flogInfo(LogTopic.IMS_EVENTS) {
            "Received inline suggestions response with ${inlineSuggestions.size} suggestion(s) provided."
        }
        return NlpInlineAutofill.showInlineSuggestions(this, inlineSuggestions)
    }

    override fun onComputeInsets(outInsets: Insets?) {
        if (outInsets == null) return
        val imeState = imeController.snapshotState()
        windowController.onComputeInsets(outInsets, imeState.isFullscreenInputRequired())
    }

    override fun getTextForImeAction(imeOptions: Int): String? {
        return try {
            when (imeOptions and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_NONE -> null
                EditorInfo.IME_ACTION_GO -> resourcesContext.getString(AndroidInternalR.string.ime_action_go)
                EditorInfo.IME_ACTION_SEARCH -> resourcesContext.getString(AndroidInternalR.string.ime_action_search)
                EditorInfo.IME_ACTION_SEND -> resourcesContext.getString(AndroidInternalR.string.ime_action_send)
                EditorInfo.IME_ACTION_NEXT -> resourcesContext.getString(AndroidInternalR.string.ime_action_next)
                EditorInfo.IME_ACTION_DONE -> resourcesContext.getString(AndroidInternalR.string.ime_action_done)
                EditorInfo.IME_ACTION_PREVIOUS -> resourcesContext.getString(AndroidInternalR.string.ime_action_previous)
                else -> resourcesContext.getString(AndroidInternalR.string.ime_action_default)
            }
        } catch (exception: Resources.NotFoundException) {
            flogWarning { "Android system IME action label is unavailable: $exception" }
            super.getTextForImeAction(imeOptions)?.toString()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        flogDebug { "onKeyDown(keyCode=$keyCode, event=$event)" }
        return imeController.onHardwareKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        flogDebug { "onKeyUp(keyCode=$keyCode, event=$event)" }
        return imeController.onHardwareKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)
    }
}
