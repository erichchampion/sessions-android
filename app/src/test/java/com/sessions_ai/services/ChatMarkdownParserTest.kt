package com.sessions_ai.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMarkdownParserTest {

    @Test
    fun parse_empty_returnsEmpty() {
        assertEquals(emptyList<ChatTurn>(), ChatMarkdownParser.parse(""))
        assertEquals(emptyList<ChatTurn>(), ChatMarkdownParser.parse("   \n\n  "))
    }

    @Test
    fun parse_singleUser_returnsOneTurn() {
        val md = "## User\n\nHello"
        val turns = ChatMarkdownParser.parse(md)
        assertEquals(1, turns.size)
        assertEquals(ChatTurn.Role.USER, turns[0].role)
        assertEquals("Hello", turns[0].content)
    }

    @Test
    fun parse_userAndAssistant_returnsTwoTurns() {
        val md = "## User\n\nHi\n\n## Assistant\n\nHi there!"
        val turns = ChatMarkdownParser.parse(md)
        assertEquals(2, turns.size)
        assertEquals(ChatTurn.Role.USER, turns[0].role)
        assertEquals("Hi", turns[0].content)
        assertEquals(ChatTurn.Role.ASSISTANT, turns[1].role)
        assertEquals("Hi there!", turns[1].content)
    }

    @Test
    fun parse_stripsChatPrefix() {
        val md = "## User\n\nChat: What is 2+2?\n\n## Assistant\n\n4"
        val turns = ChatMarkdownParser.parse(md)
        assertEquals(2, turns.size)
        assertEquals("What is 2+2?", turns[0].content)
        assertEquals(UserRequestType.CHAT, turns[0].userRequestType)
    }

    @Test
    fun parse_stripsImagePrefix() {
        val md = "## User\n\nImage: a cat\n\n## Assistant\n\nHere is a cat."
        val turns = ChatMarkdownParser.parse(md)
        assertEquals("a cat", turns[0].content)
        assertEquals(UserRequestType.IMAGE, turns[0].userRequestType)
    }

    @Test
    fun parse_multipleTurns_preservesOrder() {
        val md = "## User\n\nFirst\n\n## Assistant\n\nFirst reply\n\n## User\n\nSecond\n\n## Assistant\n\nSecond reply"
        val turns = ChatMarkdownParser.parse(md)
        assertEquals(4, turns.size)
        assertEquals("First", turns[0].content)
        assertEquals("First reply", turns[1].content)
        assertEquals("Second", turns[2].content)
        assertEquals("Second reply", turns[3].content)
    }

    @Test
    fun parse_normalizesLineEndings() {
        val md = "## User\r\n\r\nHi\r\n## Assistant\r\n\r\nBye"
        val turns = ChatMarkdownParser.parse(md)
        assertEquals(2, turns.size)
        assertEquals("Hi", turns[0].content)
        assertEquals("Bye", turns[1].content)
    }
}
