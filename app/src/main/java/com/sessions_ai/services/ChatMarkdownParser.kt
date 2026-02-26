package com.sessions_ai.services

/**
 * Parses chat markdown (## User / ## Assistant blocks) into a list of turns.
 * Matches iOS ChatMarkdownParser behavior.
 */
object ChatMarkdownParser {

    private val headingPattern = Regex("## (User|Assistant)\\s*\n\n", RegexOption.IGNORE_CASE)
    private const val CHAT_PREFIX = "Chat: "
    private const val IMAGE_PREFIX = "Image: "

    /**
     * Splits markdown content into ordered user/assistant turns.
     * Expects headings "## User" and "## Assistant" as block delimiters.
     * Strips optional "Chat:" / "Image:" prefix from user content.
     */
    fun parse(markdown: String): List<ChatTurn> {
        val normalized = markdown.replace("\r\n", "\n").replace("\r", "\n")
        val trimmed = normalized.trim()
        if (trimmed.isEmpty()) return emptyList()

        val matches = headingPattern.findAll(trimmed).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { i, match ->
            val roleStr = match.groupValues[1]
            val role = if (roleStr.equals("user", ignoreCase = true)) ChatTurn.Role.USER else ChatTurn.Role.ASSISTANT
            val start = match.range.last + 1
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else trimmed.length
            var content = trimmed.substring(start, end).trim()

            var userRequestType: UserRequestType? = null
            if (role == ChatTurn.Role.USER) {
                userRequestType = when {
                    content.startsWith(CHAT_PREFIX) -> {
                        content = content.removePrefix(CHAT_PREFIX).trim()
                        UserRequestType.CHAT
                    }
                    content.startsWith(IMAGE_PREFIX) -> {
                        content = content.removePrefix(IMAGE_PREFIX).trim()
                        UserRequestType.IMAGE
                    }
                    else -> UserRequestType.CHAT
                }
            }

            ChatTurn(role = role, content = content, userRequestType = userRequestType)
        }
    }
}

enum class UserRequestType { CHAT, IMAGE }

data class ChatTurn(
    val role: ChatTurn.Role,
    val content: String,
    val userRequestType: UserRequestType? = null
) {
    enum class Role { USER, ASSISTANT }
}
