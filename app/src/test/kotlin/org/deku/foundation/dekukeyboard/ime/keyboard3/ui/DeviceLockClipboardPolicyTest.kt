/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.keyboard3.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DeviceLockClipboardPolicyTest : FunSpec({
    test("restricts clipboard access when the device is locked") {
        isImeClipboardRestricted(isDeviceLocked = true, isKeyguardLocked = false) shouldBe true
    }

    test("restricts clipboard access while the keyguard is visible") {
        isImeClipboardRestricted(isDeviceLocked = false, isKeyguardLocked = true) shouldBe true
    }

    test("allows clipboard access only when both lock states are clear") {
        isImeClipboardRestricted(isDeviceLocked = false, isKeyguardLocked = false) shouldBe false
    }
})
