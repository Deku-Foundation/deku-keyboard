package org.deku.foundation.dekukeyboard.ime.clipboard

/**
 * Maximum Unicode code points shown in a clipboard history tile.
 *
 * This limit applies only to the preview UI. It must never be applied to the stored item or to
 * [ClipboardManager.pasteItem], so long copied content can still be pasted in full.
 */
internal const val CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS = 1_024

/**
 * Builds a bounded preview without splitting a UTF-16 surrogate pair. This limits Compose text
 * measurement for enormous clipboard payloads while leaving the underlying clipboard data intact.
 */
internal fun clipboardTextPreview(
    text: String,
    maxCodePoints: Int = CLIPBOARD_TILE_PREVIEW_MAX_CODE_POINTS,
): String {
    require(maxCodePoints >= 0) { "maxCodePoints must not be negative" }
    if (text.isEmpty() || maxCodePoints == 0) {
        return if (text.isEmpty()) text else "…"
    }
    val codePointCount = text.codePointCount(0, text.length)
    if (codePointCount <= maxCodePoints) return text
    val end = text.offsetByCodePoints(0, maxCodePoints)
    return text.substring(0, end) + "…"
}
