/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.editor

import android.text.InputType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InputAttributesPasswordTest : FunSpec({
    test("recognizes standard, visible, and web text password variations") {
        InputAttributes.wrap(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .isPasswordField shouldBe true
        InputAttributes.wrap(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            .isPasswordField shouldBe true
        InputAttributes.wrap(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
            .isPasswordField shouldBe true
    }

    test("recognizes numeric password fields") {
        InputAttributes.wrap(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            .isPasswordField shouldBe true
    }

    test("does not classify normal text or numeric editors as password fields") {
        InputAttributes.wrap(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL)
            .isPasswordField shouldBe false
        InputAttributes.wrap(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL)
            .isPasswordField shouldBe false
    }
})
