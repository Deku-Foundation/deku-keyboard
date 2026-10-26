/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.nlp.latin

import android.content.Context
import org.deku.foundation.dekukeyboard.appContext
import org.deku.foundation.dekukeyboard.ime.core.Subtype
import org.deku.foundation.dekukeyboard.ime.nlp.SpellingProvider
import org.deku.foundation.dekukeyboard.ime.nlp.SpellingResult
import org.deku.foundation.dekukeyboard.ime.nlp.SuggestionCandidate
import org.deku.foundation.dekukeyboard.ime.nlp.SuggestionProvider
import org.deku.foundation.dekukeyboard.ime.nlp.WordSuggestionCandidate
import org.deku.foundation.dekukeyboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.Locale
import org.deku.foundation.dekukeyboard.lib.android.readText
import org.deku.foundation.dekukeyboard.lib.kotlin.guardedByLock
import org.k3lp.runtime.K3Content

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.deku.foundation.dekukeyboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    // The bundled word-frequency asset is English-only. An immutable two-letter-prefix index
    // keeps per-keystroke lookup bounded to one small bucket instead of scanning the full wordlist.
    @Volatile
    private var englishWordPrefixIndex: Map<String, List<Pair<String, Int>>> = emptyMap()

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Never expose this English-only wordlist for Arabic or another language without a
        // matching dictionary. Layout availability and suggestion-dictionary availability differ.
        if (!supportsEnglishDictionary(subtype)) return@withContext

        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                // Here we use readText() because the test dictionary is a json dictionary
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                wordData.putAll(jsonData)
                englishWordPrefixIndex = buildEnglishWordPrefixIndex(jsonData)
            }
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
        if (!supportsEnglishDictionary(subtype)) return SpellingResult.unspecified()
        val normalizedWord = word.trim().lowercase(Locale.ROOT)
        if (normalizedWord.isEmpty()) return SpellingResult.unspecified()

        // Do not flag unknown names or words in unsupported dictionaries as correct. This
        // bundled wordlist supports membership checks only; correction candidates are not
        // fabricated when we have no reliable matching dictionary evidence.
        return if (wordData.withLock { normalizedWord in it }) {
            SpellingResult.validWord()
        } else {
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
        if (!supportsEnglishDictionary(subtype)) return emptyList()

        // The active composition is a word, not the whole editor buffer. Only autocomplete
        // an actual trailing word prefix; never rewrite text or infer a word after punctuation.
        val compositionText = content.compositionText?.toString().orEmpty()
        val prefix = TRAILING_ENGLISH_WORD.find(compositionText)?.value ?: return emptyList()
        val candidates = findEnglishWordPrefixSuggestions(
            index = englishWordPrefixIndex,
            prefix = prefix,
            maxCandidateCount = maxCandidateCount,
            allowPossiblyOffensive = allowPossiblyOffensive,
        )
        return candidates.map { (word, frequency) ->
            WordSuggestionCandidate(
                text = preservePrefixCase(word, prefix),
                confidence = (frequency / 255.0).coerceIn(0.0, 1.0),
                isEligibleForAutoCommit = false,
                isEligibleForUserRemoval = false,
                sourceProvider = this,
            )
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        if (!supportsEnglishDictionary(subtype)) return emptyList()
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        if (!supportsEnglishDictionary(subtype)) return 0.0
        return wordData.withLock { it.getOrDefault(word.lowercase(Locale.ROOT), 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if the app process is killed (which will most likely always be the case).
    }
}

private val TRAILING_ENGLISH_WORD = Regex("[\\p{L}][\\p{L}\\p{M}'-]*$")
private val KNOWN_OFFENSIVE_WORDS = setOf(
    "ass", "asshole", "bastard", "bitch", "bullshit", "crap", "cunt",
    "damn", "dick", "fuck", "fucking", "motherfucker", "piss", "shit", "shitty", "wtf",
)

internal fun supportsEnglishDictionary(subtype: Subtype): Boolean =
    subtype.primaryLocale.language.equals("en", ignoreCase = true)

internal fun buildEnglishWordPrefixIndex(
    words: Map<String, Int>,
): Map<String, List<Pair<String, Int>>> {
    return words.asSequence()
        .map { (word, frequency) -> word.lowercase(Locale.ROOT) to frequency }
        .filter { (word, _) -> word.length >= 2 }
        .distinctBy { it.first }
        .groupBy { (word, _) -> word.take(2) }
        .mapValues { (_, entries) ->
            entries.sortedWith(
                compareByDescending<Pair<String, Int>> { it.second }
                    .thenBy { it.first },
            )
        }
}

internal fun findEnglishWordPrefixSuggestions(
    index: Map<String, List<Pair<String, Int>>>,
    prefix: String,
    maxCandidateCount: Int,
    allowPossiblyOffensive: Boolean,
): List<Pair<String, Int>> {
    val normalizedPrefix = prefix.trim().lowercase(Locale.ROOT)
    if (normalizedPrefix.length < 2 || maxCandidateCount <= 0) return emptyList()

    return index[normalizedPrefix.take(2)].orEmpty()
        .asSequence()
        .filter { (word, _) -> word.startsWith(normalizedPrefix) && word != normalizedPrefix }
        .filter { (word, _) -> allowPossiblyOffensive || word !in KNOWN_OFFENSIVE_WORDS }
        .take(maxCandidateCount.coerceAtMost(9))
        .toList()
}

internal fun preservePrefixCase(candidate: String, prefix: String): String = when {
    prefix.isNotEmpty() && prefix == prefix.uppercase(Locale.ROOT) -> candidate.uppercase(Locale.ROOT)
    prefix.firstOrNull()?.isUpperCase() == true -> candidate.replaceFirstChar { it.titlecase(Locale.ROOT) }
    else -> candidate
}
