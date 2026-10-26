package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_CHARACTER_LAYOUT = "qwerty"

internal fun resolveCharacterLayoutId(
    localizationManifestJson: String,
    languageTag: String,
): String {
    val presets = runCatching {
        Json.parseToJsonElement(localizationManifestJson)
            .jsonObject["subtypePresets"]
            ?.jsonArray
            ?.mapNotNull { element ->
                val objectValue = element.jsonObject
                val tag = objectValue["languageTag"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val layout = objectValue["preferred"]?.jsonObject
                    ?.get("characters")?.jsonPrimitive?.contentOrNull
                    ?.substringAfterLast(':')
                    ?: return@mapNotNull null
                tag to layout
            }
    }.getOrNull().orEmpty()

    val requestedTag = Locale.forLanguageTag(languageTag).toLanguageTag()
    val requestedLanguage = requestedTag.substringBefore('-').lowercase(Locale.ROOT)
    val exact = presets.firstOrNull { (tag, _) ->
        Locale.forLanguageTag(tag).toLanguageTag().equals(requestedTag, ignoreCase = true)
    }
    val byLanguage = presets.firstOrNull { (tag, _) ->
        Locale.forLanguageTag(tag).language.equals(requestedLanguage, ignoreCase = true)
    }
    val selected = exact?.second ?: byLanguage?.second ?: DEFAULT_CHARACTER_LAYOUT
    return selected.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: DEFAULT_CHARACTER_LAYOUT
}

/**
 * Builds the touch layout for a character-layout resource while retaining the K3 key actions
 * and bottom control row. Key labels are copied into output attributes with XML escaping.
 *
 * Invalid or incomplete layout data throws IllegalArgumentException so the caller can fall back to
 * the bundled QWERTY layout instead of loading a partially generated keyboard.
 */
internal fun buildKeyboard3Xml(
    baseXml: String,
    characterLayoutJson: String,
    languageTag: String,
): String {
    val rows = runCatching {
        Json.parseToJsonElement(characterLayoutJson).jsonArray.map { row ->
            row.jsonArray.map { cell ->
                val obj = cell.jsonObject
                val label = obj["label"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                    ?: obj["code"]?.jsonPrimitive?.intOrNull?.let(::codePointToString)
                    ?: throw IllegalArgumentException("Character layout contains a key without a label or code")
                label
            }
        }
    }.getOrElse { cause ->
        throw IllegalArgumentException("Unable to parse character layout JSON", cause)
    }
    require(rows.size >= 3 && rows.take(3).all { it.isNotEmpty() }) {
        "A touch character layout must contain at least three non-empty rows"
    }

    val isRtl = isRightToLeftLanguage(languageTag)
    val effectiveRows = if (isRtl) rows.map { it.reversed() } else rows

    val keyDefinitions = buildString {
        for (rowIndex in 0..2) {
            effectiveRows[rowIndex].forEachIndexed { columnIndex, label ->
                val id = keyId(rowIndex, columnIndex, shifted = false)
                append("        <key id=\"").append(id)
                    .append("\" output=\"").append(xmlAttribute(label)).append("\" />\n")
                val shiftedLabel = shiftLabel(label, languageTag)
                append("        <key id=\"").append(keyId(rowIndex, columnIndex, shifted = true))
                    .append("\" output=\"").append(xmlAttribute(shiftedLabel)).append("\" />\n")
            }
        }
    }

    var xml = baseXml.replaceFirst("</keys>", "$keyDefinitions    </keys>")
    val localePattern = Regex("""(<keyboard3\b[^>]*\blocale=")[^"]*(")""")
    require(localePattern.containsMatchIn(xml)) { "K3 root keyboard3 element has no locale attribute" }
    xml = localePattern.replaceFirst(xml) { match ->
        match.groupValues[1] + xmlAttribute(Locale.forLanguageTag(languageTag).toLanguageTag()) + match.groupValues[2]
    }

    xml = replaceTouchLayerRows(xml, "base", effectiveRows, shifted = false, isRtl = isRtl, languageTag = languageTag, existingKeyIdByOutput = existingKeyIdByOutput)
    xml = replaceTouchLayerRows(xml, "shift", effectiveRows, shifted = true, isRtl = isRtl, languageTag = languageTag, existingKeyIdByOutput = existingKeyIdByOutput)
    xml = applyLanguageSpecificLabels(xml, languageTag)
    return xml
}

private fun replaceTouchLayerRows(
    xml: String,
    layerId: String,
    layoutRows: List<List<String>>,
    shifted: Boolean,
    isRtl: Boolean,
    languageTag: String,
    existingKeyIdByOutput: Map<String, String>,
): String {
    val layerPattern = Regex("""(<layer\s+id="$layerId">)([\s\S]*?)(</layer>)""")
    require(layerPattern.containsMatchIn(xml)) { "K3 touch layout has no '$layerId' layer" }
    return layerPattern.replaceFirst(xml) { layerMatch ->
        val rowPattern = Regex("""<row\s+keys="[^"]*"\s*/>""")
        val oldRows = rowPattern.findAll(layerMatch.groupValues[2]).toList()
        require(oldRows.size >= 4) { "K3 '$layerId' layer must have three character rows and a control row" }
        var rowIndex = 0
        val newBody = rowPattern.replace(layerMatch.groupValues[2]) { oldRow ->
            if (rowIndex >= 3) {
                rowIndex++
                if (isRtl) {
                    val keysPattern = Regex("""keys="([^"]*)"""")
                    val keysMatch = keysPattern.find(oldRow.value)
                    if (keysMatch != null) {
                        val reversedKeys = keysMatch.groupValues[1].split(Regex("\\s+")).reversed()
                        """<row keys="${reversedKeys.joinToString(" ")}" />"""
                    } else {
                        oldRow.value
                    }
                } else {
                    oldRow.value
                }
            } else {
                val ids = layoutRows[rowIndex].mapIndexed { column, label ->
                    resolveLayoutKeyId(
                        label, rowIndex, column, shifted, languageTag, existingKeyIdByOutput
                    )
                }
                val keys = when {
                    rowIndex == 2 && isRtl ->
                        listOf("bksp") + ids + if (shifted) "shift-to-base" else "base-to-shift"
                    rowIndex == 2 ->
                        listOf(if (shifted) "shift-to-base" else "base-to-shift") + ids + "bksp"
                    else -> ids
                }
                rowIndex++
                "            <row keys=\"${keys.joinToString(" ")}\" />"
            }
        }
        layerMatch.groupValues[1] + newBody + layerMatch.groupValues[3]
    }
}


private fun isRightToLeftLanguage(languageTag: String): Boolean {
    return Locale.forLanguageTag(languageTag).language.lowercase(Locale.ROOT) in
        setOf("ar", "fa", "ur", "he", "iw", "ckb", "ku")
}

private fun applyLanguageSpecificLabels(xml: String, languageTag: String): String {
    // Keep the established Arabic label convention requested by users of Arabic layouts.
    if (!Locale.forLanguageTag(languageTag).language.equals("ar", ignoreCase = true)) return xml
    val pattern = Regex("""(<string\s+id="symbol_1_display"\s+value=")[^"]*(")""")
    return pattern.replaceFirst(xml) { match ->
        match.groupValues[1] + "٣٢١؟" + match.groupValues[2]
    }
}

private fun resolveLayoutKeyId(
    label: String,
    row: Int,
    column: Int,
    shifted: Boolean,
    languageTag: String,
    existingKeyIdByOutput: Map<String, String>,
): String {
    val output = if (shifted) shiftLabel(label, languageTag) else label
    return existingKeyIdByOutput[output] ?: generatedKeyId(row, column, shifted)
}

private fun generatedKeyId(row: Int, column: Int, shifted: Boolean): String =
    "deku-layout-r$row-k$column" + if (shifted) "-shift" else ""

private fun unescapeXmlAttribute(value: String): String =
    value.replace("&quot;", "\\\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

private fun shiftLabel(label: String, languageTag: String): String {
    if (label == "ß") return "ẞ"
    return label.uppercase(Locale.forLanguageTag(languageTag))
}

private fun codePointToString(codePoint: Int): String? {
    if (!Character.isValidCodePoint(codePoint) ||
        codePoint in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code
    ) {
        return null
    }
    return String(Character.toChars(codePoint))
}

private fun xmlAttribute(value: String): String = buildString(value.length) {
    value.forEach { char ->
        append(
            when (char) {
                '&' -> "&amp;"
                '"' -> "&quot;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                else -> char.toString()
            }
        )
    }
}
