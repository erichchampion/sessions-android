package com.sessions_ai.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DuckDuckGoSearchToolTest {

    @Test
    fun testWebSearchToolEmptyArg() = runBlocking {
        val tool = DuckDuckGoSearchTool()
        val result = tool.execute(emptyMap())
        assertTrue(result.contains("Error: missing \"query\""))
    }
}
