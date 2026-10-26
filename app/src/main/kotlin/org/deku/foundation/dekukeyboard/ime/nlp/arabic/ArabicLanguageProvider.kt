/*
 * Copyright (C) 2026 Deku Foundation contributors
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

package org.deku.foundation.dekukeyboard.ime.nlp.arabic

import android.content.Context
import java.text.Normalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.deku.foundation.dekukeyboard.appContext
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.ime.nlp.SpellingProvider
import org.deku.foundation.dekukeyboard.ime.nlp.SpellingResult
import org.deku.foundation.dekukeyboard.ime.nlp.SuggestionCandidate
import org.deku.foundation.dekukeyboard.ime.nlp.SuggestionProvider
import org.deku.foundation.dekukeyboard.ime.nlp.WordSuggestionCandidate
import org.deku.foundation.dekukeyboard.lib.android.readText
import org.k3lp.runtime.K3Content

private const val ARABIC_FREQUENCY_ASSET = "ime/dict/ar/frequency.txt"

/**
 * Conservative, fully offline suggestions based on an MSA newswire frequency list.
 *
 * This list is not a complete Arabic dictionary or morphological analyser. Unknown forms are
 * returned as unspecified by the spell checker, not marked as misspellings. The provider never
 * sends typed text over the network and never auto-commits a candidate.
 */
class ArabicLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.deku.foundation.dekukeyboard.nlp.providers.arabic.msa"
    }

    private val appContext by context.appContext()
    private val loadMutex = Mutex()

    @Volatile
    private var wordPrefixIndex: Map<String, List<Pair<String, Int>>> = emptyMap()

    @Volatile
    private var wordsByFrequency: List<String> = emptyList()

    @Volatile
    private var wordFrequency: Map<String, Int> = emptyMap()

    override val providerId = ProviderId

    override suspend fun create() = Unit

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        if (!supportsArabicDictionary(subtype)) return@withContext
        if (wordsByFrequency.isNotEmpty()) return@withContext

        loadMutex.withLock {
            if (wordsByFrequency.isNotEmpty()) return@withLock
            val parsedWords = appContext.assets.readText(ARABIC_FREQUENCY_ASSET)
                .lineSequence()
                .map(::normalizeArabicWord)
                .filter { it.isNotBlank() && containsArabicScript(it) }
                .distinct()
                .toList()

            val scoredWords = parsedWords.mapIndexed { index, word ->
                word to (parsedWords.size - index)
            }
            wordPrefixIndex = buildArabicWordPrefixIndex(scoredWords)
            wordFrequency = scoredWords.toMap()
            wordsByFrequency = parsedWords
        }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        if (!supportsArabicDictionary(subtype) || !containsArabicScript(word)) {
            return SpellingResult.unspecified()
        }
        val normalized = normalizeArabicWord(word)
        return if (normalized.isNotEmpty() && normalized in wordFrequency) {
            SpellingResult.validWord()
        } else {
            // The MSA list contains lemmas and frequency-ranked entries, not every inflection.
            // Avoid false-positive misspellings for valid but unlisted Arabic words.
            SpellingResult.unspecified()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: K3Content,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        if (!supportsArabicDictionary(subtype)) return emptyList()

        val composition = content.compositionText?.toString().orEmpty()
        val rawPrefix = TRAILING_WORD.find(composition)?.value ?: return emptyList()
        if (!containsArabicScript(rawPrefix)) return emptyList()

        val suggestions = findArabicWordPrefixSuggestions(
            index = wordPrefixIndex,
            prefix = rawPrefix,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
        )
        val maxFrequency = wordsByFrequency.size.coerceAtLeast(1)
        return suggestions.map { (word, score) ->
            WordSuggestionCandidate(
                text = word,
                confidence = (score.toDouble() / maxFrequency).coerceIn(0.0, 1.0),
                isEligibleForAutoCommit = false,
                isEligibleForUserRemoval = false,
                sourceProvider = this,
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) = Unit

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) = Unit

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean = false

    override suspend fun getListOfWords(subtype: Subtype): List<String> =
        if (supportsArabicDictionary(subtype)) wordsByFrequency else emptyList()

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        if (!supportsArabicDictionary(subtype)) return 0.0
        val score = wordFrequency[normalizeArabicWord(word)] ?: return 0.0
        return score.toDouble() / wordsByFrequency.size.coerceAtLeast(1)
    }

    override suspend fun destroy() {
        wordPrefixIndex = emptyMap()
        wordsByFrequency = emptyList()
        wordFrequency = emptyMap()
    }
}

private val TRAILING_WORD = Regex("[\\p{L}\\p{M}]+$")
private val ARABIC_MARKS_AND_TATWEEL = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
private val KNOWN_ARABIC_OFFENSIVE_TERMS = setOf(
    "ابنشرموطة",
    "احا",
    "خرا",
    "شرموطة",
    "شرموطه",
    "كس",
    "كسك",
    "كسمك",
    "منيوك",
    "منيوكه",
    "نيك",
    "نيكك",
    "طيز",
    "قحبة",
    "قحبه",
    "زبي",
    "زب",
)

internal fun supportsArabicDictionary(subtype: Subtype): Boolean =
    subtype.primaryLocale.language.equals("ar", ignoreCase = true)

internal fun normalizeArabicWord(word: String): String =
    Normalizer.normalize(word, Normalizer.Form.NFC)
        .replace(ARABIC_MARKS_AND_TATWEEL, "")

/** Keep only actual Arabic-script words; locale tags alone never decide the character direction. */
internal fun containsArabicScript(value: String): Boolean =
    value.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }

internal fun buildArabicWordPrefixIndex(
    words: List<Pair<String, Int>>,
): Map<String, List<Pair<String, Int>>> {
    return words.asSequence()
        .map { (word, frequency) -> normalizeArabicWord(word) to frequency }
        .filter { (word, _) -> word.length >= 2 && containsArabicScript(word) }
        .distinctBy { it.first }
        .groupBy { (word, _) -> word.take(2) }
        .mapValues { (_, entries) ->
            entries.sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { it.first },
            )
        }
}

internal fun findArabicWordPrefixSuggestions(
    index: Map<String, List<Pair<String, Int>>>,
    prefix: String,
    maxCandidateCount: Int,
    allowPossiblyOffensive: Boolean,
): List<Pair<String, Int>> {
    val normalizedPrefix = normalizeArabicWord(prefix)
    if (normalizedPrefix.length < 2 || !containsArabicScript(normalizedPrefix) || maxCandidateCount <= 0) {
        return emptyList()
    }

    return index[normalizedPrefix.take(2)].orEmpty()
        .asSequence()
        .filter { (word, _) -> word.startsWith(normalizedPrefix) && word != normalizedPrefix }
        .filter { (word, _) -> allowPossiblyOffensive || word !in KNOWN_ARABIC_OFFENSIVE_TERMS }
        .take(maxCandidateCount.coerceAtMost(9))
        .toList()
}
