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

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Persists per-language local n-gram counts under the app's private files directory.
 *
 * Only word counts are stored; no sentences, clipboard contents or editor text are persisted.
 * All failures are non-fatal because losing personalization must never break typing.
 */
class LocalNgramStore(context: Context) {
    private val directory = File(context.filesDir, DIRECTORY_NAME)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(languageKey: String): LocalNgramModel {
        val model = LocalNgramModel()
        runCatching {
            val file = fileFor(languageKey)
            if (!file.isFile) return@runCatching
            val snapshot = json.decodeFromString(LocalNgramSnapshot.serializer(), file.readText())
            model.restore(snapshot)
        }
        return model
    }

    fun save(languageKey: String, model: LocalNgramModel) {
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return@runCatching
            val target = fileFor(languageKey)
            val temporary = File(directory, "${target.name}.tmp")
            temporary.writeText(json.encodeToString(LocalNgramSnapshot.serializer(), model.snapshot()))
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }
    }

    private fun fileFor(languageKey: String): File {
        val safeName = languageKey.replace(UNSAFE_FILE_CHARS, "_").ifBlank { "default" }
        return File(directory, "$safeName.json")
    }

    private companion object {
        const val DIRECTORY_NAME = "ime/local-ngram"
        val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
