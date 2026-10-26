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

import android.content.Context
import org.deku.foundation.dekukeyboard.app.DekuPreferenceStore
import org.deku.foundation.dekukeyboard.lib.devtools.flogDebug
import org.deku.foundation.dekukeyboard.lib.devtools.flogWarning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.deku.foundation.dekukeyboard.lib.kotlin.collectLatestIn

val SubtypeJsonConfig = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = false
}

/**
 * Class which acts as a high level helper for the raw implementation of subtypes in the prefs. Additionally provides
 * helper methods for the in-keyboard language switch process.
 */
class SubtypeManager(context: Context) {
    private val prefs by DekuPreferenceStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeSubtypePersistenceMutex = Mutex()

    val subtypesFlow: StateFlow<List<Subtype>>
        field = MutableStateFlow(listOf())
    val isInitializedFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    inline var subtypes
        get() = subtypesFlow.value
        private set(v) { subtypesFlow.value = v }

    val activeSubtypeFlow: StateFlow<Subtype>
        field = MutableStateFlow(Subtype.DEFAULT)
    inline var activeSubtype
        get() = activeSubtypeFlow.value
        private set(v) { activeSubtypeFlow.value = v }

    init {
        prefs.localization.subtypes.asFlow().collectLatestIn(scope) { listRaw ->
            flogDebug { listRaw }
            // A damaged/legacy preference must not cancel this collector permanently. Recover to an
            // empty editable list, log the data problem, and let the next save replace the bad value.
            val list = if (listRaw.isNotBlank()) {
                runCatching { SubtypeJsonConfig.decodeFromString<List<Subtype>>(listRaw) }
                    .onFailure { error ->
                        flogWarning {
                            "Malformed saved subtype preferences; opening an empty language list: ${error.message}"
                        }
                    }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            subtypes = list
            evaluateActiveSubtype(list)
            isInitializedFlow.value = true
        }
    }

    private fun persistNewSubtypeList(list: List<Subtype>) {
        // Publish the latest value immediately so rapid edits cannot race against a delayed DataStore
        // emission and overwrite one another with an older snapshot.
        subtypes = list
        scope.launch {
            runCatching {
                val listRaw = SubtypeJsonConfig.encodeToString(list)
                prefs.localization.subtypes.set(listRaw)
            }.onFailure { error ->
                flogWarning { "Failed to persist language configuration: ${error.message}" }
            }
        }
    }

    /**
     * Gets the active subtype and returns it. If the activeSubtypeId points to a non-existent
     * subtype, this method tries to determine a new active subtype.
     *
     * @return The active subtype or null, if the subtype list is empty or no new active subtype
     *  could be determined.
     */
    private suspend fun evaluateActiveSubtype(list: List<Subtype>) {
        val storedSubtypeId = prefs.localization.activeSubtypeId.get()
        val subtype = list.find { it.id == storedSubtypeId } ?: list.firstOrNull() ?: Subtype.DEFAULT
        activeSubtype = subtype
        if (subtype.id != storedSubtypeId) {
            persistActiveSubtypeId()
        }
    }

    /**
     * Serialize active-language writes and always persist the latest in-memory selection. Rapid
     * presses can schedule several writes; an older coroutine must never save a stale language last.
     */
    private suspend fun persistActiveSubtypeId() {
        activeSubtypePersistenceMutex.withLock {
            prefs.localization.activeSubtypeId.set(activeSubtype.id)
        }
    }

    private fun queueActiveSubtypePersistence(): Job = scope.launch {
        runCatching { persistActiveSubtypeId() }
            .onFailure { error ->
                flogWarning { "Failed to persist active language selection: ${error.message}" }
            }
    }

    /**
     * Adds a given [subtype] to the subtype list, if it does not exist.
     *
     * @param subtype The subtype which should be added.
     * @return True if the subtype was added, false otherwise. A return value of false indicates
     *  that the subtype already exists.
     */
    fun addSubtype(subtype: Subtype): Boolean {
        if (!isInitializedFlow.value) return false
        val subtypeList = subtypes
        if (subtypeList.any { it.equalsExcludingId(subtype) }) {
            return false
        }

        // English is the default fallback keyboard, so make it an ordinary selectable subtype when
        // the user adds their first non-English language. This preserves English instead of silently
        // replacing it with the first language the user configures.
        var nextId = maxOf(
            System.currentTimeMillis(),
            (subtypeList.maxOfOrNull { it.id } ?: -1L) + 1L,
        )
        val defaultEnglish = if (
            subtypeList.isEmpty() && subtype.primaryLocale != Subtype.DEFAULT.primaryLocale
        ) {
            Subtype.DEFAULT.copy(id = nextId++)
        } else {
            null
        }
        val subtypeToAdd = subtype.copy(id = nextId)
        val newSubtypeList = subtypeList + listOfNotNull(defaultEnglish, subtypeToAdd)
        persistNewSubtypeList(newSubtypeList)
        return true
    }

    /**
     * Gets a subtype by the given [id].
     *
     * @param id The id of the subtype you want to get.
     * @return The subtype or null, if no matching subtype could be found.
     */
    fun getSubtypeById(id: Long): Subtype? {
        val subtypeList = subtypes
        return subtypeList.find { it.id == id }
    }

    /**
     * Modifies an existing subtype with the newly provided details. In order to determine which
     * subtype should be updated, the id must be the same.
     *
     * @param subtypeToModify The subtype with the new details but same id.
     */
    fun modifySubtypeWithSameId(subtypeToModify: Subtype) {
        if (!isInitializedFlow.value) return
        val subtypeList = subtypes
        val index = subtypeList.indexOfFirst { subtypeToModify.id == it.id }
        if (index >= 0 && index < subtypeList.size) {
            val newSubtypeList = subtypeList.mapIndexed { n, subtype ->
                if (n == index) {
                    subtypeToModify
                } else {
                    subtype
                }
            }
            persistNewSubtypeList(newSubtypeList)
        }
    }

    /**
     * Removes a given [subtypeToRemove]. Nothing happens if the given [subtypeToRemove] does not
     * exist.
     *
     * @param subtypeToRemove The subtype which should be removed.
     */
    fun removeSubtype(subtypeToRemove: Subtype) {
        if (!isInitializedFlow.value) return
        val subtypeList = subtypes
        val indexToRemove = subtypeList.indexOf(subtypeToRemove)
        if (indexToRemove in subtypeList.indices) {
            val newSubtypeList = subtypeList.mapIndexedNotNull { n, subtype ->
                if (n != indexToRemove) {
                    subtype
                } else {
                    null
                }
            }
            persistNewSubtypeList(newSubtypeList)
            scope.launch { evaluateActiveSubtype(newSubtypeList) }
        }
    }

    /**
     * Switch to the previous subtype in the subtype list if possible.
     */
    fun switchToPrevSubtype(): Job {
        val subtypeList = subtypes
        if (subtypeList.isEmpty()) return scope.launch { }

        val currentIndex = subtypeList.indexOfFirst { it.id == activeSubtype.id }
        val previousIndex = if (currentIndex <= 0) subtypeList.lastIndex else currentIndex - 1
        // Publish synchronously so consecutive taps are applied to the latest selection rather than
        // every coroutine reading the same previous state.
        activeSubtype = subtypeList[previousIndex]
        return queueActiveSubtypePersistence()
    }

    /**
     * Switch to the next subtype in the subtype list if possible.
     */
    fun switchToNextSubtype(): Job {
        val subtypeList = subtypes
        if (subtypeList.isEmpty()) return scope.launch { }

        val currentIndex = subtypeList.indexOfFirst { it.id == activeSubtype.id }
        val nextIndex = if (currentIndex < 0 || currentIndex == subtypeList.lastIndex) 0
            else currentIndex + 1
        // Publish synchronously so consecutive taps are applied to the latest selection rather than
        // every coroutine reading the same previous state.
        activeSubtype = subtypeList[nextIndex]
        return queueActiveSubtypePersistence()
    }

    fun switchToSubtypeById(id: Long): Job {
        val subtype = subtypes.firstOrNull { it.id == id } ?: return scope.launch { }
        activeSubtype = subtype
        return queueActiveSubtypePersistence()
    }
}
