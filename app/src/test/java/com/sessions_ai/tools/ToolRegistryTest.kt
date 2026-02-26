package com.sessions_ai.tools

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun registerAndLookup_returnsTool() {
        val registry = ToolRegistry.shared
        val stub = StubTool(name = "test_stub", description = "A stub", result = "ok")
        registry.register(stub)
        try {
            assertEquals("test_stub", registry.tool(named = "test_stub")?.name)
        } finally {
            registry.unregister("test_stub")
        }
    }

    @Test
    fun lookup_returnsNullForUnregistered() {
        val registry = ToolRegistry.shared
        assertNull(registry.tool(named = "nonexistent_tool_xyz"))
    }

    @Test
    fun execute_callsToolAndReturnsResult() = runBlocking {
        val registry = ToolRegistry.shared
        val stub = StubTool(name = "test_echo", description = "Echo", result = "hello")
        registry.register(stub)
        try {
            val result = registry.execute(name = "test_echo", args = emptyMap())
            assertEquals("hello", result)
        } finally {
            registry.unregister("test_echo")
        }
    }

    @Test
    fun execute_throwsWhenToolNotFound() = runBlocking {
        val registry = ToolRegistry.shared
        var thrown: Throwable? = null
        try {
            registry.execute(name = "missing_tool_xyz", args = emptyMap())
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue(thrown is IllegalArgumentException)
        assertTrue(thrown?.message?.contains("Tool not found") == true)
    }

    private class StubTool(
        override val name: String,
        override val description: String,
        private val result: String
    ) : Tool {
        override val schema: Map<String, Any> = emptyMap()
        override suspend fun execute(args: Map<String, Any>): String = result
    }
}
