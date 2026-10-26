/*
 * Copyright (C) 2026 The Deku Foundation Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package org.deku.foundation.dekukeyboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly

class NlpSessionSuggestionPolicyTest : FunSpec({
    test("private sessions accept only suggestions generated for that private session") {
        selectSuggestionsForSession(
            isPrivateSession = true,
            suggestionsFromCurrentSession = listOf("current-private"),
            suggestionsWereGeneratedPrivately = true,
            clipboardSuggestions = listOf("clipboard"),
        ) shouldContainExactly listOf("current-private")
    }

    test("private sessions reject stale non-private candidates and clipboard suggestions") {
        selectSuggestionsForSession(
            isPrivateSession = true,
            suggestionsFromCurrentSession = listOf("stale-public"),
            suggestionsWereGeneratedPrivately = false,
            clipboardSuggestions = listOf("clipboard"),
        ) shouldContainExactly emptyList()
    }

    test("normal sessions prioritize fresh clipboard suggestions") {
        selectSuggestionsForSession(
            isPrivateSession = false,
            suggestionsFromCurrentSession = listOf("learned"),
            suggestionsWereGeneratedPrivately = false,
            clipboardSuggestions = listOf("clipboard"),
        ) shouldContainExactly listOf("clipboard")
    }

    test("normal sessions do not reuse private-session candidates") {
        selectSuggestionsForSession(
            isPrivateSession = false,
            suggestionsFromCurrentSession = listOf("stale-private"),
            suggestionsWereGeneratedPrivately = true,
            clipboardSuggestions = emptyList(),
        ) shouldContainExactly emptyList()
    }
})
