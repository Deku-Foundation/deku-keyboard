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

package org.deku.foundation.dekukeyboard.ime.keyboard3.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFold
import androidx.compose.ui.util.fastForEach
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.ime.keyboard3.ImeController
import org.deku.foundation.dekukeyboard.ime.keyboard3.LocalImeController
import org.deku.foundation.dekukeyboard.ime.keyboard3.touch.InputShiftState
import org.deku.foundation.dekukeyboard.ime.keyboard3.touch.TouchKey
import org.deku.foundation.dekukeyboard.ime.keyboard3.touch.TouchKeyboard
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.k3lp.model.K3Model
import org.k3lp.model.layer.K3LayerId
import kotlin.math.pow
import kotlin.time.Duration

sealed interface TrackedPointer {
    val id: PointerId
    val down: PointerInputChange
    val downKey: TouchKey

    data class Peek(
        override val id: PointerId,
        override val down: PointerInputChange,
        override val downKey: TouchKey,
        val downLayerId: K3LayerId,
        val downInputShiftState: InputShiftState,
        val peekLayerId: K3LayerId,
        val peekKey: TouchKey?,
        val peekLine: PeekLine?,
        val peekMustSwitchBack: Boolean,
    ) : TrackedPointer

    data class Output(
        override val id: PointerId,
        override val down: PointerInputChange,
        override val downKey: TouchKey,
        val longPress: LongPress,
        val repeatJob: Job?,
    ) : TrackedPointer
}

data class TrackedMultiTapSeq(
    val touchKey: TouchKey,
    val multiTapIndex: Int,
    val commitJob: Job?,
    val commit: suspend () -> Unit,
    val cycleNext: suspend () -> Unit,
)

data class PeekLine(
    val start: Offset,
    val end: Offset,
)

data class PopupGeometry(
    val viewportBounds: Rect,
    val layoutDirection: LayoutDirection,
)

internal fun calculateLongPressAnchorBounds(
    keyBounds: Rect,
    viewportBounds: Rect = Rect(0f, 0f, 1f, 1f),
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
): Rect {
    if (
        keyBounds.isEmpty || keyBounds.width <= 0f || keyBounds.height <= 0f ||
        viewportBounds.isEmpty || viewportBounds.width <= 0f || viewportBounds.height <= 0f
    ) {
        return Rect.Zero
    }

    // Geometry stays normalized to the keyboard width/height. The viewport may extend above
    // y=0 by exactly the area available in the containing IME root window.
    val width = keyBounds.width.coerceAtMost(viewportBounds.width)
        .coerceAtLeast(MIN_LONG_PRESS_ANCHOR_DIMENSION)
    val preferredHeight = (keyBounds.height * 0.9f).coerceAtMost(viewportBounds.height)
    val availableAbove = (keyBounds.top - viewportBounds.top).coerceAtLeast(0f)
    val height = if (availableAbove >= MIN_LONG_PRESS_ANCHOR_DIMENSION * 1.2f) {
        preferredHeight.coerceAtMost(availableAbove / 1.2f)
            .coerceAtLeast(MIN_LONG_PRESS_ANCHOR_DIMENSION)
    } else {
        preferredHeight
    }
    return calculatePopupBounds(
        keyBounds = keyBounds,
        containerBounds = viewportBounds,
        popupSize = Size(width, height),
        layoutDirection = layoutDirection,
    )
}

private const val MIN_LONG_PRESS_ANCHOR_DIMENSION = 0.01f

private data class LastUpKey(
    val touchKey: TouchKey,
    val uptimeMillis: Long,
)

