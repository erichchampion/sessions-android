package com.sessions_ai.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.net.HttpURLConnection
import java.net.URL

class PageContentProvider {

    companion object {
        const val MAX_RESPONSE_BYTES = 5_000_000
        const val TIMEOUT_MS = 15000

        private val DISCARD_SELECTOR = listOf(
            "script", "style", "link",
            "nav", "header", "footer", "aside",
            "ins", "iframe",
            "button", "form", "input", "select",
            "[style*='display:none']", "[style*='display: none']", "[style*='visibility:hidden']",
            ".hidden", ".sr-only", "[hidden]"
        ).joinToString(", ")
    }

    suspend fun fetchHTML(urlString: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5")

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            throw Exception("HTTP $responseCode")
        }

        val inputStream = connection.inputStream
        val buffer = ByteArray(MAX_RESPONSE_BYTES)
        var bytesRead = 0
        var truncated = false

        while (bytesRead < MAX_RESPONSE_BYTES) {
            val read = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
            if (read == -1) break
            bytesRead += read
        }
        
        if (inputStream.read() != -1) {
            truncated = true
        }

        val htmlString = String(buffer, 0, bytesRead, Charsets.UTF_8)
        Pair(htmlString, truncated)
    }

    fun extractSemanticContent(html: String, baseUrl: String): String {
        return try {
            val doc = Jsoup.parse(html, baseUrl)
            doc.select(DISCARD_SELECTOR).remove()

            val main = doc.select("main").first()
            if (main != null) {
                val text = extractTextPreservingLinks(main).trim()
                if (text.isNotEmpty()) return collapseWhitespace(text)
            }

            val articleEls = doc.select("article")
            if (articleEls.isNotEmpty()) {
                val parts = articleEls.mapNotNull { 
                    val t = extractTextPreservingLinks(it).trim()
                    if (t.isNotEmpty()) t else null
                }
                if (parts.isNotEmpty()) return collapseWhitespace(parts.joinToString("\n\n"))
            }

            val sectionEls = doc.select("section")
            if (sectionEls.isNotEmpty()) {
                val parts = sectionEls.mapNotNull {
                    val t = extractTextPreservingLinks(it).trim()
                    if (t.isNotEmpty()) t else null
                }
                if (parts.isNotEmpty()) return collapseWhitespace(parts.joinToString("\n\n"))
            }

            val body = doc.select("body").first()
            if (body != null) {
                val text = extractTextPreservingLinks(body).trim()
                if (text.isNotEmpty()) return collapseWhitespace(text)
            }

            val text = extractTextPreservingLinks(doc).trim()
            collapseWhitespace(text)
            
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractTextPreservingLinks(element: Element): String {
        val sb = java.lang.StringBuilder()
        for (node in element.childNodes()) {
            if (node is TextNode) {
                sb.append(node.wholeText)
            } else if (node is Element) {
                if (node.tagName().lowercase() == "a") {
                    val href = node.absUrl("href").ifEmpty { node.attr("href") }
                    val inner = extractTextPreservingLinks(node)
                    if (href.isNotBlank()) {
                        sb.append("$inner ($href)")
                    } else {
                        sb.append(inner)
                    }
                } else {
                    sb.append(extractTextPreservingLinks(node))
                }
            }
        }
        return sb.toString()
    }

    private fun collapseWhitespace(s: String): String {
        return s.split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" ")
    }
}
