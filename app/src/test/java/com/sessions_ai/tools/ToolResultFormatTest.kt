package com.sessions_ai.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolResultFormatTest {

    @Test
    fun result_producesExpectedFormat() {
        val formatted = ToolResultFormat.result("calculator", "Result: 42")
        assertEquals("Result of calculator: Result: 42", formatted)
    }

    @Test
    fun error_producesExpectedFormat() {
        val formatted = ToolResultFormat.error("web_search", "Network timeout")
        assertEquals("Result of web_search: Error – Network timeout", formatted)
    }
}
