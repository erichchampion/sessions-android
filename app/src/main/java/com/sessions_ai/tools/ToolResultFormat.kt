package com.sessions_ai.tools

/**
 * Consistent formatting for tool results appended to the prompt. Tools return raw content;
 * ChatOrchestrator wraps with "Result of toolName: content" or "Result of toolName: Error – message".
 * Matches iOS ToolResultFormat for parity.
 */
object ToolResultFormat {

    /** Formats a successful tool result for the prompt. */
    fun result(toolName: String, content: String): String =
        "Result of $toolName: $content"

    /** Formats a tool error for the prompt. Uses en-dash (–) to match iOS. */
    fun error(toolName: String, message: String): String =
        "Result of $toolName: Error – $message"
}
