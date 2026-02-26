package com.sessions_ai.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorToolTest {

    private val tool = CalculatorTool()

    @Test
    fun name_isCalculator() {
        assertEquals("calculator", tool.name)
    }

    @Test
    fun execute_validExpression_returnsResult() = runBlocking {
        val result = tool.execute(mapOf("expression" to "2 + 3"))
        assertTrue(result.contains("5"))
    }

    @Test
    fun execute_expressionWithMultiplication_returnsResult() = runBlocking {
        val result = tool.execute(mapOf("expression" to "2 + 3 * 4"))
        assertTrue(result.contains("14"))
    }

    @Test
    fun execute_percentOf_returnsResult() = runBlocking {
        val result = tool.execute(mapOf("expression" to "15 * 240 / 100"))
        assertTrue(result.contains("36"))
    }

    @Test
    fun execute_missingExpression_returnsError() = runBlocking {
        val result = tool.execute(emptyMap())
        assertTrue(result.lowercase().contains("expression") || result.lowercase().contains("missing"))
    }

    @Test
    fun execute_invalidExpression_returnsError() = runBlocking {
        val result = tool.execute(mapOf("expression" to "foo bar"))
        assertFalse(result.isEmpty())
        assertTrue(
            result.lowercase().contains("error") || result.lowercase().contains("invalid") || !result.contains("Result:")
        )
    }

    @Test
    fun execute_divisionByZero_returnsErrorOrHandled() = runBlocking {
        val result = tool.execute(mapOf("expression" to "1 / 0"))
        assertFalse(result.isEmpty())
        assertTrue(result.contains("infinit") || result.lowercase().contains("error") || result.contains("Result:"))
    }
}
