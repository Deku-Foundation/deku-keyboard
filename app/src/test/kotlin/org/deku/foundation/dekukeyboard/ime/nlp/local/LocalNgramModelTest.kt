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

package org.deku.foundation.dekukeyboard.ime.nlp.local

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class LocalNgramModelTest : FunSpec({
    test("never learns a trailing partial word") {
        LocalPredictionText.completedWords("hello wor") shouldBe listOf("hello")
        LocalPredictionText.completedWords("hello world ") shouldBe listOf("hello", "world")
        LocalPredictionText.completedWords("hello, ") shouldBe listOf("hello")
    }

    test("tokenizes scripts other than Latin") {
        LocalPredictionText.words("Привет мир") shouldBe listOf("привет", "мир")
        LocalPredictionText.words("مرحبا بالعالم") shouldBe listOf("مرحبا", "بالعالم")
        LocalPredictionText.words("שלום עולם") shouldBe listOf("שלום", "עולם")
    }

    test("learns a transition after two observations and predicts it") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("good", "morning", "good", "morning"))

        model.predict(listOf("good"), max = 3).map { it.word } shouldContainExactly listOf("morning")
    }

    test("requires repeated evidence before suggesting") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("hello", "there"))

        model.predict(listOf("hello"), max = 3) shouldBe emptyList()
    }

    test("backs off from a two-word context to the last word") {
        val model = LocalNgramModel()
        model.observeCompletedWords(
            listOf("i", "really", "like", "i", "really", "like", "it", "i", "really", "like", "it"),
        )

        model.predict(listOf("unseen", "like"), max = 3).map { it.word } shouldContainExactly listOf("it")
    }

    test("does not learn from deletions or cursor jumps") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("one", "two"))
        model.observeCompletedWords(listOf("one"))

        model.predict(listOf("one"), max = 3) shouldBe emptyList()
    }

    test("normalizes case and Unicode before learning") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("Café", "Beyoncé", "Café", "Beyoncé"))

        model.predict(listOf("CAFÉ"), max = 3).map { it.word } shouldContainExactly listOf("beyoncé")
    }

    test("falls back to common words when there is no context") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("hello", "world", "hello", "world"))

        model.predict(emptyList(), max = 3).firstOrNull()?.word shouldBe "world"
    }

    test("keeps bounded state under vocabulary pressure") {
        val model = LocalNgramModel(maxVocabulary = 10, maxContexts = 10)
        model.observeCompletedWords((0 until 40).flatMap { listOf("word$it", "next$it") })

        (model.snapshot().unigrams.size <= 10) shouldBe true
        (model.snapshot().transitions.size <= 10) shouldBe true
    }

    test("round-trips through a snapshot") {
        val model = LocalNgramModel()
        model.observeCompletedWords(listOf("hello", "world", "hello", "world"))
        val restored = LocalNgramModel().apply { restore(model.snapshot()) }

        restored.predict(listOf("hello"), max = 3).map { it.word } shouldContainExactly listOf("world")
    }
})
