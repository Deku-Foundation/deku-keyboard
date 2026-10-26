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

import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator

abstract class FileStorage(
    val fs: FileSystem = SystemFileSystem,
) : Storage {
    abstract fun getBasePath(): Path

    fun resolveCanonicalPath(ref: DekuRef): Path = resolveCanonicalPath(ref.relativePath)

    internal fun resolveCanonicalPath(relativePath: String): Path {
        if (
            relativePath.startsWith("/") ||
            relativePath.startsWith("\\") ||
            relativePath.split('/', '\\').any { it == "." || it == ".." } ||
            '\u0000' in relativePath
        ) {
            throw IOException("Invalid relative storage path: $relativePath")
        }

        // Targets may not exist yet. Canonicalize the deepest existing ancestor and append only
        // validated path segments so new cache files/directories can be resolved safely.
        val basePath = resolveAllowingMissing(getBasePath())
        val path = if (relativePath.isEmpty()) {
            basePath
        } else {
            resolveAllowingMissing(Path(basePath, relativePath))
        }
        if (!path.isWithin(basePath)) {
            throw IOException("Storage path escapes its base directory: $relativePath")
        }
        return path
    }

    private fun resolveAllowingMissing(path: Path): Path {
        if (fs.exists(path)) return fs.resolve(path)

        val missingSegments = mutableListOf<String>()
        var ancestor = path
        while (!fs.exists(ancestor)) {
            val parent = ancestor.parent ?: return path
            missingSegments += ancestor.name
            ancestor = parent
        }

        var resolved = fs.resolve(ancestor)
        for (segment in missingSegments.asReversed()) {
            resolved = Path(resolved, segment)
        }
        return resolved
    }

    private fun Path.isWithin(base: Path): Boolean {
        val baseString = base.toString()
        val candidateString = toString()
        if (candidateString == baseString) return true
        val prefix = if (baseString.endsWith(SystemPathSeparator)) {
            baseString
        } else {
            "$baseString$SystemPathSeparator"
        }
        return candidateString.startsWith(prefix)
    }

    override fun delete(ref: DekuRef, mustExist: Boolean) {
        try {
            val path = resolveCanonicalPath(ref)
            fs.delete(path, mustExist)
        } catch (e: FileNotFoundException) {
            if (mustExist) {
                throw e
            } else {
                return
            }
        }
    }

    final override fun exists(ref: DekuRef): Boolean {
        val path = resolveCanonicalPath(ref)
        return fs.exists(path)
    }

    override fun list(ref: DekuRef): List<DekuRef> {
        val path = resolveCanonicalPath(ref)
        return fs.list(path).map { ref.subRef(it.name) }
    }

    override fun mkdirs(ref: DekuRef) {
        val path = resolveCanonicalPath(ref)
        fs.createDirectories(path)
    }

    override fun source(ref: DekuRef): RawSource {
        val path = resolveCanonicalPath(ref)
        return fs.source(path)
    }

    override fun sink(ref: DekuRef): RawSink {
        val path = resolveCanonicalPath(ref)
        return fs.sink(path)
    }
}
