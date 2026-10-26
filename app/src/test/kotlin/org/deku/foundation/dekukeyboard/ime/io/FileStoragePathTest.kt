/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.io

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.io.IOException
import kotlinx.io.files.Path
import java.util.UUID

class FileStoragePathTest : FunSpec({
    test("resolves a not-yet-created nested target beneath the storage root") {
        val root = temporaryRoot()
        val storage = TestFileStorage(root)

        storage.resolveCanonicalPath("nested/config.json") shouldBe Path(root, "nested/config.json")
    }

    test("rejects parent traversal before returning a path") {
        val storage = TestFileStorage(temporaryRoot())

        shouldThrow<IOException> {
            storage.resolveCanonicalPath("nested/../../outside.txt")
        }
    }

    test("rejects a direct parent-segment escape even when no target exists") {
        val storage = TestFileStorage(temporaryRoot())

        shouldThrow<IOException> {
            storage.resolveCanonicalPath("../outside.txt")
        }
    }
}) {
    companion object {
        private fun temporaryRoot(): Path =
            Path(System.getProperty("java.io.tmpdir"), "deku-storage-${UUID.randomUUID()}")
    }

    private class TestFileStorage(private val root: Path) : FileStorage() {
        override fun getBasePath(): Path = root

        override fun canAccessUnderlyingMedium(ref: DekuRef): Boolean = ref.isInternal
    }
}
