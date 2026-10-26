package org.deku.foundation.dekukeyboard.ime.keyboard3.extension

import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val DEFAULT_CHARACTER_LAYOUT = "qwerty"

private data class PopupVariants(
    val main: String?,
    val relevant: List<String>,
)

private data class PopupBinding(
    val keyIds: List<String>,
    val defaultKeyId: String,
)

/**
 * Finds a locale's bundled popup-map ID without coupling the keyboard builder to app settings.
 * Exact region presets win; unknown regions fall back to their primary language and then to the
 * bundled default map. IDs are accepted only when declared by the localization manifest.
 */
internal fun resolvePopupMappingId(localizationManifestJson: String, languageTag: String): String? {
    val manifest = runCatching { Json.parseToJsonElement(localizationManifestJson).jsonObject }
        .getOrNull() ?: return null
    val declaredIds = manifest["popupMappings"]?.jsonArray.orEmpty().mapNotNull { entry ->
        runCatching {
            entry.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
        }.getOrNull()
    }.toSet()
    if (declaredIds.isEmpty()) return null

    val presets = manifest["subtypePresets"]?.jsonArray.orEmpty().mapNotNull { entry ->
        runCatching {
            val obj = entry.jsonObject
            val tag = obj["languageTag"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val mapId = obj["popupMapping"]?.jsonPrimitive?.contentOrNull
                ?.substringAfterLast(':')
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
                ?: return@runCatching null
            tag to mapId
        }.getOrNull()
    }
    val requestedTag = Locale.forLanguageTag(languageTag).toLanguageTag()
    val requestedLanguage = Locale.forLanguageTag(languageTag).language
    val exact = presets.firstOrNull { (tag, _) ->
        Locale.forLanguageTag(tag).toLanguageTag().equals(requestedTag, ignoreCase = true)
    }
    val sameLanguage = presets.firstOrNull { (tag, _) ->
        Locale.forLanguageTag(tag).language.equals(requestedLanguage, ignoreCase = true)
    }
    return (exact?.second ?: sameLanguage?.second)
        ?.takeIf { it in declaredIds }
        ?: "default".takeIf { it in declaredIds }
}

private fun popupLabel(element: kotlinx.serialization.json.JsonElement?): String? {
    val obj = runCatching { element?.jsonObject }.getOrNull() ?: return null
    return obj["label"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: obj["code"]?.jsonPrimitive?.intOrNull?.let(::codePointToString)
}

/**
 * Parses the active locale popup map. Only the general "all" key group is applied to the physical
 * character rows here; input-type-specific groups remain untouched.
 */
private fun parsePopupVariants(popupMappingJson: String?): Map<String, PopupVariants> {
    if (popupMappingJson.isNullOrBlank()) return emptyMap()
    val all = runCatching {
        Json.parseToJsonElement(popupMappingJson).jsonObject["all"]?.jsonObject
    }.getOrNull() ?: return emptyMap()

    return all.entries.mapNotNull { (keyLabel, element) ->
        if (keyLabel.startsWith("~") || keyLabel.isBlank()) return@mapNotNull null
        runCatching {
            val obj = element.jsonObject
            val main = popupLabel(obj["main"])
            val relevant = obj["relevant"]?.jsonArray.orEmpty()
                .mapNotNull(::popupLabel)
                .distinct()
            if (main == null && relevant.isEmpty()) null
            else keyLabel to PopupVariants(main, relevant.filterNot { it == main })
        }.getOrNull()
    }.toMap()
}

private fun createPopupBinding(
    row: Int,
    column: Int,
    shifted: Boolean,
    variants: PopupVariants,
    existingKeyIds: Set<String>,
    existingKeyIdByOutput: Map<String, String>,
    generatedKeys: MutableMap<String, String>,
): PopupBinding? {
    val labels = (listOfNotNull(variants.main) + variants.relevant).distinct()
    if (labels.isEmpty()) return null
    val prefix = generatedKeyId(row, column, shifted) + "-popup"
    val ids = labels.mapIndexed { index, label ->
        existingKeyIdByOutput[label] ?: "$prefix-$index".also { id ->
            if (id !in existingKeyIds && !generatedKeys.containsKey(id)) {
                generatedKeys[id] = label
            }
        }
    }
    val defaultLabel = variants.main ?: labels.first()
    val defaultId = ids[labels.indexOf(defaultLabel).coerceAtLeast(0)]
    return PopupBinding(ids.distinct(), defaultId)
}

private fun applyPopupBindings(xml: String, bindings: Map<String, PopupBinding>): String {
    if (bindings.isEmpty()) return xml
    val keyTagPattern = Regex("""<key\b[^>]*\bid="([^"]+)"[^>]*/>""")
    return keyTagPattern.replace(xml) { match ->
        val binding = bindings[match.groupValues[1]] ?: return@replace match.value
        var tag = match.value
        val existingIds = Regex("""\blongPressKeyIds="([^"]*)"""")
            .find(tag)?.groupValues?.get(1)
            ?.split(Regex("""\s+"""))
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val mergedIds = (existingIds + binding.keyIds).distinct().joinToString(" ")
        fun putAttribute(source: String, name: String, value: String): String {
            val attribute = Regex("""\b$name="[^"]*"""")
            val replacement = "$name=\"$value\""
            return if (attribute.containsMatchIn(source)) {
                attribute.replaceFirst(source, replacement)
            } else {
                source.removeSuffix("/>").trimEnd() + " " + replacement + " />"
            }
        }
        tag = putAttribute(tag, "longPressKeyIds", mergedIds)
        tag = putAttribute(tag, "longPressDefaultKeyId", binding.defaultKeyId)
        tag
    }
}

internal fun parseCharacterLayoutIds(layoutManifestJson: String): Set<String> = runCatching {
    Json.parseToJsonElement(layoutManifestJson)
        .jsonObject["layouts"]?.jsonObject
        ?.get("characters")?.jsonArray
        ?.mapNotNull { layout ->
            layout.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
        }
        ?.toSet()
        .orEmpty()
}.getOrDefault(emptySet())

internal fun resolveCharacterLayoutId(
    localizationManifestJson: String,
    languageTag: String,
    preferredCharacterLayoutId: String? = null,
    availableCharacterLayoutIds: Set<String>? = null,
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
    val knownLayoutIds = availableCharacterLayoutIds ?: presets.map { it.second }.toSet()
    val preferred = preferredCharacterLayoutId?.takeIf {
        it.matches(Regex("[A-Za-z0-9_-]+")) && it in knownLayoutIds
    }
    val selected = preferred ?: exact?.second ?: byLanguage?.second ?: DEFAULT_CHARACTER_LAYOUT
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
    popupMappingJson: String? = null,
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

    // Layout JSON is ordered by physical screen position from left to right. The language's
    // text direction must not mirror key positions or modifier/control placement.
    val effectiveRows = rows

    val existingKeyIds = Regex("""<key\s+id="([^"]+)"""")
        .findAll(baseXml)
        .map { it.groupValues[1] }
        .toSet()
    val existingKeyIdByOutput = Regex("""<key\s+id="([^"]+)"[^>]*\boutput="([^"]*)"[^>]*/>""")
        .findAll(baseXml)
        .mapNotNull { match ->
            val output = unescapeXmlAttribute(match.groupValues[2])
            if (output.isEmpty() || output.startsWith("@k3:action")) null
            else output to match.groupValues[1]
        }
        .toMap()

    val generatedKeys = linkedMapOf<String, String>()
    val popupVariantsByLabel = parsePopupVariants(popupMappingJson)
    val popupBindings = linkedMapOf<String, PopupBinding>()
    for (rowIndex in 0..2) {
        effectiveRows[rowIndex].forEachIndexed { columnIndex, label ->
            val baseId = resolveLayoutKeyId(
                label = label,
                row = rowIndex,
                column = columnIndex,
                shifted = false,
                languageTag = languageTag,
                existingKeyIdByOutput = existingKeyIdByOutput,
            )
            if (baseId !in existingKeyIds && !generatedKeys.containsKey(baseId)) {
                generatedKeys[baseId] = label
            }
            val shiftedLabel = shiftLabel(label, languageTag)
            val shiftedId = resolveLayoutKeyId(
                label = label,
                row = rowIndex,
                column = columnIndex,
                shifted = true,
                languageTag = languageTag,
                existingKeyIdByOutput = existingKeyIdByOutput,
            )
            if (shiftedId !in existingKeyIds && !generatedKeys.containsKey(shiftedId)) {
                generatedKeys[shiftedId] = shiftedLabel
            }

            val basePopup = popupVariantsByLabel[label]
                ?: popupVariantsByLabel[label.lowercase(Locale.ROOT)]
            if (basePopup != null) {
                createPopupBinding(
                    rowIndex, columnIndex, false, basePopup,
                    existingKeyIds, existingKeyIdByOutput, generatedKeys,
                )?.let { popupBindings[baseId] = it }
            }
            val shiftedPopup = popupVariantsByLabel[shiftedLabel]
                ?: popupVariantsByLabel[shiftedLabel.lowercase(Locale.ROOT)]
                ?: popupVariantsByLabel[label]
            if (shiftedPopup != null) {
                createPopupBinding(
                    rowIndex, columnIndex, true, shiftedPopup,
                    existingKeyIds, existingKeyIdByOutput, generatedKeys,
                )?.let { popupBindings[shiftedId] = it }
            }
        }
    }
    val keyDefinitions = buildString {
        generatedKeys.forEach { (id, output) ->
            append("        <key id=\"").append(id)
                .append("\" output=\"").append(xmlAttribute(output)).append("\" />\n")
        }
    }

    var xml = baseXml.replaceFirst("</keys>", "$keyDefinitions    </keys>")
    xml = applyPopupBindings(xml, popupBindings)
    val localePattern = Regex("""(<keyboard3\b[^>]*\blocale=")[^"]*(")""")
    val localeMatch = localePattern.find(xml)
        ?: throw IllegalArgumentException("K3 root keyboard3 element has no locale attribute")
    xml = xml.replaceRange(
        localeMatch.range,
        localeMatch.groupValues[1] +
            xmlAttribute(Locale.forLanguageTag(languageTag).toLanguageTag()) +
            localeMatch.groupValues[2],
    )

    xml = replaceTouchLayerRows(xml, "base", effectiveRows, shifted = false, languageTag = languageTag, existingKeyIdByOutput = existingKeyIdByOutput)
    xml = replaceTouchLayerRows(xml, "shift", effectiveRows, shifted = true, languageTag = languageTag, existingKeyIdByOutput = existingKeyIdByOutput)
    xml = applyLanguageSpecificLabels(xml, languageTag)
    return xml
}

private fun replaceTouchLayerRows(
    xml: String,
    layerId: String,
    layoutRows: List<List<String>>,
    shifted: Boolean,
    languageTag: String,
    existingKeyIdByOutput: Map<String, String>,
): String {
    val layerPattern = Regex("""(<layer\s+id="$layerId">)([\s\S]*?)(</layer>)""")
    val layerMatch = layerPattern.find(xml)
        ?: throw IllegalArgumentException("K3 touch layout has no '$layerId' layer")
    val rowPattern = Regex("""<row\s+keys="[^"]*"\s*/>""")
    val oldRows = rowPattern.findAll(layerMatch.groupValues[2]).toList()
    require(oldRows.size >= 4) { "K3 '$layerId' layer must have three character rows and a control row" }
    var rowIndex = 0
    val newBody = rowPattern.replace(layerMatch.groupValues[2]) { oldRow ->
        if (rowIndex >= 3) {
            // Keep the bottom control row exactly as declared by the base keyboard. Locale
            // direction changes text rendering, not physical modifier/control key positions.
            rowIndex++
            oldRow.value
        } else {
            val ids = layoutRows[rowIndex].mapIndexed { column, label ->
                resolveLayoutKeyId(
                    label, rowIndex, column, shifted, languageTag, existingKeyIdByOutput
                )
            }
            val keys = if (rowIndex == 2) {
                listOf(if (shifted) "shift-to-base" else "base-to-shift") + ids + "bksp"
            } else {
                ids
            }
            rowIndex++
            "            <row keys=\"${keys.joinToString(" ")}\" />"
        }
    }
    val replacement = layerMatch.groupValues[1] + newBody + layerMatch.groupValues[3]
    return xml.replaceRange(layerMatch.range, replacement)
}


private fun applyLanguageSpecificLabels(xml: String, languageTag: String): String {
    // Keep the established Arabic label convention requested by users of Arabic layouts.
    if (!Locale.forLanguageTag(languageTag).language.equals("ar", ignoreCase = true)) return xml
    val pattern = Regex("""(<string\s+id="symbol_1_display"\s+value=")[^"]*(")""")
    val match = pattern.find(xml) ?: return xml
    return xml.replaceRange(
        match.range,
        match.groupValues[1] + "٣٢١؟" + match.groupValues[2],
    )
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
    value.replace("&quot;", "\"")
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
