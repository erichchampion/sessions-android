package com.sessions_ai.tools

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches a short summary for a topic from the Wikipedia REST API (no API key).
 * Matches iOS WikipediaTool behavior for parity.
 */
class WikipediaTool : Tool {

    override val name = "wikipedia"
    override val description = "Use for encyclopedic or well-known topics: history, science, biographies, books, geography. Pass \"query\" or \"title\" in args. Do not use for current events or recent news—use web_search instead."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf("query" to mapOf("type" to "string")),
        "required" to listOf("query")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val query = (args["query"] as? String) ?: (args["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: return "Error: missing \"query\" or \"title\" for wikipedia."
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return "Error: missing \"query\" or \"title\" for wikipedia."
        val pageTitle = trimmed.replace(" ", "_")
        return try {
            val encodedTitle = URLEncoder.encode(pageTitle, "UTF-8").replace("+", "%20")
            val url = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            if (code != 200) return "Error: Wikipedia returned $code. Page may not exist."
            val json = conn.inputStream.bufferedReader().readText()
            val obj = JSONObject(json)
            val extract = obj.optString("extract")
            if (extract.isEmpty()) return "Error: could not get summary from Wikipedia (missing or empty extract)."
            val titleStr = obj.optString("title", pageTitle)
            "$titleStr: $extract"
        } catch (e: Exception) {
            "Error: Wikipedia request failed – ${e.message ?: "unknown error"}"
        }
    }
}
