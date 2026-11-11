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

package org.deku.foundation.dekukeyboard.lib.kotlin

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MimeTypeFilterEscapingTest : FunSpec({
    test("dots in a filter match literally instead of as regex wildcards") {
        val filter = mimeTypeFilterOf("application/vnd.ms-fontobject")
        filter.matches("application/vnd.ms-fontobject") shouldBe true
        filter.matches("application/vndXmsYfontobject") shouldBe false
    }

    test("plus signs in a filter match literally") {
        val filter = mimeTypeFilterOf("application/x-font+type")
        filter.matches("application/x-font+type") shouldBe true
        filter.matches("application/x-fontXtype") shouldBe false
    }

    test("a wildcard still spans multiple characters within a segment") {
        val filter = mimeTypeFilterOf("application/*")
        filter.matches("application/x-font-otf") shouldBe true
        filter.matches("application/") shouldBe false
        filter.matches("text/plain") shouldBe false
    }

    test("a filter cannot match across the type/subtype separator") {
        val filter = mimeTypeFilterOf("image/*")
        filter.matches("image/png/extra") shouldBe false
    }
})
