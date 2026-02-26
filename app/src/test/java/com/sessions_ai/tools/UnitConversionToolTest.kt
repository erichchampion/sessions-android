package com.sessions_ai.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConversionToolTest {

    private val tool = UnitConversionTool()

    @Test
    fun name_isUnitConversion() {
        assertEquals("unit_conversion", tool.name)
    }

    @Test
    fun execute_milesToKm_returnsApproximateResult() = runBlocking {
        val result = tool.execute(mapOf("value" to 5, "from_unit" to "miles", "to_unit" to "km"))
        assertTrue(result.contains("Result:"))
        assertTrue(result.contains("km"))
        assertTrue(result.contains("8") || result.contains("8.0")) // ~8.05
    }

    @Test
    fun execute_fahrenheitToCelsius_returnsApproximateResult() = runBlocking {
        val result = tool.execute(mapOf("value" to 100, "from_unit" to "fahrenheit", "to_unit" to "celsius"))
        assertTrue(result.contains("Result:"))
        assertTrue(result.contains("celsius"))
        assertTrue(result.contains("37") || result.contains("37.8"))
    }

    @Test
    fun execute_missingValue_returnsError() = runBlocking {
        val result = tool.execute(mapOf("from_unit" to "miles", "to_unit" to "km"))
        assertTrue(result.lowercase().contains("error"))
        assertTrue(result.contains("value"))
    }
}
