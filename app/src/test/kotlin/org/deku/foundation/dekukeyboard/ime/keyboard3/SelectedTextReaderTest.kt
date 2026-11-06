package org.deku.foundation.dekukeyboard.ime.keyboard3

import android.view.inputmethod.InputConnection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

class SelectedTextReaderTest : FunSpec({
    test("reads a 128 KiB current selection from InputConnection") {
        val selectedText = "x".repeat(128 * 1024)
        var queried = false
        val connection = Proxy.newProxyInstance(
            InputConnection::class.java.classLoader ?: ClassLoader.getSystemClassLoader(),
            arrayOf(InputConnection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getSelectedText" -> {
                    args?.get(0) shouldBe 0
                    queried = true
                    selectedText
                }
                else -> when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    else -> null
                }
            }
        } as InputConnection

        readSelectedText(connection) shouldBe selectedText
        queried shouldBe true
        selectedText.length shouldBe 128 * 1024
    }

    test("returns null when no InputConnection is active") {
        readSelectedText(null) shouldBe null
    }
})
