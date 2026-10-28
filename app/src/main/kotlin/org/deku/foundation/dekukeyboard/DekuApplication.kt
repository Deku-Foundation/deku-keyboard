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

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Handler
import android.util.Log
import androidx.core.os.UserManagerCompat
import org.deku.foundation.dekukeyboard.app.DekuPreferenceModel
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.clipboard.ClipboardManager
import org.deku.foundation.dekukeyboard.ime.core.SubtypeManager
import org.deku.foundation.dekukeyboard.ime.dictionary.DictionaryManager
import org.deku.foundation.dekukeyboard.ime.extension.ExtensionController
import org.deku.foundation.dekukeyboard.ime.io.AndroidStorageController
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeController
import org.deku.foundation.dekukeyboard.ime.media.emoji.DekuEmojiCompat
import org.deku.foundation.dekukeyboard.ime.nlp.NlpManager
import org.deku.foundation.dekukeyboard.ime.text.gestures.GlideTypingManager
import org.deku.foundation.dekukeyboard.ime.theme.SystemThemeMode
import org.deku.foundation.dekukeyboard.ime.theme.ThemeController
import org.deku.foundation.dekukeyboard.lib.crashutility.CrashUtility
import org.deku.foundation.dekukeyboard.lib.devtools.Flog
import org.deku.foundation.dekukeyboard.lib.devtools.LogTopic
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.deku.foundation.dekukeyboard.lib.kotlin.tryOrNull
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [DekuApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var dekuApplicationReference = WeakReference<DekuApplication?>(null)

@Suppress("unused")
class DekuApplication : Application() {
    companion object {
        init {
            try {
                System.loadLibrary("deku_native")
            } catch (_: Exception) {
            }
        }

    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    lateinit var storageController: AndroidStorageController
    lateinit var extensionController: ExtensionController
    lateinit var imeController: ImeController
    lateinit var themeController: ThemeController

    val clipboardManager = lazy { ClipboardManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }

    override fun onCreate() {
        super.onCreate()
        dekuApplicationReference = WeakReference(this)

        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)

            storageController = AndroidStorageController(this)
            extensionController = ExtensionController(this, storageController)
            imeController = ImeController(
                storageController = storageController,
                clipboardManager = clipboardManager.value,
                application = this,
            )
            themeController = ThemeController(
                storageController,
                extensionController,
                initialSystemThemeMode = resources.configuration.determineSystemThemeMode(),
            )

            DekuEmojiCompat.init(this)

            if (!UserManagerCompat.isUserUnlocked(this)) {
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        themeController.activeSystemThemeMode.value = newConfig.determineSystemThemeMode()
    }

    fun init() {
        // cacheDir is disposable and managed by Android. Avoid a recursive synchronous purge
        // during Application initialization; components that own temporary caches should manage
        // those entries asynchronously and within their own cache subdirectories.
        scope.launch {
            val result = DekuPreferenceStore.initAndroid(
                context = this@DekuApplication,
                datastoreName = DekuPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
        }
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private fun Configuration.determineSystemThemeMode(): SystemThemeMode {
        return when (uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> SystemThemeMode.NIGHT
            Configuration.UI_MODE_NIGHT_NO -> SystemThemeMode.DAY
            else -> SystemThemeMode.UNKNOWN
        }
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                storageController.notifyUserUnlocked()
                mainHandler.post {
                    init()
                }
            }
        }
    }
}

private fun requireDekuApplication(): DekuApplication =
    checkNotNull(dekuApplicationReference.get()) {
        "DekuApplication must be initialized before resolving application-scoped services."
    }

tailrec fun Context.inferDekuApplication(): DekuApplication {
    return when (this) {
        is DekuApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.inferDekuApplication()
            else -> requireDekuApplication()
        }
        else -> tryOrNull { this.applicationContext as DekuApplication } ?: requireDekuApplication()
    }
}

fun Context.appContext() = lazyOf(this.inferDekuApplication())

fun Context.clipboardManager() = this.inferDekuApplication().clipboardManager

fun Context.glideTypingManager() = this.inferDekuApplication().glideTypingManager

fun Context.nlpManager() = this.inferDekuApplication().nlpManager

fun Context.subtypeManager() = this.inferDekuApplication().subtypeManager
