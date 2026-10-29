/*
 * Copyright (C) 2024-2025 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.nlp

import android.content.Context
import android.os.Build
import android.util.Size
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionInfo
import android.widget.inline.InlineContentView
import androidx.annotation.RequiresApi
import org.deku.foundation.dekukeyboard.lib.devtools.flogInfo
import org.deku.foundation.dekukeyboard.lib.devtools.flogWarning
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class NlpInlineAutofillSuggestion(
    val info: InlineSuggestionInfo,
    val view: InlineContentView?,
)

/**
 * Chooses a requested inline-suggestion height that is legal for the provider's presentation spec.
 * A non-positive or out-of-range height means the chip has not been measured yet or the measured
 * value is stale; Android's WRAP_CONTENT lets the platform size the remote view within its own bounds.
 */
internal fun resolveInlineSuggestionHeight(
    requestedHeight: Int,
    minHeight: Int,
    maxHeight: Int,
): Int = when {
    requestedHeight <= 0 -> ViewGroup.LayoutParams.WRAP_CONTENT
    minHeight < 0 || maxHeight < minHeight -> ViewGroup.LayoutParams.WRAP_CONTENT
    requestedHeight < minHeight || requestedHeight > maxHeight -> ViewGroup.LayoutParams.WRAP_CONTENT
    else -> requestedHeight
}

object NlpInlineAutofill {
    private val currentSequenceId = AtomicInteger(0)

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val setterGuard = Mutex()

    val suggestions: StateFlow<List<NlpInlineAutofillSuggestion>>
        field = MutableStateFlow(emptyList())

    var suggestionsChipHeightPx: Int = 0

    @RequiresApi(Build.VERSION_CODES.R)
    fun showInlineSuggestions(context: Context, rawSuggestions: List<InlineSuggestion>): Boolean {
        val sequenceId = generateSequenceId()

        if (rawSuggestions.isEmpty()) {
            clearInlineSuggestions(sequenceId)
            return false
        }

        // Capture this UI-owned value before switching to the background scope.
        val requestedChipHeightPx = suggestionsChipHeightPx
        scope.launch {
            val latch = CountDownLatch(rawSuggestions.size)
            val suggestionsArray = Array<NlpInlineAutofillSuggestion?>(rawSuggestions.size) { null }

            flogInfo { "showInlineSuggestions: [${sequenceId}] start inflating suggestions" }
            for ((index, rawSuggestion) in rawSuggestions.withIndex()) {
                val presentationSpec = rawSuggestion.info.inlinePresentationSpec
                val height = resolveInlineSuggestionHeight(
                    requestedHeight = requestedChipHeightPx,
                    minHeight = presentationSpec.minSize.height,
                    maxHeight = presentationSpec.maxSize.height,
                )
                val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, height)

                try {
                    rawSuggestion.inflate(context, size, context.mainExecutor) { view ->
                        suggestionsArray[index] = NlpInlineAutofillSuggestion(rawSuggestion.info, view)
                        latch.countDown()
                    }
                } catch (e: IllegalArgumentException) {
                    flogWarning {
                        "showInlineSuggestions: [${sequenceId}] invalid inline-suggestion size/payload; skipping: ${e.message}"
                    }
                    latch.countDown()
                } catch (e: IllegalStateException) {
                    flogWarning {
                        "showInlineSuggestions: [${sequenceId}] inline suggestion could not be inflated; skipping: ${e.message}"
                    }
                    latch.countDown()
                }
            }

            if (!latch.await(2_000, TimeUnit.MILLISECONDS)) {
                flogWarning { "showInlineSuggestions: [${sequenceId}] timed out while waiting for all " +
                    "suggestions to inflate" }
                return@launch
            }

            val inflatedSuggestions = suggestionsArray.filterNotNull().sortedByDescending { it.info.isPinned }
            setterGuard.lock()
            flogInfo { "showInlineSuggestions: [${sequenceId}] successfully inflated " +
                "${inflatedSuggestions.count { it.view != null }} out of ${inflatedSuggestions.size} suggestions" }
            if (currentSequenceId.get() == sequenceId) {
                flogInfo { "showInlineSuggestions: [${sequenceId}] setting suggestions" }
                suggestions.value = inflatedSuggestions
            } else {
                flogWarning { "showInlineSuggestions: [${sequenceId}] seqId != current, skip setting suggestions" }
            }
            setterGuard.unlock()
        }

        return true
    }

    fun clearInlineSuggestions() {
        // Increment sequence id to invalidate eventual pending suggestions
        clearInlineSuggestions(generateSequenceId())
    }

    private fun clearInlineSuggestions(sequenceId: Int) {
        scope.launch {
            setterGuard.lock()
            flogInfo { "clearInlineSuggestions: [${sequenceId}] clearing suggestions" }
            suggestions.value = emptyList()
            setterGuard.unlock()
        }
    }

    private fun generateSequenceId(): Int {
        return currentSequenceId.incrementAndGet()
    }
}
