package com.sessions_ai.tools

/**
 * A tool that the LLM can invoke by name with optional arguments.
 */
interface Tool {
    val name: String
    val description: String
    val schema: Map<String, Any> // Simplified JSON Schema mapping for now
    
    suspend fun execute(args: Map<String, Any>): String
}
