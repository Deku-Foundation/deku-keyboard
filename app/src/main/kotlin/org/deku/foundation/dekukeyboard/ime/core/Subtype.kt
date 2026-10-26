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

package org.deku.foundation.dekukeyboard.ime.core

import org.deku.foundation.dekukeyboard.ime.nlp.latin.LatinLanguageProvider
import org.deku.foundation.dekukeyboard.lib.DekuLocale
import kotlinx.serialization.Serializable

/**
 * Data class which represents an user-specified set of language and layout. String representations
 * of this object are stored as an Json array in the preference datastore.
 *
 * @property id The ID of this subtype.
 * @property primaryLocale The primary locale of this subtype.
 * @property secondaryLocales The secondary locales of this subtype. May be an empty list.
 * @property nlpProviders The NLP provider map to instantiate the correct provider for each category.
 * @property characterLayoutId Optional bundled character-layout choice; null preserves automatic
 * locale-based selection for older saved preferences and backups.
 */
@Serializable
data class Subtype(
    val id: Long,
    val primaryLocale: DekuLocale,
    val secondaryLocales: List<DekuLocale>,
    val nlpProviders: SubtypeNlpProviderMap = SubtypeNlpProviderMap(),
    val characterLayoutId: String? = null,
) {
    companion object {
        /**
         * Subtype to use when prefs do not contain any valid subtypes.
         */
        val DEFAULT = Subtype(
            id = -1,
            primaryLocale = DekuLocale.from("en", "US"),
            secondaryLocales = emptyList(),
            nlpProviders = SubtypeNlpProviderMap(),
        )
    }

    /**
     * Returns an accumulated list of all locales of this subtype.
     */
    fun locales(): List<DekuLocale> {
        val locales = mutableListOf(primaryLocale)
        locales.addAll(secondaryLocales)
        return locales
    }

    /**
     * Converts this object into its short string representation, used for debugging. Format:
     *  <id>/<language_tag>/<currency_set_name>
     */
    fun toShortString(): String {
        val languageTag = primaryLocale.languageTag()
        return "$id/$languageTag"
    }

    fun equalsExcludingId(other: Subtype): Boolean {
        if (other.primaryLocale != primaryLocale) return false
        if (other.secondaryLocales != secondaryLocales) return false
        if (other.nlpProviders != nlpProviders) return false
        if (other.characterLayoutId != characterLayoutId) return false

        return true
    }
}

@Serializable
data class SubtypeNlpProviderMap(
    val spelling: String = LatinLanguageProvider.ProviderId,
    val suggestion: String = LatinLanguageProvider.ProviderId,
) {
    inline fun forEach(action: (String, String) -> Unit) {
        action("spelling", spelling)
        action("suggestion", suggestion)
    }
}
