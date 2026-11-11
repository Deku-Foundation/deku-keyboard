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

package org.deku.foundation.dekukeyboard.lib.compose

import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.deku.foundation.dekukeyboard.app.DekuPreferenceModel
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.app.LocalNavController
import dev.patrickgold.jetpref.datastore.ui.PreferenceLayout
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiContent
import org.deku.foundation.dekukeyboard.lib.android.AndroidVersion
import org.deku.foundation.dekukeyboard.lib.compose.DekuAppBar
import org.deku.foundation.dekukeyboard.lib.compose.DekuIconButton
import org.deku.foundation.dekukeyboard.lib.compose.autoMirrorForRtl
import org.deku.foundation.dekukeyboard.lib.compose.dekuVerticalScroll

@Composable
fun DekuScreen(builder: @Composable DekuScreenScope.() -> Unit) {
    val scope = remember { DekuScreenScopeImpl() }
    builder(scope)
    scope.Render()
}

typealias DekuScreenActions = @Composable RowScope.() -> Unit
typealias DekuScreenBottomBar = @Composable () -> Unit
typealias DekuScreenContent = PreferenceUiContent<DekuPreferenceModel>
typealias DekuScreenFab = @Composable () -> Unit
typealias DekuScreenNavigationIcon = @Composable () -> Unit

interface DekuScreenScope {
    var title: String

    var navigationIconVisible: Boolean

    var previewFieldVisible: Boolean

    var scrollable: Boolean

    var iconSpaceReserved: Boolean

    fun actions(actions: DekuScreenActions)

    fun bottomBar(bottomBar: DekuScreenBottomBar)

    fun content(content: DekuScreenContent)

    fun floatingActionButton(fab: DekuScreenFab)

    fun navigationIcon(navigationIcon: DekuScreenNavigationIcon)
}

private class DekuScreenScopeImpl : DekuScreenScope {
    override var title: String by mutableStateOf("")
    override var navigationIconVisible: Boolean by mutableStateOf(true)
    override var previewFieldVisible: Boolean by mutableStateOf(false)
    override var scrollable: Boolean by mutableStateOf(true)
    override var iconSpaceReserved: Boolean by mutableStateOf(true)

    private var actions: DekuScreenActions = @Composable { }
    private var bottomBar: DekuScreenBottomBar = @Composable { }
    private var content: DekuScreenContent = @Composable { }
    private var fab: DekuScreenFab = @Composable { }
    private var navigationIcon: DekuScreenNavigationIcon = @Composable {
        val navController = LocalNavController.current
        DekuIconButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.autoMirrorForRtl(),
            icon = Icons.AutoMirrored.Filled.ArrowBack,
        )
    }

    override fun actions(actions: DekuScreenActions) {
        this.actions = actions
    }

    override fun bottomBar(bottomBar: DekuScreenBottomBar) {
        this.bottomBar = bottomBar
    }

    override fun content(content: DekuScreenContent) {
        this.content = content
    }

    override fun floatingActionButton(fab: DekuScreenFab) {
        this.fab = fab
    }

    override fun navigationIcon(navigationIcon: DekuScreenNavigationIcon) {
        this.navigationIcon = navigationIcon
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun Render() {
        val context = LocalContext.current
        val previewFieldController = LocalPreviewFieldController.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestPreviewFieldVisible = rememberUpdatedState(previewFieldVisible)
        val colorScheme = MaterialTheme.colorScheme

        DisposableEffect(lifecycleOwner, previewFieldController) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME ->
                        previewFieldController?.isVisible = latestPreviewFieldVisible.value
                    Lifecycle.Event.ON_PAUSE,
                    Lifecycle.Event.ON_STOP ->
                        previewFieldController?.isVisible = false
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                previewFieldController?.isVisible = latestPreviewFieldVisible.value
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                // A leaving NavHost entry can be disposed after the entering entry resumed.
                // Only this still-resumed entry may hide the shared preview field.
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    previewFieldController?.isVisible = false
                }
            }
        }

        SideEffect {
            val window = (context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            if (AndroidVersion.ATLEAST_API29_Q) {
                window.navigationBarColor = Color.Transparent.toArgb()
                window.isNavigationBarContrastEnforced = true
            } else {
                window.navigationBarColor = colorScheme.scrim.toArgb()
            }
        }

        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { DekuAppBar(title, navigationIcon.takeIf { navigationIconVisible }, actions, scrollBehavior) },
            bottomBar = bottomBar,
            floatingActionButton = fab,
        ) { innerPadding ->
            val scrollModifier = if (scrollable) {
                Modifier.dekuVerticalScroll()
            } else {
                Modifier
            }
            PreferenceLayout(
                DekuPreferenceStore,
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxWidth()
                    .then(scrollModifier),
                iconSpaceReserved = iconSpaceReserved,
                content = content,
            )
        }
    }
}
