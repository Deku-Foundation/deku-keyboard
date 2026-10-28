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

package org.deku.foundation.dekukeyboard.ime.extension

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import org.deku.foundation.dekukeyboard.ime.io.DekuRef
import org.deku.foundation.dekukeyboard.ime.io.Storage
import org.deku.foundation.dekukeyboard.ime.io.StorageController
import org.deku.foundation.dekukeyboard.ime.io.StorageMultiplexer
import org.deku.foundation.dekukeyboard.ime.io.FileStorage
import org.deku.foundation.dekukeyboard.ime.io.copy
import org.deku.foundation.dekukeyboard.ime.io.readJson
import org.deku.foundation.dekukeyboard.ime.keyboard3.extension.Keyboard3Extension
import org.deku.foundation.dekukeyboard.ime.theme.ThemeExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.deku.foundation.dekukeyboard.lib.kotlin.collectIn
import org.k3lp.lib.meta.report.MutableReportList

/**
 * Provides the [ExtensionController] instance this composition tree is associated with.
 */
val LocalExtensionController = staticCompositionLocalOf<ExtensionController> {
    error("No extension controller is associated with this composition tree.")
}

class ExtensionController(
    private val context: Context,
    private val storageController: StorageController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val extensionIoMutex = Mutex()

    private val eventChannel = Channel<Event>(Channel.RENDEZVOUS)
    private val mutableIndex = MutableIndex(storageController.activeStorage.value)

    val activeIndex: StateFlow<ExtensionIndex>
        field = MutableStateFlow(mutableIndex.build())

    init {
        storageController.activeStorage.collectIn(scope) { newStorage ->
            eventChannel.send(Event.ResetStorage(newStorage))
        }
        scope.launch {
            while (true) {
                val event = eventChannel.receive()
                mutableIndex.handle(event)
                activeIndex.value = mutableIndex.build()
            }
        }
    }

    /**
     * Deletes an installed, user-managed extension. Bundled APK assets can never be deleted.
     *
     * The controller verifies the extension against the current index and confines deletion to
     * exactly one child of the internal extensions directory before touching storage.
     */
    suspend fun delete(extension: Extension<*>): Boolean = extensionIoMutex.withLock {
        val extensionId = extension.manifest.meta.id
        val indexedExtension = activeIndex.value.extensions[extensionId]
        if (indexedExtension?.sourceRef != extension.sourceRef) {
            return@withLock false
        }
        require(extension.canDelete() && extension.sourceRef.isInternal) {
            "Only user-installed extensions can be deleted."
        }

        require(isSafeInstalledExtensionPath(extension.sourceRef.relativePath, extensionId)) {
            "Refusing to delete a path outside the installed extension directory."
        }

        val storage = storageController.activeStorage.value
        check(storage.canAccessUnderlyingMedium(extension.sourceRef)) {
            "Extension storage is currently unavailable."
        }
        check(storage.exists(extension.sourceRef)) {
            "The selected extension no longer exists."
        }
        storage.deleteRecursively(extension.sourceRef)
        eventChannel.send(Event.ResetStorage(storage))
        true
    }

    /**
     * Exports an extension as a .flex ZIP archive. The archive contains the extension's contents
     * at its root (including extension.json), matching the importer's package convention.
     *
     * Output is restricted to content:// destinations supplied by Android's document picker.
     * Files are staged under app cache and removed on both success and failure.
     */
    suspend fun export(extension: Extension<*>, ref: DekuRef) = extensionIoMutex.withLock {
        val indexedExtension = activeIndex.value.extensions[extension.manifest.meta.id]
        check(indexedExtension?.sourceRef == extension.sourceRef) {
            "The selected extension is no longer installed."
        }
        require(extension.sourceRef.isInternal || extension.sourceRef.isAssets) {
            "Unsupported extension source."
        }
        require(ref.isExternal && ref.uri.scheme == "content") {
            "Extension exports must target a document-provider content URI."
        }

        val storage = storageController.activeStorage.value
        check(storage.canAccessUnderlyingMedium(extension.sourceRef)) {
            "Extension storage is currently unavailable."
        }
        check(storage.exists(extension.sourceRef)) {
            "The selected extension no longer exists."
        }

        val token = UUID.randomUUID().toString()
        val stagingRoot = DekuRef.cache("extension-export/$token")
        val archiveRef = DekuRef.cache("extension-export/$token.flex")
        val stagingParent = DekuRef.cache("extension-export")
        try {
            storage.mkdirs(stagingParent)
            copyExtensionTree(storage, extension.sourceRef, stagingRoot)
            val stagingDirectory = resolveLocalFile(storage, stagingRoot)
            val archiveFile = resolveLocalFile(storage, archiveRef)
            check(stagingDirectory.isDirectory) { "Extension staging directory was not created." }
            archiveFile.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    error("Could not create extension export cache directory.")
                }
            }

            ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zip ->
                writeZipDirectory(stagingDirectory, stagingDirectory, zip)
            }

            val output = checkNotNull(context.contentResolver.openOutputStream(ref.uri, "w")) {
                "The document provider did not open the selected export destination."
            }
            output.use { destination ->
                archiveFile.inputStream().use { source -> source.copyTo(destination) }
            }
        } finally {
            runCatching {
                if (storage.exists(stagingRoot)) storage.deleteRecursively(stagingRoot)
            }
            runCatching { storage.delete(archiveRef, mustExist = false) }
        }
    }

    private fun copyExtensionTree(
        storage: Storage,
        source: DekuRef,
        destination: DekuRef,
    ) {
        val selectedStorage = (storage as? StorageMultiplexer)?.selectStorageBy(source)
        val isDirectory = when (selectedStorage) {
            is FileStorage -> resolveLocalFile(storage, source).isDirectory
            else -> storage.list(source).isNotEmpty()
        }

        if (isDirectory) {
            storage.mkdirs(destination)
            for (child in storage.list(source)) {
                val childName = child.pathName
                require(
                    childName.isNotBlank() &&
                        childName != "." &&
                        childName != ".." &&
                        '/' !in childName &&
                        '\\' !in childName
                ) {
                    "Extension contains an invalid path segment."
                }
                copyExtensionTree(storage, child, destination.subRef(childName))
            }
        } else {
            Storage.copy(storage, source, storage, destination)
        }
    }

    private fun resolveLocalFile(storage: Storage, ref: DekuRef): File {
        val multiplexer = storage as? StorageMultiplexer
            ?: error("The active storage cannot resolve local export files.")
        val fileStorage = multiplexer.selectStorageBy(ref) as? FileStorage
            ?: error("The export staging reference does not point to a local file.")
        return File(fileStorage.resolveCanonicalPath(ref).toString())
    }

    private fun writeZipDirectory(
        root: File,
        directory: File,
        zip: ZipOutputStream,
    ) {
        val children = checkNotNull(directory.listFiles()) {
            "Could not read staged extension files."
        }.sortedBy { it.name }

        for (child in children) {
            val entryName = child.relativeTo(root).invariantSeparatorsPath
            require(isSafeExtensionArchiveEntryName(entryName)) {
                "Extension export contains an unsafe ZIP path."
            }
            if (child.isDirectory) {
                zip.putNextEntry(ZipEntry("$entryName/"))
                zip.closeEntry()
                writeZipDirectory(root, child, zip)
            } else {
                zip.putNextEntry(ZipEntry(entryName))
                child.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private sealed interface Event {
        data class ResetStorage(val newStorage: Storage) : Event
    }

    private class MutableIndex(initialStorage: Storage) {
        private val bundledRef = DekuRef.assets(EXTENSIONS_PATH)
        private val internalRef = DekuRef.internal(EXTENSIONS_PATH)

        private var storage: Storage = initialStorage
        private val extensions = mutableMapOf<String, Extension<*>>()
        private val reports = MutableReportList()

        fun build(): ExtensionIndex {
            return ExtensionIndex(
                storage = storage,
                extensions = extensions.toMap(),
                reports = reports.toReportList(),
            )
        }

        suspend fun handle(event: Event) {
            when (event) {
                is Event.ResetStorage -> handleResetStorage(event.newStorage)
            }
        }

        private suspend fun handleResetStorage(newStorage: Storage) {
            storage = newStorage
            extensions.clear()
            reports.clear()
            indexExtensions(bundledRef)
            if (reports.numErrorReports == 0) {
                indexExtensions(internalRef)
            }
        }

        private fun indexExtensions(ref: DekuRef) {
            if (!storage.canAccessUnderlyingMedium(ref)) {
                // If we do not have access to the underlying medium all operations below would fail.
                // This can happen if we are initialized before first device-unlock (in direct boot mode
                // on Android).
                return
            }
            val extRefList =
                try {
                    storage.list(ref)
                } catch (e: Throwable) {
                    reports.add(
                        ExtensionIndexViolation.UnexpectedError(
                            cause = e,
                            sourceRange = ref.asSourceRange(),
                        )
                    )
                    return
                }
            for (extRef in extRefList) {
                val extId = extRef.pathName
                val manifestRef = extRef.subRef(ExtensionDefaults.MANIFEST_FILE_NAME)
                val manifest =
                    try {
                        storage.readJson<ExtensionManifest>(manifestRef, ExtensionJson)
                    } catch (e: Throwable) {
                        // TODO fine-grained violations
                        reports.add(
                            ExtensionIndexViolation.UnexpectedError(
                                cause = e,
                                sourceRange = manifestRef.asSourceRange(),
                            )
                        )
                        continue
                    }
                if (extId != manifest.meta.id) {
                    reports.add(
                        ExtensionIndexViolation.IdMismatch(
                            idInDirName = extId,
                            idInManifest = manifest.meta.id,
                            sourceRange = extRef.asSourceRange(),
                        )
                    )
                    continue
                }
                val duplicateExtension = extensions[extId]
                if (duplicateExtension != null) {
                    reports.add(
                        ExtensionIndexViolation.DuplicateExtension(
                            extRefA = duplicateExtension.sourceRef,
                            extRefB = extRef,
                            sourceRange = extRef.asSourceRange(),
                        )
                    )
                    continue
                }
                extensions[extId] = when (manifest) {
                    is Keyboard3Extension.Manifest -> {
                        Keyboard3Extension(manifest, extRef)
                    }
                    is ThemeExtension.Manifest -> {
                        ThemeExtension(manifest, extRef)
                    }
                    else -> {
                        reports.add(
                            ExtensionIndexViolation.UnknownManifest(
                                klass = manifest::class,
                                sourceRange = manifestRef.asSourceRange(),
                            )
                        )
                        continue
                    }
                }
            }
        }
    }

    companion object {
        const val EXTENSIONS_PATH = "extensions"

        val ExtensionJson = Json {
            classDiscriminator = "$"
            encodeDefaults = false
            ignoreUnknownKeys = true
            isLenient = false
            prettyPrint = true
            prettyPrintIndent = "  "
            serializersModule = SerializersModule {
                polymorphic(ExtensionManifest::class) {
                    subclass(Keyboard3Extension.Manifest::class, Keyboard3Extension.Manifest.serializer())
                    subclass(ThemeExtension.Manifest::class, ThemeExtension.Manifest.serializer())
                }
            }
        }
    }
}

/**
 * Restricts destructive operations to one direct child of the extension root.
 * Kept pure so traversal and identity assumptions can be regression-tested without Android storage.
 */
internal fun isSafeInstalledExtensionPath(relativePath: String, extensionId: String): Boolean {
    val segments = relativePath.split('/')
    return segments.size == 2 &&
        segments[0] == ExtensionController.EXTENSIONS_PATH &&
        segments[1] == extensionId &&
        extensionId.isNotBlank() &&
        '/' !in extensionId &&
        '\\' !in extensionId
}

/**
 * ZIP entry names must be relative POSIX paths. Reject Windows separators too because archives
 * can later be extracted on another platform.
 */
internal fun isSafeExtensionArchiveEntryName(entryName: String): Boolean {
    if (entryName.isBlank() || entryName.startsWith("/") || '\\' in entryName) return false
    val segments = entryName.split('/')
    return segments.all { it.isNotBlank() && it != "." && it != ".." }
}
