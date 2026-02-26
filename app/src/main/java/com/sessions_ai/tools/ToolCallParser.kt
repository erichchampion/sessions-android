package com.sessions_ai.tools

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object ToolCallParser {

    private const val TAG = "ToolCallParser"
    private const val OPEN_TAG = "<tool_call>"
    private const val CLOSE_TAG = "</tool_call>"

    /** Tools that accept a single quoted string; map tool name -> arg key (e.g. web_search -> "query"). */
    private val LABELED_QUOTED_STRING_TOOLS = mapOf(
        "web_search" to "query",
        "wikipedia" to "query",
        "fetch_page" to "url"
    )

    /**
     * Removes all complete `<tool_call>...</tool_call>` blocks and clips any incomplete
     * `<tool_call>...` blocks at the end. Also removes Qwen-style tool call formats
     * so they do not show in the UI during streaming.
     * Never throws: on any exception returns the original text so the UI does not crash.
     */
    fun scrubText(text: String): String {
        return try {
            var result = stripTaggedToolCalls(text)
            result = stripCreatePlanArrayFormat(result)
            result = stripLabeledJSONToolCallFormat(result)
            result = stripLabeledQuotedStringToolCallFormat(result)
            result = stripBareJsonToolCalls(result)
            result = result.replace("_Generating response…_", "").trim()
            result.trim()
        } catch (_: Throwable) {
            text
        }
    }

    /** Replace Unicode double-quote codepoints with ASCII " so JSON parsing and extractJsonObject work. */
    private fun normalizeUnicodeQuotes(text: String): String {
        return text.replace('\u201C', '"')  // LEFT DOUBLE QUOTATION MARK
            .replace('\u201D', '"')  // RIGHT DOUBLE QUOTATION MARK
            .replace('\u201E', '"')  // DOUBLE LOW-9 QUOTATION MARK
            .replace('\u201F', '"')  // DOUBLE HIGH-REVERSED-9 QUOTATION MARK
            .replace('\u2033', '"')  // DOUBLE PRIME
            .replace('\u2036', '"')  // REVERSED DOUBLE PRIME
    }

    /**
     * Extracts a list of paired (tool_name, arguments_map) from all supported tool call formats.
     */
    fun parseToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val normalized = normalizeUnicodeQuotes(text)
        var results = parseTaggedToolCalls(normalized)
        if (results.isEmpty()) {
            results = parseQwenXmlStyleToolCalls(normalized)
        }
        if (results.isEmpty()) {
            parseCreatePlanArrayFormat(normalized)?.let { results = listOf(it) }
        }
        if (results.isEmpty()) {
            results = parseLabeledJSONToolCalls(normalized)
        }
        if (results.isEmpty()) {
            results = parseLabeledQuotedStringToolCalls(normalized)
        }
        if (results.isEmpty()) {
            results = parseBareJsonToolCalls(normalized)
        }
        return results
    }

    /**
     * Qwen-style XML tool call: <tool_call>\n<function_name>name</function_name>\n<param>value</param>\n</tool_call>
     */
    private fun parseQwenXmlStyleToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        var searchStart = 0
        while (true) {
            val openIdx = text.indexOf(OPEN_TAG, searchStart)
            if (openIdx < 0) break
            val contentStart = openIdx + OPEN_TAG.length
            val closeIdx = text.indexOf(CLOSE_TAG, contentStart)
            if (closeIdx < 0) break
            val inner = text.substring(contentStart, closeIdx).trim()
            searchStart = closeIdx + CLOSE_TAG.length
            val name = extractXmlTagContent(inner, "function_name")
                ?: extractXmlTagContent(inner, "name") ?: continue
            val args = mutableMapOf<String, Any>()
            var i = 0
            while (i < inner.length) {
                val tagStart = inner.indexOf('<', i)
                if (tagStart < 0) break
                val tagEnd = inner.indexOf('>', tagStart)
                if (tagEnd < 0) break
                val tagName = inner.substring(tagStart + 1, tagEnd).trim()
                if (tagName.isEmpty() || tagName.startsWith("/")) {
                    i = tagEnd + 1
                    continue
                }
                val closeTag = "</$tagName>"
                val valueStart = tagEnd + 1
                val valueEnd = inner.indexOf(closeTag, valueStart)
                if (valueEnd < 0) {
                    i = tagEnd + 1
                    continue
                }
                val value = inner.substring(valueStart, valueEnd).trim()
                if (tagName != "function_name" && tagName != "name") {
                    args[tagName] = value
                }
                i = valueEnd + closeTag.length
            }
            if (name.isNotEmpty()) results.add(Pair(name, args))
        }
        return results
    }

    private fun extractXmlTagContent(inner: String, tagName: String): String? {
        val open = "<$tagName>"
        val close = "</$tagName>"
        val start = inner.indexOf(open)
        if (start < 0) return null
        val contentStart = start + open.length
        val end = inner.indexOf(close, contentStart)
        if (end < 0) return null
        return inner.substring(contentStart, end).trim()
    }

    private fun parseTaggedToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        var searchStart = 0
        while (true) {
            val openIndex = text.indexOf(OPEN_TAG, searchStart)
            if (openIndex < 0) break
            val contentStart = openIndex + OPEN_TAG.length
            val closeIndex = text.indexOf(CLOSE_TAG, contentStart)
            val content: String
            val nextStart: Int
            if (closeIndex != -1) {
                content = text.substring(contentStart, closeIndex).trim()
                nextStart = closeIndex + CLOSE_TAG.length
            } else {
                // Missing </tool_call>: extract single JSON object to end
                val extracted = extractJsonObject(text, contentStart) ?: break
                content = extracted.first
                nextStart = extracted.second
            }
            parseJsonToolCall(content)?.let { results.add(it) }
            searchStart = nextStart
        }
        return results
    }

    private fun parseJsonToolCall(content: String): Pair<String, Map<String, Any>>? {
        fun tryParse(raw: String): Pair<String, Map<String, Any>>? {
            return try {
                val normalized = lenientJson(raw.trim())
                val json = JSONObject(normalized)
                val name = json.optString("name").ifEmpty { json.optString("Name") }
                if (name.isEmpty()) return null
                val argsObj = json.optJSONObject("args")
                val argsMap = mutableMapOf<String, Any>()
                if (argsObj != null) {
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = argsObj.opt(k)
                        if (v != null && v != JSONObject.NULL) {
                            argsMap[k] = when (v) {
                                is JSONArray -> jsonArrayToList(v)
                                is JSONObject -> jsonObjectToMap(v)
                                is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong() else v.toDouble()
                                else -> v
                            }
                        }
                    }
                }
                // Some models put parameters at top level (e.g. {"name":"create_plan","steps":[...]}). Merge those into args.
                val rootKeys = json.keys()
                while (rootKeys.hasNext()) {
                    val k = rootKeys.next()
                    if (k == "name" || k == "Name" || k == "args") continue
                    if (argsMap.containsKey(k)) continue
                    val v = json.opt(k) ?: continue
                    if (v == JSONObject.NULL) continue
                    argsMap[k] = when (v) {
                        is JSONArray -> jsonArrayToList(v)
                        is JSONObject -> jsonObjectToMap(v)
                        is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong() else v.toDouble()
                        else -> v
                    }
                }
                Pair(name, normalizeArgs(argsMap))
            } catch (e: Exception) {
                // Do not log JSONException (truncated/invalid JSON) during display scrub to avoid log spam
                if (e !is org.json.JSONException) {
                    try { Log.w(TAG, "parseJsonToolCall failed: ${e.message}", e) } catch (_: Throwable) { }
                }
                null
            }
        }
        return tryParse(content) ?: tryParse(stripNonPrintableAscii(content))
    }

    /** Strip control and other non-printable ASCII so stray chars from streaming don't break JSON. */
    private fun stripNonPrintableAscii(s: String): String =
        s.replace(Regex("[\\x00-\\x1F\\x7F]"), "")

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.opt(k) ?: continue
            if (v == JSONObject.NULL) continue
            map[k] = when (v) {
                is JSONArray -> jsonArrayToList(v)
                is JSONObject -> jsonObjectToMap(v)
                is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong() else v.toDouble()
                else -> v
            }
        }
        return map
    }

    private fun jsonArrayToList(arr: JSONArray): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until arr.length()) {
            val v = arr.opt(i) ?: continue
            if (v == JSONObject.NULL) continue
            list.add(when (v) {
                is JSONArray -> jsonArrayToList(v)
                is JSONObject -> jsonObjectToMap(v)
                is Number -> if (v.toDouble() == v.toLong().toDouble()) v.toLong() else v.toDouble()
                else -> v
            })
        }
        return list
    }

    /** Convert list of step objects to list of step strings for create_plan. */
    @Suppress("UNCHECKED_CAST")
    private fun normalizeArgs(args: Map<String, Any>): Map<String, Any> {
        if (!args.containsKey("steps")) return args
        val steps = args["steps"]
        if (steps is List<*>) {
            val stringSteps = steps.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> listOf("title", "name", "step").firstNotNullOfOrNull { key ->
                        (item[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                    else -> (item as? String)?.trim()?.takeIf { it.isNotEmpty() }
                }
            }
            if (stringSteps.isNotEmpty()) return args + ("steps" to stringSteps)
        }
        return args
    }

    /** Strip trailing commas before } or ] so lenient parsers can accept model output. Normalizes Unicode quotes to ASCII. */
    private fun lenientJson(json: String): String {
        val normalized = normalizeUnicodeQuotes(json)
        return normalized.replace(Regex(",\\s*\\}"), "}").replace(Regex(",\\s*\\]"), "]")
    }

    /**
     * Parses bare JSON tool calls with no &lt;tool_call&gt; tags or "toolName:" prefix.
     * Handles model output like: {"name":"web_search","args":{"query":"..."}}
     */
    private fun parseBareJsonToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        var searchStart = 0
        while (searchStart < text.length) {
            val braceIdx = text.indexOf('{', searchStart)
            if (braceIdx < 0) break
            val afterBrace = text.substring(braceIdx + 1).trimStart()
            val looksLikeTool = afterBrace.startsWith("\"name\"") || afterBrace.startsWith("\"Name\"")
            if (!looksLikeTool) {
                searchStart = braceIdx + 1
                continue
            }
            val extracted = extractJsonObject(text, braceIdx) ?: run {
                searchStart = braceIdx + 1
                continue
            }
            parseJsonToolCall(extracted.first)?.let { results.add(it) }
            searchStart = extracted.second
        }
        return results
    }

    private fun extractJsonObject(text: String, start: Int): Pair<String, Int>? {
        var i = start
        while (i < text.length && text[i] != '{') i++
        if (i >= text.length) return null
        val objectStart = i
        var depth = 1
        i++
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '"', '\'' -> {
                    val end = skipJsonString(text, i)
                    if (end == null) return null
                    i = end
                    continue
                }
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return Pair(text.substring(objectStart, i + 1), i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    /** Returns index of character after the closing quote, or null if string is unclosed. */
    private fun skipJsonString(text: String, start: Int): Int? {
        if (start >= text.length) return null
        val quote = text[start]
        if (quote != '"' && quote != '\'') return null
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                else -> i++
            }
        }
        return null
    }

    private fun parseCreatePlanArrayFormat(text: String): Pair<String, Map<String, Any>>? {
        val marker = "create_plan"
        val markerIdx = text.indexOf(marker, ignoreCase = true)
        if (markerIdx < 0) return null
        val afterMarker = text.substring(markerIdx + marker.length)
        val colonIdx = afterMarker.indexOf(':')
        if (colonIdx < 0 || afterMarker.substring(0, colonIdx).trim().isNotEmpty()) return null
        var scanStart = colonIdx + 1
        while (scanStart < afterMarker.length && afterMarker[scanStart].isWhitespace()) scanStart++
        if (scanStart >= afterMarker.length) return null
        // Allow optional "steps" before the array (e.g. "create_plan: steps[\"A\", \"B\"]")
        if (afterMarker.regionMatches(scanStart, "steps", 0, 5, ignoreCase = true)) {
            scanStart += 5
            while (scanStart < afterMarker.length && (afterMarker[scanStart].isWhitespace() || afterMarker[scanStart] == '=')) scanStart++
        }
        if (scanStart >= afterMarker.length || afterMarker[scanStart] != '[') return null
        var depth = 0
        var i = scanStart
        while (i < afterMarker.length) {
            when (afterMarker[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val slice = afterMarker.substring(scanStart, i + 1)
                        return try {
                            val arr = JSONArray(slice)
                            val steps = mutableListOf<String>()
                            for (j in 0 until arr.length()) {
                                val item = arr.opt(j)
                                when (item) {
                                    is String -> item.trim().takeIf { it.isNotEmpty() }?.let { steps.add(it) }
                                    is JSONObject -> item.optString("step").trim().takeIf { it.isNotEmpty() }?.let { steps.add(it) }
                                    else -> item?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { steps.add(it) }
                                }
                            }
                            if (steps.isEmpty()) null else Pair("create_plan", mapOf("steps" to steps))
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun parseLabeledJSONToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        var searchStart = 0
        while (true) {
            val colonIdx = text.indexOf(':', searchStart)
            if (colonIdx < 0) break
            val afterColon = colonIdx + 1
            var jsonStart = afterColon
            while (jsonStart < text.length && text[jsonStart].isWhitespace()) jsonStart++
            if (jsonStart >= text.length || text[jsonStart] != '{') {
                searchStart = afterColon
                continue
            }
            val extracted = extractJsonObject(text, jsonStart) ?: run {
                searchStart = afterColon
                continue
            }
            val parsed = parseJsonToolCall(extracted.first)
            if (parsed != null) {
                results.add(parsed)
            } else {
                // Model may output "create_plan: {"steps":[...]}" (args only, no "name"/"args" wrapper). Use label as tool name.
                val labelStart = (colonIdx - 1).downTo(0).firstOrNull { i ->
                    !text[i].isLetterOrDigit() && text[i] != '_'
                }?.let { it + 1 } ?: 0
                val label = text.substring(labelStart, colonIdx).trim()
                if (label.isNotEmpty()) {
                    try {
                        val normalized = lenientJson(extracted.first.trim())
                        val json = JSONObject(normalized)
                        val argsMap = jsonObjectToMap(json)
                        if (argsMap.isNotEmpty()) {
                            results.add(Pair(label, normalizeArgs(argsMap)))
                        }
                    } catch (_: Exception) { /* not valid args JSON */ }
                }
            }
            searchStart = extracted.second
        }
        return results
    }

    private fun parseLabeledQuotedStringToolCalls(text: String): List<Pair<String, Map<String, Any>>> {
        val results = mutableListOf<Pair<String, Map<String, Any>>>()
        var searchStart = 0
        while (true) {
            val colonIdx = text.indexOf(':', searchStart)
            if (colonIdx < 0) break
            val afterColon = colonIdx + 1
            var quoteStart = afterColon
            while (quoteStart < text.length && text[quoteStart].isWhitespace()) quoteStart++
            if (quoteStart >= text.length || text[quoteStart] != '"') {
                searchStart = afterColon
                continue
            }
            val extracted = extractQuotedString(text, quoteStart) ?: run {
                searchStart = afterColon
                continue
            }
            val value = extracted.first
            val endIndex = extracted.second
            var labelStart = colonIdx
            while (labelStart > 0 && (text[labelStart - 1].isLetterOrDigit() || text[labelStart - 1] == '_')) {
                labelStart--
            }
            val label = text.substring(labelStart, colonIdx)
            val argKey = LABELED_QUOTED_STRING_TOOLS[label] ?: run {
                searchStart = afterColon
                continue
            }
            results.add(Pair(label, mapOf(argKey to value)))
            searchStart = endIndex
        }
        return results
    }

    private fun extractQuotedString(text: String, start: Int): Pair<String, Int>? {
        if (start >= text.length || text[start] != '"') return null
        val sb = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' -> {
                    i++
                    if (i < text.length) {
                        sb.append(text[i])
                        i++
                    }
                }
                c == '"' -> return Pair(sb.toString(), i + 1)
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        return null
    }

    private fun stripBareJsonToolCalls(text: String): String {
        val sb = StringBuilder()
        var searchStart = 0
        while (searchStart < text.length) {
            val braceIdx = text.indexOf('{', searchStart)
            if (braceIdx < 0) {
                sb.append(text.substring(searchStart))
                break
            }
            val afterBrace = text.substring(braceIdx + 1).trimStart()
            val looksLikeTool = afterBrace.startsWith("\"name\"") || afterBrace.startsWith("\"Name\"")
            if (!looksLikeTool) {
                sb.append(text.substring(searchStart, braceIdx + 1))
                searchStart = braceIdx + 1
                continue
            }
            val extracted = try {
                extractJsonObject(text, braceIdx)
            } catch (_: Throwable) {
                null
            }
            val parsed = if (extracted != null) {
                try {
                    parseJsonToolCall(extracted.first)
                } catch (_: Throwable) {
                    null
                }
            } else null
            if (extracted == null || parsed == null) {
                sb.append(text.substring(searchStart, braceIdx + 1))
                searchStart = braceIdx + 1
                continue
            }
            sb.append(text.substring(searchStart, braceIdx))
            searchStart = extracted.second
        }
        return sb.toString().trim()
    }

    private fun stripTaggedToolCalls(text: String): String {
        val sb = StringBuilder()
        var searchStart = 0
        while (true) {
            val openIndex = text.indexOf(OPEN_TAG, searchStart)
            if (openIndex == -1) {
                sb.append(text.substring(searchStart))
                break
            }
            sb.append(text.substring(searchStart, openIndex))
            val contentStart = openIndex + OPEN_TAG.length
            val closeIndex = text.indexOf(CLOSE_TAG, contentStart)
            if (closeIndex != -1) {
                searchStart = closeIndex + CLOSE_TAG.length
            } else {
                val extracted = extractJsonObject(text, contentStart)
                if (extracted != null) {
                    searchStart = extracted.second
                } else {
                    searchStart = contentStart
                    break
                }
            }
        }
        return sb.toString().trim()
    }

    private fun stripCreatePlanArrayFormat(text: String): String {
        val marker = "create_plan"
        val markerIdx = text.indexOf(marker, ignoreCase = true)
        if (markerIdx < 0) return text
        val afterMarker = text.substring(markerIdx + marker.length)
        val colonIdx = afterMarker.indexOf(':')
        if (colonIdx < 0 || afterMarker.substring(0, colonIdx).trim().isNotEmpty()) return text
        var scanStart = colonIdx + 1
        while (scanStart < afterMarker.length && afterMarker[scanStart].isWhitespace()) scanStart++
        if (scanStart >= afterMarker.length || afterMarker[scanStart] != '[') return text
        var depth = 0
        var i = scanStart
        while (i < afterMarker.length) {
            when (afterMarker[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        val prefix = text.substring(0, text.indexOf(marker, ignoreCase = true))
                        val blockEnd = markerIdx + marker.length + i + 1
                        val suffix = if (blockEnd < text.length) text.substring(blockEnd) else ""
                        return (prefix + suffix).trim()
                    }
                }
            }
            i++
        }
        return text
    }

    private fun stripLabeledJSONToolCallFormat(text: String): String {
        var result = text
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < result.length) {
                val colonIdx = result.indexOf(':', i)
                if (colonIdx < 0) break
                val afterColon = colonIdx + 1
                var jsonStart = afterColon
                while (jsonStart < result.length && result[jsonStart].isWhitespace()) jsonStart++
                if (jsonStart < result.length && result[jsonStart] == '{') {
                    val extracted = extractJsonObject(result, jsonStart)
                    if (extracted != null) {
                        try {
                            val json = JSONObject(extracted.first)
                            val name = json.optString("name")
                            var labelStart = colonIdx
                            while (labelStart > 0 && (result[labelStart - 1].isLetterOrDigit() || result[labelStart - 1] == '_')) {
                                labelStart--
                            }
                            val label = result.substring(labelStart, colonIdx)
                            if (label == name) {
                                var removeStart = labelStart
                                if (labelStart > 0 && (result[labelStart - 1] == '\n' || result[labelStart - 1] == '\r')) {
                                    removeStart = labelStart - 1
                                }
                                result = result.substring(0, removeStart) + result.substring(extracted.second)
                                changed = true
                                break
                            }
                        } catch (_: Exception) { }
                    }
                }
                i = afterColon
            }
            if (!changed) break
        }
        return result
    }

    private fun stripLabeledQuotedStringToolCallFormat(text: String): String {
        var result = text
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < result.length) {
                val colonIdx = result.indexOf(':', i)
                if (colonIdx < 0) break
                val afterColon = colonIdx + 1
                var quoteStart = afterColon
                while (quoteStart < result.length && result[quoteStart].isWhitespace()) quoteStart++
                if (quoteStart < result.length && result[quoteStart] == '"') {
                    val extracted = extractQuotedString(result, quoteStart)
                    if (extracted != null) {
                        var labelStart = colonIdx
                        while (labelStart > 0 && (result[labelStart - 1].isLetterOrDigit() || result[labelStart - 1] == '_')) {
                            labelStart--
                        }
                        val label = result.substring(labelStart, colonIdx)
                        if (label in LABELED_QUOTED_STRING_TOOLS) {
                            var removeStart = labelStart
                            if (labelStart > 0 && (result[labelStart - 1] == '\n' || result[labelStart - 1] == '\r')) {
                                removeStart = labelStart - 1
                            }
                            result = result.substring(0, removeStart) + result.substring(extracted.second)
                            changed = true
                            break
                        }
                    }
                }
                i = afterColon
            }
            if (!changed) break
        }
        return result
    }
}
