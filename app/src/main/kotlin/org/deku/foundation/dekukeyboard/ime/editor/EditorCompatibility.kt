package org.deku.foundation.dekukeyboard.ime.editor

/**
 * Some game/text engines (notably reports against Ren'Py) misreport single-line text inputs as
 * VISIBLE_PASSWORD and provide no usable surrounding-text state. When no explicit IME action is
 * provided, use the Android key-event path rather than committing a newline or dispatching action 0.
 *
 * This predicate deliberately does not apply to real password fields that declare DONE/NEXT/etc.,
 * or to multiline fields where newline insertion is expected.
 */
internal fun requiresVisiblePasswordEnterFallback(
    inputAttributes: InputAttributes,
    imeOptions: ImeOptions,
): Boolean =
    inputAttributes.type == InputAttributes.Type.TEXT &&
        inputAttributes.variation == InputAttributes.Variation.VISIBLE_PASSWORD &&
        !inputAttributes.flagTextMultiLine &&
        imeOptions.action == ImeOptions.Action.UNSPECIFIED
