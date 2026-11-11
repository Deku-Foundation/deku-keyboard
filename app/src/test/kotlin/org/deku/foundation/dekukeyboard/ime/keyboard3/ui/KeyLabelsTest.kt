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

package org.deku.foundation.dekukeyboard.ime.keyboard3.ui

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.k3lp.lib.text.asK3String

class KeyLabelsTest : FunSpec({
    test("character keys announce their text") {
        readableKeyLabel("a".asK3String()) shouldBe "a"
        readableKeyLabel("é".asK3String()) shouldBe "é"
    }

    test("blank character keys are not announced") {
        readableKeyLabel(" ".asK3String()) shouldBe null
        readableKeyLabel("".asK3String()) shouldBe null
    }

    test("descriptor names become readable labels") {
        readableDescriptorLabel("backspace") shouldBe "Backspace"
        readableDescriptorLabel("shift-left") shouldBe "Shift Left"
        readableDescriptorLabel("org.k3lp.action.enter") shouldBe "Enter"
    }

    test("empty descriptor names produce no label") {
        readableDescriptorLabel("") shouldBe null
        readableDescriptorLabel("  ") shouldBe null
    }
})