// All pointer-event mutations and the delayed long-press expansion are serialized by
// mutationGuard. Keep launches UNDISPATCHED in trackPointerInput so events acquire the mutex
// in dispatch order and the focused popup key cannot lag behind the user's release.
class PointerTracker internal constructor(
    val touchKeyboard: TouchKeyboard,
    val imeController: ImeController,
    val interactionController: InteractionController,
    val showSimplePopups: Boolean,
    val peekDistanceSqMin: Float,
    private val popupGeometry: State<PopupGeometry>,
) {
    private val mutationGuard = Mutex()

    val trackedPeekPointer: StateFlow<TrackedPointer.Peek?>
        field = MutableStateFlow(null)

    val trackedOutputPointer: StateFlow<TrackedPointer.Output?>
        field = MutableStateFlow(null)

    val trackedMultiTapSeq: StateFlow<TrackedMultiTapSeq?>
        field = MutableStateFlow(null)

    private var lastUpKeyState: LastUpKey? = null

    suspend fun mutateLocked(action: suspend PointerTracker.() -> Unit) {
        mutationGuard.withLock { action() }
    }

    context(scope: CoroutineScope)
    suspend fun handleDown(down: PointerInputChange, size: IntSize) {
        val lastUpKey = lastUpKeyState

        trackedPeekPointer.value?.let { trackedPointer ->
            if (trackedPointer.peekKey != null) {
                handleUp(down.copy(id = trackedPointer.id), size)
            }
        }
        trackedOutputPointer.value?.let { trackedPointer ->
            handleUp(down.copy(id = trackedPointer.id), size)
        }

        val downLayerId = imeController.snapshotState().touchLayerId
        val downKey = touchKeyboard.findKey(downLayerId, down.position.normalized(size)) ?: return

        val timingOptions = interactionController.activeTimingOptions.value
        val keyRepeatTimeout = timingOptions.getKeyRepeatTimeout(downKey.attrs.output)
        val keyRepeatDelay = timingOptions.getKeyRepeatDelay(downKey.attrs.output)
        val longPressTimeout = timingOptions.getLongPressTimeout(downKey.attrs.output)
        val doubleTapTimeout = timingOptions.doubleTapTimeout

        val peekLayerId = downKey.attrs.layerId
        if (peekLayerId != null) {
            // this is a peek key action
            trackedMultiTapSeq.value?.let { multiTapSeq ->
                multiTapSeq.commit()
            }
            val downInputShiftState: InputShiftState
            imeController.updateState {
                downInputShiftState = state.flags.inputShiftState
                if (downKey.isShiftKey) {
                    val isDoubleTap = lastUpKey != null && lastUpKey.touchKey.isShiftKey &&
                        (down.uptimeMillis - lastUpKey.uptimeMillis) < doubleTapTimeout.inWholeMilliseconds
                    cycleInputShiftState(isDoubleTap)
                } else {
                    switchTouchLayer(peekLayerId)
                }
            }
            trackedPeekPointer.value = TrackedPointer.Peek(
                id = down.id,
                down = down,
                downKey = downKey,
                downLayerId = downLayerId,
                downInputShiftState = downInputShiftState,
                peekLayerId = peekLayerId,
                peekKey = null,
                peekLine = null,
                peekMustSwitchBack = false,
            )
            interactionController.performFeedback(InteractionKind.KeyPress)
        } else {
            trackedOutputPointer.value = TrackedPointer.Output(
                id = down.id,
                down = down,
                downKey = downKey,
                longPress = if (downKey.isSuitableForPopup) {
                    val geometry = popupGeometry.value
                    val anchorBounds = calculateLongPressAnchorBounds(
                        keyBounds = downKey.bounds,
                        viewportBounds = geometry.viewportBounds,
                        layoutDirection = geometry.layoutDirection,
                    )
                    LongPress(
                        anchorBounds = anchorBounds,
                        simpleBounds = if (showSimplePopups && downKey.isSuitableForSimplePopup) {
                            // The preview surface is exactly the popup rect. Extending its background
                            // down through the key made the preview look vertically stretched/mirrored.
                            anchorBounds
                        } else Rect.Zero,
                        simpleLabel = downKey.display,
                        simpleIndicateExtended = downKey.isSuitableForExtendedPopup,
                        extendedBounds = Rect.Zero,
                        extendedKeys = downKey.extendedPopupKeys,
                        extendedFocusedIndex = 0,
                        extendedJob = if (downKey.isSuitableForExtendedPopup) {
                            launchExtendedLongPressJob(longPressTimeout)
                        } else null,
                        popupViewportBounds = geometry.viewportBounds,
                        layoutDirection = geometry.layoutDirection,
                        pointerDownPosition = down.position.normalized(size),
                    )
                } else LongPress.None,
                repeatJob = if (downKey.isRepeatable) {
                    scope.launch {
                        delay(keyRepeatTimeout)
                        while (isActive) {
                            imeController.updateState {
                                downKey.attrs.output?.let { emit(it) }
                            }
                            interactionController.performFeedback(InteractionKind.KeyRepeat)
                            delay(keyRepeatDelay)
                        }
                    }
                } else null,
            )
            trackedMultiTapSeq.value.let { multiTapSeq ->
                if (multiTapSeq != null) {
                    if (multiTapSeq.touchKey !== downKey) {
                        multiTapSeq.commit()
                    } else if (downKey.multiTapKeys.isNotEmpty()) {
                        multiTapSeq.cycleNext()
                    }
                } else if (downKey.multiTapKeys.isNotEmpty()) {
                    val commit = suspend commit@{
                        val multiTapSeq = trackedMultiTapSeq.value ?: return@commit
                        val multiTapKey = multiTapSeq.touchKey.multiTapKeys.getOrNull(multiTapSeq.multiTapIndex)
                            ?: return@commit
                        val output = multiTapKey.data.output ?: return@commit
                        imeController.updateState {
                            emit(output)
                        }
                        trackedMultiTapSeq.value = null
                    }
                    val cycleNext = suspend cycleNext@{
                        val multiTapSeq = trackedMultiTapSeq.value ?: return@cycleNext
                        multiTapSeq.commitJob?.cancel()
                        val newIndex = (multiTapSeq.multiTapIndex + 1) % multiTapSeq.touchKey.multiTapKeys.size.fastCoerceAtLeast(1)
                        trackedMultiTapSeq.value = multiTapSeq.copy(
                            multiTapIndex = newIndex,
                            commitJob = null,
                        )
                    }
                    trackedMultiTapSeq.value = TrackedMultiTapSeq(
                        touchKey = downKey,
                        multiTapIndex = 0,
                        commitJob = null,
                        commit = commit,
                        cycleNext = cycleNext,
                    )
                }
            }
            interactionController.performFeedback(InteractionKind.KeyPress)
        }
    }

    suspend fun handleMove(move: PointerInputChange, size: IntSize) {
        trackedPeekPointer.value?.takeIf { it.id == move.id }?.let { trackedPointer ->
            val distanceSq = (move.position - trackedPointer.down.position).getDistanceSquared()
            if (trackedPointer.peekLine != null || distanceSq >= peekDistanceSqMin) {
                val newPeekKey = touchKeyboard.findKey(trackedPointer.peekLayerId, move.position.normalized(size))
                trackedPeekPointer.value = trackedPointer.copy(
                    peekKey = newPeekKey,
                    peekLine = PeekLine(trackedPointer.down.position, move.position),
                    peekMustSwitchBack = true,
                )
            }
        }
        trackedOutputPointer.value?.takeIf { it.id == move.id }?.let { trackedPointer ->
            val longPress = trackedPointer.longPress
            if (longPress.shouldShowExtendedPopup()) {
                val position = resolveLongPressPointerPosition(
                    longPress = longPress,
                    normalizedPosition = move.position.normalized(size),
                )
                if (position == null) {
                    cancelOutput()
                } else {
                    val newFocusedIndex = longPress.getNearestKeyIndex(position)
                    if (newFocusedIndex != longPress.extendedFocusedIndex) {
                        trackedOutputPointer.value = trackedPointer.copy(
                            longPress = longPress.copy(extendedFocusedIndex = newFocusedIndex),
                        )
                    }
                }
            }
        }
    }

    context(scope: CoroutineScope)
    suspend fun handleUp(up: PointerInputChange, size: IntSize) {
        trackedPeekPointer.value?.takeIf { it.id == up.id }?.let { trackedPointer ->
            imeController.updateState {
                trackedPointer.peekKey?.attrs?.output?.let { emit(it) }
                if (trackedPointer.peekMustSwitchBack) {
                    if (trackedPointer.downKey.isShiftKey) {
                        revertCycleInputShiftState(
                            trackedPointer.downLayerId, trackedPointer.downInputShiftState
                        )
                    } else {
                        switchTouchLayer(trackedPointer.downLayerId)
                    }
                }
            }
            trackedPeekPointer.value = null
            lastUpKeyState = LastUpKey(trackedPointer.downKey, up.uptimeMillis)
            interactionController.performFeedback(
                InteractionKind.KeyRelease,
                trackedPointer.downKey.attrs.output,
            )
        }
        trackedOutputPointer.value?.takeIf { it.id == up.id }?.let { trackedPointer ->
            trackedPointer.longPress.extendedJob?.cancel()
            trackedPointer.repeatJob?.cancel()
            imeController.updateState {
                val output = when {
                    trackedPointer.downKey == trackedMultiTapSeq.value?.touchKey -> null
                    trackedPointer.longPress.shouldShowExtendedPopup() -> {
                        val longPress = trackedPointer.longPress
                        // Pointer-up can carry a newer coordinate than the last coalesced move event.
                        // Resolve the release point before committing so the visible selection wins.
                        val releasePosition = resolveLongPressPointerPosition(
                            longPress = longPress,
                            normalizedPosition = up.position.normalized(size),
                        )
                        val releaseIndex = releasePosition?.let(longPress::getNearestKeyIndex)
                        releaseIndex?.let { longPress.extendedKeys.getOrNull(it)?.data?.output }
                    }
                    else -> trackedPointer.downKey.attrs.output
                }
                output?.let { emit(it) }
            }
            trackedPeekPointer.update { it?.copy(peekMustSwitchBack = true) }
            trackedOutputPointer.value = null
            lastUpKeyState = LastUpKey(trackedPointer.downKey, up.uptimeMillis)
            interactionController.performFeedback(
                InteractionKind.KeyRelease,
                trackedPointer.downKey.attrs.output,
            )
        }
        trackedMultiTapSeq.update { multiTapSeq ->
            if (multiTapSeq != null) {
                multiTapSeq.commitJob?.cancel() // should never happen, but still ensure we never have 2 active jobs
                multiTapSeq.copy(
                    commitJob = scope.launch {
                        val timingOptions = interactionController.activeTimingOptions.value
                        delay(timingOptions.multiTapTimeout)
                        mutateLocked {
                            if (trackedOutputPointer.value?.longPress?.extendedBounds?.isEmpty == false) {
                                return@mutateLocked
                            }
                            multiTapSeq.commit()
                        }
                    }
                )
            } else null
        }
    }

    suspend fun cancelNonPresent(changes: List<PointerInputChange>) {
        trackedPeekPointer.value?.id?.let { id ->
            if (changes.fastAll { it.id != id }) {
                cancelPeek()
            }
        }
        trackedOutputPointer.value?.id?.let { id ->
            if (changes.fastAll { it.id != id }) {
                cancelOutput()
            }
        }
    }

    suspend fun cancelAll() {
        cancelPeek()
        cancelOutput()
    }

    private suspend fun cancelPeek() {
        val trackedPointer = trackedPeekPointer.getAndUpdate { null } ?: return
        imeController.updateState {
            if (trackedPointer.downKey.isShiftKey) {
                revertCycleInputShiftState(
                    trackedPointer.downLayerId, trackedPointer.downInputShiftState
                )
            } else {
                switchTouchLayer(trackedPointer.downLayerId)
            }
        }
    }

    private suspend fun cancelOutput() {
        cancelMultiTap()
        val trackedPointer = trackedOutputPointer.getAndUpdate { null } ?: return
        trackedPointer.longPress.extendedJob?.cancel()
        trackedPointer.repeatJob?.cancel()
    }

    private suspend fun cancelMultiTap() {
        val multiTapSeq = trackedMultiTapSeq.getAndUpdate { null } ?: return
        multiTapSeq.commitJob?.cancel()
    }

    private fun Offset.normalized(size: IntSize): Offset {
        return Offset(x / size.width, y / size.height)
    }

    context(scope: CoroutineScope)
    private fun launchExtendedLongPressJob(
        longPressTimeout: Duration,
    ): Job = scope.launch(Dispatchers.Default) {
        delay(longPressTimeout)
        var showedPopup = false
        var pressedOutput: org.k3lp.lib.text.K3StringOrDescriptor? = null
        mutateLocked {
            trackedMultiTapSeq.value?.let { multiTapSeq ->
                multiTapSeq.commitJob?.cancel()
                trackedMultiTapSeq.value = null
            }
            val trackedPointer = trackedOutputPointer.value ?: return@mutateLocked
            val longPress = trackedPointer.longPress
            val anchorBounds = longPress.anchorBounds
            val extendedKeys = layoutExtendedLongPressKeys(
                extendedKeys = longPress.extendedKeys,
                anchorBounds = anchorBounds,
                viewportBounds = longPress.popupViewportBounds,
                layoutDirection = longPress.layoutDirection,
            )
            val extendedBounds = anchorBounds.copy(
                left = extendedKeys.fastFold(anchorBounds.left) { acc, key -> minOf(acc, key.bounds.left) },
                top = extendedKeys.fastFold(anchorBounds.top) { acc, key -> minOf(acc, key.bounds.top) },
                right = extendedKeys.fastFold(anchorBounds.right) { acc, key -> maxOf(acc, key.bounds.right) },
                bottom = extendedKeys.fastFold(anchorBounds.bottom) { acc, key -> maxOf(acc, key.bounds.bottom) },
            )
            trackedOutputPointer.value = trackedPointer.copy(
                longPress = trackedPointer.longPress.copy(
                    simpleIndicateExtended = false,
                    extendedBounds = extendedBounds,
                    extendedKeys = extendedKeys,
                ),
            )
            pressedOutput = trackedPointer.downKey.attrs.output
            showedPopup = true
        }
        if (showedPopup) {
            // View/audio feedback must execute on the main thread, outside mutationGuard so pointer
            // release cannot deadlock waiting for the long-press job to finish.
            withContext(Dispatchers.Main.immediate) {
                interactionController.performFeedback(InteractionKind.LongPress, pressedOutput)
            }
        }
    }
}

