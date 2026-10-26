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

package org.deku.foundation.dekukeyboard.ime.io

import android.content.Context
import java.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.asSource
import kotlinx.io.files.Path

class AndroidStorage(
    val id: Int,
    val context: Context,
    val isUserUnlocked: Boolean,
) : StorageMultiplexer() {
    val assetsStorage = object : Storage {
        override fun canAccessUnderlyingMedium(ref: DekuRef): Boolean {
            return ref.isAssets
        }

        override fun delete(ref: DekuRef, mustExist: Boolean) {
            throw UnsupportedOperationException("Cannot delete within assets ($ref)")
        }

        override fun deleteContents(ref: DekuRef) {
            throw UnsupportedOperationException("Cannot delete within assets ($ref)")
        }

        override fun deleteRecursively(ref: DekuRef) {
            throw UnsupportedOperationException("Cannot delete within assets ($ref)")
        }

        override fun exists(ref: DekuRef): Boolean {
            val path = ref.relativePath
            if (path.isEmpty()) return true

            return try {
                context.assets.open(path).use { true }
            } catch (_: IOException) {
                context.assets.list(path)?.isNotEmpty() == true
            }
        }

        override fun list(ref: DekuRef): List<DekuRef> {
            return context.assets.list(ref.relativePath)?.map { name ->
                ref.subRef(name)
            } ?: emptyList()
        }

        override fun mkdirs(ref: DekuRef) {
            throw UnsupportedOperationException("Cannot mkdirs within assets ($ref)")
        }

        override fun source(ref: DekuRef): RawSource {
            return context.assets.open(ref.relativePath).asSource()
        }

        override fun sink(ref: DekuRef): RawSink {
            throw UnsupportedOperationException("Cannot write to assets ($ref)")
        }
    }

    val cacheStorage = object : FileStorage() {
        override fun getBasePath(): Path {
            return Path(context.noBackupFilesDir.path, "cache")
        }

        override fun canAccessUnderlyingMedium(ref: DekuRef): Boolean {
            return isUserUnlocked && ref.isCache
        }
    }

    val internalStorage = object : FileStorage() {
        override fun getBasePath(): Path {
            return Path(context.filesDir.path)
        }

        override fun canAccessUnderlyingMedium(ref: DekuRef): Boolean {
            return isUserUnlocked && ref.isInternal
        }
    }

    override fun selectStorageBy(ref: DekuRef): Storage {
        return when {
            ref.isAssets -> assetsStorage
            ref.isCache -> cacheStorage
            ref.isInternal -> internalStorage
            else -> throw UnsupportedOperationException("No storage available for ref $ref")
        }
    }
}
