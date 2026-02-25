package com.sessions_ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.pow

data class WebSearchResult(
    val title: String,
    val snippet: String?,
    val url: String
)

class DuckDuckGoProvider(
    private val maxRetries: Int = 2,
    private val retryDelayMs: Long = 500
) {
    private val searchUrl = "https://html.duckduckgo.com/html/"

    private val titleLinkRegexes = listOf(
        Regex("""<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE),
        Regex("""<a[^>]*href="([^"]+)"[^>]*class="[^"]*result[^"]*"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE)
    )

    private val snippetRegexes = listOf(
        Regex("""<a[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([^<]+)</a>""", RegexOption.IGNORE_CASE),
        Regex("""<span[^>]*class="[^"]*result__snippet[^"]*"[^>]*>([^<]+)</span>""", RegexOption.IGNORE_CASE)
    )

    private val decimalEntityRegex = Regex("""&#(\d+);""")
    private val hexEntityRegex = Regex("""&#x([0-9a-fA-F]+);""", RegexOption.IGNORE_CASE)

    suspend fun search(query: String): List<WebSearchResult> = withContext(Dispatchers.IO) {
        searchWithRetry(query, 0)
    }

    private suspend fun searchWithRetry(query: String, attempt: Int): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$searchUrl?q=$encodedQuery")

        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")

            val responseCode = connection.responseCode
            if (responseCode == 429 || responseCode == 403 || responseCode >= 500) {
                if (attempt < maxRetries) {
                    delay((retryDelayMs * 2.0.pow(attempt)).toLong())
                    return searchWithRetry(query, attempt + 1)
                }
                throw Exception("DuckDuckGo search failed with HTTP $responseCode")
            }

            if (responseCode != 200) {
                throw Exception("DuckDuckGo search failed with HTTP $responseCode")
            }

            val html = connection.inputStream.bufferedReader().use { it.readText() }
            return parseHTMLResults(html)

        } catch (e: Exception) {
            if (attempt < maxRetries) {
                delay((retryDelayMs * 2.0.pow(attempt)).toLong())
                return searchWithRetry(query, attempt + 1)
            }
            throw e
        }
    }

    private fun parseHTMLResults(html: String): List<WebSearchResult> {
        val results = mutableListOf<WebSearchResult>()

        for (regex in titleLinkRegexes) {
            val matches = regex.findAll(html)
            for (match in matches) {
                if (match.groupValues.size < 3) continue

                val urlStringRaw = match.groupValues[1]
                var title = match.groupValues[2]
                    .trim()
                    .replace("\n", " ")
                    .replace("\t", " ")
                title = decodeHTMLEntities(title)
                if (title.isEmpty() || title.length <= 3) continue

                val contextStart = maxOf(0, match.range.first - 200)
                val contextBefore = html.substring(contextStart, match.range.first).lowercase()
                if (isAdResult(contextBefore)) continue

                var urlString = urlStringRaw
                if (urlString.lowercase().contains("duckduckgo.com/y.js") && urlString.lowercase().contains("ad_domain=")) continue

                if (urlString.contains("uddg=")) {
                    val uddgIndex = urlString.indexOf("uddg=")
                    val encodedPart = urlString.substring(uddgIndex + 5).substringBefore("&")
                    val decoded = java.net.URLDecoder.decode(encodedPart, "UTF-8")
                    urlString = decoded
                    if (urlString.lowercase().contains("duckduckgo.com/y.js") && urlString.lowercase().contains("ad_domain=")) continue
                } else if (urlString.startsWith("/l/")) {
                    continue
                }

                if (urlString.startsWith("//")) urlString = "https:$urlString"
                else if (!urlString.startsWith("http://") && !urlString.startsWith("https://")) continue

                var snippet: String? = null
                val searchStart = match.range.last + 1
                val searchEnd = minOf(searchStart + 500, html.length)
                val contextAfter = html.substring(searchStart, searchEnd)

                for (snippetRegex in snippetRegexes) {
                    val snippetMatch = snippetRegex.find(contextAfter)
                    if (snippetMatch != null && snippetMatch.groupValues.size >= 2) {
                        snippet = decodeHTMLEntities(
                            snippetMatch.groupValues[1]
                                .trim()
                                .replace("\n", " ")
                                .replace("\t", " ")
                        )
                        break
                    }
                }

                if (results.none { it.url == urlString }) {
                    results.add(WebSearchResult(title, snippet, urlString))
                }
            }
            if (results.isNotEmpty()) break
        }
        return results
    }

    private fun decodeHTMLEntities(string: String): String {
        var result = string
        val namedEntities = mapOf(
            "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
            "&nbsp;" to " ", "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…"
        )
        for ((entity, replacement) in namedEntities) {
            result = result.replace(entity, replacement, ignoreCase = true)
        }

        result = decimalEntityRegex.replace(result) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull()
            if (code != null && code in 0..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }

        result = hexEntityRegex.replace(result) { matchResult ->
            val hex = matchResult.groupValues[1]
            val code = hex.toIntOrNull(16)
            if (code != null && code in 0..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                matchResult.value
            }
        }

        return result
    }

    private fun isAdResult(context: String): Boolean {
        val adIndicators = listOf(
            "class=\"ad\"", "class='ad'", "class=\"ad-", "class=\"sponsored\"", "class=\"result--ad\"",
            "class=\"result__ad\"", "data-module=\"ad\"", "sponsored link", "advertisement"
        )
        return adIndicators.any { context.contains(it) }
    }
}
