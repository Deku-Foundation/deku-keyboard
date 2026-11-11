/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.extension

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExtensionPathSafetyTest : FunSpec({
    test("allows only the selected extension directly under the installed extension root") {
        isSafeInstalledExtensionPath("extensions/local.example.theme", "local.example.theme") shouldBe true
    }

    test("rejects the extensions root, nested paths, and mismatched IDs for deletion") {
        isSafeInstalledExtensionPath("extensions", "theme") shouldBe false
        isSafeInstalledExtensionPath("extensions/theme/assets", "theme") shouldBe false
        isSafeInstalledExtensionPath("extensions/other", "theme") shouldBe false
        isSafeInstalledExtensionPath("../theme", "theme") shouldBe false
        isSafeInstalledExtensionPath("extensions/../theme", "theme") shouldBe false
        isSafeInstalledExtensionPath("extensions/", "") shouldBe false
    }

    test("rejects unsafe or ambiguous ZIP entry paths") {
        isSafeExtensionArchiveEntryName("extension.json") shouldBe true
        isSafeExtensionArchiveEntryName("stylesheets/day.json") shouldBe true
        isSafeExtensionArchiveEntryName("../escape.txt") shouldBe false
        isSafeExtensionArchiveEntryName("stylesheets/../../escape.txt") shouldBe false
        isSafeExtensionArchiveEntryName("/absolute.txt") shouldBe false
        isSafeExtensionArchiveEntryName("stylesheets\\\\escape.txt") shouldBe false
        isSafeExtensionArchiveEntryName("stylesheets//day.json") shouldBe false
        isSafeExtensionArchiveEntryName("") shouldBe false
    }
})