@Composable
internal fun rememberPointerTracker(
    touchKeyboard: TouchKeyboard,
    popupGeometry: State<PopupGeometry>,
): PointerTracker {
    val prefs by DekuPreferenceStore
    val density = LocalDensity.current
    val imeController = LocalImeController.current
    val interactionController = LocalInteractionController.current

    val showSimplePopups by prefs.keyboard.popupEnabled.collectAsState()
    val flickKeyDistanceThresholdDp by prefs.gestures.flickKeyDistanceThresholdDp.collectAsState()
    val peekDistanceSqMin = with(density) { flickKeyDistanceThresholdDp.dp.toPx().pow(2) }

    val pointerTracker = remember(touchKeyboard, showSimplePopups, peekDistanceSqMin) {
        PointerTracker(touchKeyboard, imeController, interactionController, showSimplePopups, peekDistanceSqMin, popupGeometry)
    }

    return pointerTracker
}

fun Modifier.trackPointerInput(
    pointerTracker: PointerTracker,
    model: K3Model,
) = this.pointerInput(pointerTracker, model) {
    try {
        coroutineScope {
            val scope = this
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val sizeAtEvent = size

                    // Register state-mutating handlers in dispatch order. With default launch start,
                    // a later PointerUp may be scheduled before a preceding PointerMove acquires the
                    // mutex, causing the selected long-press popup key to differ from the visible
                    // focused key at release. UNDISPATCHED runs each handler through its first
                    // suspension immediately, preserving FIFO Mutex acquisition order while later
                    // suspensions still resume on the scope's original dispatcher.
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        pointerTracker.mutateLocked {
                            cancelNonPresent(event.changes)
                        }
                    }
                    event.changes.fastForEach { change ->
                        if (change.changedToDown()) {
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                pointerTracker.mutateLocked {
                                    handleDown(change, sizeAtEvent)
                                }
                            }
                        } else if (change.changedToUp()) {
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                pointerTracker.mutateLocked {
                                    handleUp(change, sizeAtEvent)
                                }
                            }
                        } else if (!change.isConsumed) {
                            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                pointerTracker.mutateLocked {
                                    handleMove(change, sizeAtEvent)
                                }
                            }
                        }
                        change.consume()
                    }
                }
            }
        }
    } finally {
        withContext(NonCancellable) {
            pointerTracker.mutateLocked {
                cancelAll()
            }
        }
    }
}
