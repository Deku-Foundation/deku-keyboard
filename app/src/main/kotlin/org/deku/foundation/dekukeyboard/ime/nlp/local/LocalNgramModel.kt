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

package org.deku.foundation.dekukeyboard.ime.nlp.local

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * A next-word prediction produced by the on-device learner.
 *
 * [confidence] is a relative score in the range 0.0..1.0, not a calibrated probability.
 */
data class LocalWordPrediction(
    val word: String,
    val confidence: Double,
)

private const val MAX_CONTEXT_WORDS = 2

/**
 * Language-agnostic tokenization helpers for the local model.
 *
 * The model deliberately works on whitespace-delimited words for every script, including
 * Arabic, Cyrillic, Greek and Hebrew. It never sends text anywhere and never treats a partial
 * trailing word as a completed word, so typing "hel" does not teach the model a word called
 * "hel".
 */
object LocalPredictionText {
    private val WORD_PATTERN = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['\u2019][\\p{L}\\p{M}\\p{N}]+)*")

    fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun words(text: CharSequence): List<String> =
        WORD_PATTERN.findAll(text)
            .map { normalize(it.value) }
            .filter { it.isNotEmpty() }
            .toList()

    /**
     * Returns the words that are definitely complete before the cursor. A trailing partial word
     * is dropped, while text ending in a separator/punctuation keeps its final word.
     */
    fun completedWords(textBefore: CharSequence): List<String> {
        val words = words(textBefore)
        if (words.isEmpty()) return emptyList()
        val last = textBefore.lastOrNull()
        val endsInsideWord = last != null && (Character.isLetterOrDigit(last) || last == '\'' || last == '\u2019')
        return if (endsInsideWord) words.dropLast(1) else words
    }

    /** Last one or two completed words used as the prediction context. */
    fun contextWords(textBefore: CharSequence): List<String> =
        completedWords(textBefore).takeLast(MAX_CONTEXT_WORDS)
}

@Serializable
data class LocalNgramSnapshot(
    val transitions: Map<String, Map<String, Int>> = emptyMap(),
    val unigrams: Map<String, Int> = emptyMap(),
)

/**
 * A compact, fully offline bigram/trigram learner for next-word suggestions.
 *
 * The model stores only word-pair counts, never the surrounding sentence. Counts are capped so
 * memory use stays bounded for long-running IME processes. A transition needs at least two
 * observations before it is surfaced, which keeps one-off typos and unusual names from producing
 * noisy suggestions.
 *
 * This is intentionally a small statistical model, not an LLM: it is fast enough to run on every
 * keystroke, works for any whitespace-delimited writing system, and learns per-language on-device.
 */
class LocalNgramModel(
    private val maxVocabulary: Int = 1_500,
    private val maxContexts: Int = 6_000,
) {
    private val unigrams = HashMap<String, Int>()
    private val transitions = HashMap<String, HashMap<String, Int>>()
    private var lastCompletedWords = emptyList<String>()

    /**
     * Feeds the completed words of the current surrounding text. Only newly appended words are
     * learned; deletions, cursor moves and truncated context reset the observation instead of
     * inventing transitions.
     */
    @Synchronized
    fun observeCompletedWords(words: List<String>) {
        val normalized = words.map(LocalPredictionText::normalize).filter { it.isNotBlank() }
        val previous = lastCompletedWords
        val isAppend = normalized.size > previous.size &&
            normalized.subList(0, previous.size) == previous
        if (isAppend) {
            for (index in maxOf(0, previous.size - 1) until normalized.size - 1) {
                learnPair(normalized[index], normalized[index + 1])
            }
        }
        lastCompletedWords = normalized.takeLast(MAX_OBSERVED_WORDS)
    }

    private fun learnPair(previous: String, next: String) {
        unigrams[next] = (unigrams[next] ?: 0) + 1
        val nextWords = transitions.getOrPut(previous) { HashMap() }
        nextWords[next] = (nextWords[next] ?: 0) + 1
        if (unigrams.size > maxVocabulary || transitions.size > maxContexts) {
            prune()
        }
    }

    private fun prune() {
        // Halve every count first so frequently observed pairs survive several pruning rounds.
        unigrams.replaceAll { _, count -> count / 2 }
        unigrams.entries.removeIf { it.value <= 0 }
        transitions.values.forEach { nextWords ->
            nextWords.replaceAll { _, count -> count / 2 }
            nextWords.entries.removeIf { it.value <= 0 }
        }
        transitions.entries.removeIf { it.value.isEmpty() }

        if (unigrams.size > maxVocabulary) {
            keepMostFrequent(unigrams, maxVocabulary)
        }
        if (transitions.size > maxContexts) {
            val keep = transitions.entries
                .sortedByDescending { (_, nextWords) -> nextWords.values.sum() }
                .take(maxContexts)
                .map { it.key }
                .toHashSet()
            transitions.keys.retainAll(keep)
        }
    }

    private fun keepMostFrequent(counts: MutableMap<String, Int>, limit: Int) {
        if (counts.size <= limit) return
        val keep = counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toHashSet()
        counts.keys.retainAll(keep)
    }

    /**
     * Predicts up to [max] next words for the given completed [context]. Falls back from the
     * two-word context to the last word, and finally to the most common learned words when the
     * editor has no previous word yet.
     */
    @Synchronized
    fun predict(context: List<String>, max: Int): List<LocalWordPrediction> {
        if (max <= 0) return emptyList()
        val normalizedContext = context
            .map(LocalPredictionText::normalize)
            .filter { it.isNotBlank() }
            .takeLast(MAX_CONTEXT_WORDS)

        val scores = LinkedHashMap<String, Double>()
        for (depth in normalizedContext.size downTo 1) {
            val key = normalizedContext.takeLast(depth).joinToString(CONTEXT_SEPARATOR)
            val nextWords = transitions[key] ?: continue
            val total = nextWords.values.sum().coerceAtLeast(1)
            nextWords.forEach { (word, count) ->
                if (count >= MIN_REPEATED_EVIDENCE) {
                    scores[word] = maxOf(scores[word] ?: 0.0, count.toDouble() / total)
                }
            }
            if (scores.isNotEmpty()) break
        }

        if (scores.isEmpty() && normalizedContext.isEmpty()) {
            val total = unigrams.values.sum().coerceAtLeast(1)
            unigrams.forEach { (word, count) ->
                if (count >= MIN_REPEATED_EVIDENCE) {
                    scores[word] = count.toDouble() / total
                }
            }
        }

        return scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(max)
            .map { (word, score) -> LocalWordPrediction(word, score.coerceIn(0.0, 1.0)) }
    }

    @Synchronized
    fun snapshot(): LocalNgramSnapshot = LocalNgramSnapshot(
        transitions = transitions.mapValues { (_, nextWords) -> nextWords.toMap() },
        unigrams = unigrams.toMap(),
    )

    @Synchronized
    fun restore(snapshot: LocalNgramSnapshot) {
        transitions.clear()
        unigrams.clear()
        snapshot.unigrams.forEach { (word, count) ->
            if (word.isNotBlank() && count > 0) unigrams[word] = count
        }
        snapshot.transitions.forEach { (previous, nextWords) ->
            if (previous.isBlank()) return@forEach
            val restored = nextWords.filterValues { it > 0 }.toMutableMap()
            if (restored.isNotEmpty()) transitions[previous] = HashMap(restored)
        }
        lastCompletedWords = emptyList()
        if (unigrams.size > maxVocabulary || transitions.size > maxContexts) {
            prune()
        }
    }

    @Synchronized
    fun clear() {
        unigrams.clear()
        transitions.clear()
        lastCompletedWords = emptyList()
    }

    private companion object {
        const val CONTEXT_SEPARATOR = "\u0001"
        const val MAX_OBSERVED_WORDS = 64
        const val MIN_REPEATED_EVIDENCE = 2
    }
}
