package com.sessions_ai.tools

class FetchPageTool(private val provider: PageContentProvider = PageContentProvider()) : Tool {
    override val name = "fetch_page"
    override val description = "Get main text from a web page. Use when the user gives a URL to analyze or when you need full article text for a URL found via web_search. Pass \"url\" in args. Prefer web_search for broad queries; use fetch_page when you have a specific URL."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "url" to mapOf("type" to "string"),
            "max_chars" to mapOf("type" to "integer")
        ),
        "required" to listOf("url")
    )

    companion object {
        const val DEFAULT_MAX_CHARS = 20_000
        
        private val STRIP_HTML_REGEX = Regex("</?[a-zA-Z][^>]*>")
        
        fun stripRemainingTags(text: String): String {
            return STRIP_HTML_REGEX.replace(text, " ").trim()
        }
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val urlString = (args["url"] as? String) ?: (args["u"] as? String)
        if (urlString.isNullOrBlank()) {
            return "Error: missing \"url\" argument for fetch_page."
        }
        
        val maxChars = (args["max_chars"] as? Number)?.toInt() ?: DEFAULT_MAX_CHARS
        
        val urlLower = urlString.lowercase().trim()
        if (!urlLower.startsWith("http://") && !urlLower.startsWith("https://")) {
            return "Error: invalid URL. Use http or https only."
        }
        
        return try {
            val (html, wasTruncated) = provider.fetchHTML(urlString.trim())
            var text = provider.extractSemanticContent(html, urlString.trim())
            
            if (text.isEmpty()) {
                return "No main content found at that URL."
            }
            
            text = stripRemainingTags(text)
            
            var result = if (text.length <= maxChars) {
                text
            } else {
                text.substring(0, maxChars) + " ... [truncated]"
            }
            
            if (wasTruncated) {
                result += "\n\n[Page was very long; content above is from the first part of the page.]"
            }
            
            result
        } catch (e: Exception) {
            "Fetch failed: ${e.message}"
        }
    }
}
