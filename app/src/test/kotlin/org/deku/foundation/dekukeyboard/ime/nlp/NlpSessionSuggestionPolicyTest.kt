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

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.deku.foundation.dekukeyboard.ime.editor.ImeOptions
import org.deku.foundation.dekukeyboard.ime.editor.InputAttributes

class NlpSessionSuggestionPolicyTest : FunSpec({
    test("incognito sessions do not expose keyboard-generated or clipboard suggestions") {
        selectSuggestionsForSession(
            isPrivateSession = true,
            suggestionsFromCurrentSession = listOf("current-private"),
            suggestionsWereGeneratedPrivately = true,
            clipboardSuggestions = listOf("clipboard"),
        ) shouldContainExactly emptyList()
    }

    test("incognito and no-personalized-learning flags block NLP") {
        val normalText = InputAttributes.wrap(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL)
        shouldBlockNlpSuggestions(false, normalText, ImeOptions.wrap(EditorInfo.IME_ACTION_DONE)) shouldBe false
        shouldBlockNlpSuggestions(true, normalText, ImeOptions.wrap(EditorInfo.IME_ACTION_DONE)) shouldBe true
        shouldBlockNlpSuggestions(
            false,
            normalText,
            ImeOptions.wrap(EditorInfoCompat.IME_FLAG_NO_PERSONALIZED_LEARNING),
        ) shouldBe true
    }

    test("structured data fields block suggestions and learning") {
        val blockedTypes = listOf(
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )

        blockedTypes.forEach { raw ->
            shouldBlockNlpSuggestions(
                isIncognitoMode = false,
                inputAttributes = InputAttributes.wrap(raw),
                imeOptions = ImeOptions.wrap(EditorInfo.IME_ACTION_DONE),
            ) shouldBe true
        }
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
