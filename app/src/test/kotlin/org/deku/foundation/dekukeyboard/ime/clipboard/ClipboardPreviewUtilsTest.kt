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

package org.deku.foundation.dekukeyboard.ime.clipboard

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardPreviewUtilsTest : FunSpec({
    test("keeps images that already fit at full resolution") {
        calculateInSampleSize(720, 720, 720) shouldBe 1
        calculateInSampleSize(99, 99, 100) shouldBe 1
    }

    test("downsamples large photos by a power of two") {
        calculateInSampleSize(4000, 3000, 720) shouldBe 4
        calculateInSampleSize(1440, 2560, 720) shouldBe 2
    }

    test("uses the larger dimension to choose the sample size") {
        calculateInSampleSize(4000, 100, 720) shouldBe 4
        calculateInSampleSize(100, 4000, 720) shouldBe 4
    }

    test("returns 1 for unknown or invalid bounds") {
        calculateInSampleSize(0, 0, 720) shouldBe 1
        calculateInSampleSize(-1, 5000, 720) shouldBe 1
    }

    test("rejects a non-positive preview bound") {
        shouldThrow<IllegalArgumentException> { calculateInSampleSize(1000, 1000, 0) }
    }
})
