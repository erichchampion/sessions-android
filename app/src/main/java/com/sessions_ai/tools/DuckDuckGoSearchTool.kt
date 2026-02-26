package com.sessions_ai.tools

class DuckDuckGoSearchTool(private val provider: DuckDuckGoProvider = DuckDuckGoProvider()) : Tool {
    override val name = "web_search"
    override val description = "Use when the user asks to search the web, look something up, find information, or get current/recent information. Pass \"query\" in args with the search phrase. Prefer this for current events, news, and broad lookups."
    override val schema = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf("type" to "string")
        ),
        "required" to listOf("query")
    )

    override suspend fun execute(args: Map<String, Any>): String {
        val query = (args["query"] as? String) ?: (args["q"] as? String)
        if (query.isNullOrBlank()) {
            return "Error: missing \"query\" argument for DuckDuckGo search."
        }

        return try {
            val results = provider.search(query)
            formatResults(results, max = 8)
        } catch (e: Exception) {
            "DuckDuckGo search failed: ${e.message}"
        }
    }

    private fun formatResults(results: List<WebSearchResult>, max: Int): String {
        if (results.isEmpty()) return "No results found."
        
        val lines = results.take(max).mapIndexed { index, result ->
            val snippet = result.snippet?.let { " - $it" } ?: ""
            "${index + 1}. ${result.title}$snippet (${result.url})"
        }
        return lines.joinToString("\n")
    }
}
