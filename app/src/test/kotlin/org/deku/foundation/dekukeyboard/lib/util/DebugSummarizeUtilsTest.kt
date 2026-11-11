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

package org.deku.foundation.dekukeyboard.lib.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith

class DebugSummarizeUtilsTest : FunSpec({
    test("summarizes null as an explicit marker") {
        summarizeBundleValue(null) shouldBe "null"
    }

    test("renders primitive and string extras") {
        summarizeBundleValue("text") shouldBe "text"
        summarizeBundleValue(42) shouldBe "42"
        summarizeBundleValue(true) shouldBe "true"
    }

    test("renders collections and nested arrays without failing") {
        summarizeBundleValue(listOf("a", 1, null)) shouldBe "[a, 1, null]"
        summarizeBundleValue(arrayOf("a", "b")) shouldBe "[a, b]"
        summarizeBundleValue(mapOf("key" to 7)) shouldBe "{key=7}"
    }

    test("falls back to the class name for unknown types") {
        summarizeBundleValue(StringBuilder("ignored")) shouldBe "ignored"
        summarizeBundleValue(Any()) shouldStartWith "java.lang.Object"
    }

    test("collapses newlines so one extra cannot break the summary layout") {
        val summary = summarizeBundleValue("first\nsecond\rthird")
        summary shouldNotContain "\n"
        summary shouldNotContain "\r"
        summary shouldBe "first second third"
    }

    test("bounds very long values") {
        val summary = summarizeBundleValue("x".repeat(1000))
        summary.length shouldBe 121
        summary shouldContain "…"
    }
})
