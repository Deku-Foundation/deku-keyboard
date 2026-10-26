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

import kotlinx.io.RawSink
import kotlinx.io.RawSource

/**
 * An interface providing basic operations on an underlying storage medium.
 *
 * This API is designed to closely follow [kotlinx.io.files.FileSystem].
 */
interface Storage {
    fun canAccessUnderlyingMedium(ref: DekuRef): Boolean

    fun delete(ref: DekuRef, mustExist: Boolean = true)

    fun deleteContents(ref: DekuRef) {
        list(ref).forEach { deleteRecursively(it) }
    }

    fun deleteRecursively(ref: DekuRef) {
        list(ref).forEach { deleteRecursively(it) }
        delete(ref)
    }

    fun exists(ref: DekuRef): Boolean

    fun list(ref: DekuRef): List<DekuRef>

    fun mkdirs(ref: DekuRef)

    fun source(ref: DekuRef): RawSource

    fun sink(ref: DekuRef): RawSink

    companion object
}
