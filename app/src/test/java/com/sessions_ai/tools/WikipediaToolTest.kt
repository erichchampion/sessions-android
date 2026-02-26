package com.sessions_ai.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaToolTest {

    private val tool = WikipediaTool()

    @Test
    fun name_isWikipedia() {
        assertEquals("wikipedia", tool.name)
    }

    @Test
    fun execute_missingQuery_returnsError() = runBlocking {
        val result = tool.execute(emptyMap())
        assertTrue(result.lowercase().contains("error"))
        assertTrue(result.contains("query") || result.contains("title"))
    }

    @Test
    fun execute_blankQuery_returnsError() = runBlocking {
        val result = tool.execute(mapOf("query" to "   "))
        assertTrue(result.lowercase().contains("error"))
    }
}
