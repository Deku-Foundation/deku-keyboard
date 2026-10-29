/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package org.deku.foundation.dekukeyboard.ime.theme

import org.deku.foundation.dekukeyboard.ime.io.AndroidStorage
import org.deku.foundation.dekukeyboard.lib.devtools.flogError
import kotlinx.io.files.SystemFileSystem
import org.deku.foundation.dekukeyboard.lib.snygg.value.SnyggAssetResolver
import java.net.URI

internal fun parseThemeAssetRelativePath(uri: String): String {
    val parsed = URI.create(uri)
    require(parsed.scheme == "flex") { "Theme assets must use the flex scheme" }
    require(parsed.rawAuthority.isNullOrEmpty()) { "Theme asset URIs must not include an authority" }
    require(parsed.rawQuery == null && parsed.rawFragment == null) {
        "Theme asset URIs must not include a query or fragment"
    }

    val path = parsed.path?.removePrefix("/")?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Theme asset URI must include a relative path")
    require('\\' !in path && '\u0000' !in path) { "Invalid theme asset path" }
    require(path.split('/').all { segment ->
        segment.isNotBlank() && segment != "." && segment != ".."
    }) { "Theme asset path must not contain traversal segments" }
    return path
}

/** Resolves a theme-relative asset only within the selected theme extension. */
class DekuAssetResolver(val theme: ThemeController.Theme) : SnyggAssetResolver {
    override fun resolveAbsolutePath(uri: String) = runCatching {
        val relativePath = parseThemeAssetRelativePath(uri)
        val extensionRef = checkNotNull(theme.extensionRef) { "Loaded theme extension is unavailable" }
        check(extensionRef.isCache || extensionRef.isInternal) {
            "Theme assets must be stored in app-managed storage"
        }
        val storage = checkNotNull(theme.storage) { "Theme storage is unavailable" }
        check(storage is AndroidStorage) { "Unsupported theme storage implementation" }

        val effectiveStorage = if (extensionRef.isCache) storage.cacheStorage else storage.internalStorage
        val assetRef = extensionRef.subRef(relativePath)
        val canonicalPath = effectiveStorage.resolveCanonicalPath(assetRef)
        check(SystemFileSystem.metadataOrNull(canonicalPath)?.isRegularFile == true) {
            "Theme asset '$relativePath' does not exist or is not a regular file"
        }
        canonicalPath.toString()
    }.onFailure { exception ->
        flogError {
            "DekuAssetResolver could not resolve URI '$uri': ${exception.message}"
        }
    }
}
