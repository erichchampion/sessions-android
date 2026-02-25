package com.sessions_ai.tools

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class FetchPageToolTest {

    @Test
    fun testFetchPageToolEmptyArg() = runBlocking {
        val tool = FetchPageTool()
        val result = tool.execute(emptyMap())
        assertTrue(result.contains("Error: missing \"url\""))
    }
    
    @Test
    fun testFetchPageToolInvalidProtocol() = runBlocking {
        val tool = FetchPageTool()
        val result = tool.execute(mapOf("url" to "ftp://example.com"))
        assertTrue(result.contains("Error: invalid URL. Use http or https only."))
    }
    
    @Test
    fun testStripRemainingTags() {
        val raw = "Hello <b>world</b> <script>alert(1)</script>"
        val clean = FetchPageTool.stripRemainingTags(raw)
        assertTrue(!clean.contains("<b>"))
    }
}
