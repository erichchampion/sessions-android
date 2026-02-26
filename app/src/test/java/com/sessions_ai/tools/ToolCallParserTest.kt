package com.sessions_ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun parse_emptyString_returnsEmpty() {
        val calls = ToolCallParser.parseToolCalls("")
        assertTrue(calls.isEmpty())
    }

    @Test
    fun parse_noToolCall_returnsEmpty() {
        val text = "Just some text and no tool_call."
        val calls = ToolCallParser.parseToolCalls(text)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun parse_singleToolCall_returnsOne() {
        val text = """Here is the result: <tool_call>{"name": "calendar", "args": {}}</tool_call>"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("calendar", calls[0].first)
        assertTrue(calls[0].second.isEmpty())
    }

    @Test
    fun parse_toolCallWithArgs_parsesArgs() {
        val text = """<tool_call>{"name": "web_search", "args": {"query": "weather"}}</tool_call>"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].first)
        assertEquals("weather", calls[0].second["query"])
    }

    @Test
    fun parse_multipleToolCalls_returnsAll() {
        val text = """
        <tool_call>{"name": "calendar", "args": {}}</tool_call>
        and
        <tool_call>{"name": "web_search", "args": {"q": "test"}}</tool_call>
        """.trimIndent()
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(2, calls.size)
        assertEquals("calendar", calls[0].first)
        assertEquals("web_search", calls[1].first)
        assertEquals("test", calls[1].second["q"])
    }

    @Test
    fun parse_malformedJSON_skipsThatCall() {
        val text = """<tool_call>{"name": "calendar", "args": }</tool_call>"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun scrubText_removesCompleteToolCallBlocks() {
        val text = "Hello <tool_call>{\"name\":\"x\",\"args\":{}}</tool_call> world"
        assertEquals("Hello  world", ToolCallParser.scrubText(text))
    }

    @Test
    fun scrubText_removesTrailingIncompleteToolCall() {
        val text = "prefix <tool_call>{\"name\":\"maps\",\"args\":{\"a\":\"b\"}}"
        assertEquals("prefix", ToolCallParser.scrubText(text))
    }

    @Test
    fun scrubText_preservesTextWhenNoToolCalls() {
        val text = "Just some text."
        assertEquals("Just some text.", ToolCallParser.scrubText(text))
    }

    // Optional parity: missing </tool_call>

    @Test
    fun parse_missingClosingTag_parsesOneCall() {
        val text = """prefix <tool_call>{"name":"maps","args":{"a":"b"}}"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("maps", calls[0].first)
        assertEquals("b", calls[0].second["a"])
    }

    @Test
    fun parse_missingClosingTag_withTrailingText_parsesOneCall() {
        val text = """ <tool_call>{"name":"x","args":{}} more text"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("x", calls[0].first)
    }

    @Test
    fun scrubText_missingClosingTag_removesToolCall() {
        val text = """prefix <tool_call>{"name":"maps","args":{"a":"b"}}"""
        assertEquals("prefix", ToolCallParser.scrubText(text))
    }

    @Test
    fun scrubText_missingClosingTag_withTrailingText_removesOnlyToolCall() {
        val text = """ <tool_call>{"name":"x","args":{}} more text"""
        assertEquals("more text", ToolCallParser.scrubText(text))
    }

    // Optional parity: Qwen-style create_plan array

    @Test
    fun parse_qwenStyleCreatePlanArray_returnsCreatePlanCall() {
        val text = """create_plan:  [{"step":"Identify the rules for using le, la, and les in French.","index":0}, {"step":"Explain when these articles may be shortened as with l'homme.","index":1}]"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("create_plan", calls[0].first)
        @Suppress("UNCHECKED_CAST")
        val steps = calls[0].second["steps"] as? List<String>
        assertTrue(steps != null && steps.size == 2)
        assertEquals("Identify the rules for using le, la, and les in French.", steps!![0])
        assertEquals("Explain when these articles may be shortened as with l'homme.", steps[1])
    }

    // Optional parity: Qwen-style labeled JSON (no <tool_call> tags)

    @Test
    fun parse_qwenStyleLabeledJSON_readAttachedFile_parsesOneCall() {
        val text = """read_attached_file: {"name":"read_attached_file","args":{"index":1,"part":1}}"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("read_attached_file", calls[0].first)
        assertEquals(1, (calls[0].second["index"] as? Number)?.toInt())
        assertEquals(1, (calls[0].second["part"] as? Number)?.toInt())
    }

    @Test
    fun parse_qwenStyleLabeledJSON_withNewline_parsesOneCall() {
        val text = "read_attached_file:\n{\"name\":\"read_attached_file\",\"args\":{\"index\":1,\"part\":1}}"
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("read_attached_file", calls[0].first)
    }

    @Test
    fun scrubText_qwenStyleLabeledJSON_removesToolCall() {
        val text = """read_attached_file: {"name":"read_attached_file","args":{"index":1,"part":1}}"""
        assertEquals("", ToolCallParser.scrubText(text))
    }

    // Optional parity: Qwen shorthand toolName: "string"

    @Test
    fun parse_qwenShorthand_webSearch_parsesOneCall() {
        val text = """web_search: "old and new kingdom dates for ancient egypt""""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].first)
        assertEquals("old and new kingdom dates for ancient egypt", calls[0].second["query"])
    }

    @Test
    fun parse_qwenShorthand_wikipedia_parsesOneCall() {
        val text = """wikipedia: "Ancient Egypt""""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("wikipedia", calls[0].first)
        assertEquals("Ancient Egypt", calls[0].second["query"])
    }

    @Test
    fun scrubText_qwenShorthand_removesToolCall() {
        val text = """web_search: "old and new kingdom dates for ancient egypt""""
        assertEquals("", ToolCallParser.scrubText(text))
    }

    // Bare JSON (no <tool_call> tags, no "toolName:" prefix)

    @Test
    fun parse_bareJsonToolCall_parsesOne() {
        val text = """Here you go: {"name":"web_search","args":{"query":"weather today"}}"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].first)
        assertEquals("weather today", calls[0].second["query"])
    }

    @Test
    fun parse_bareJsonToolCall_withNewlineBeforeJson_parsesOne() {
        val text = """
            {"name":"web_search","args":{"query":"test"}}
        """.trimIndent()
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("web_search", calls[0].first)
    }

    @Test
    fun parse_bareJsonTrailingComma_parsesOne() {
        val text = """<tool_call>{"name":"calculator","args":{"expression":"2+2"},}</tool_call>"""
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("calculator", calls[0].first)
    }

    // Exact string from debug.txt (74 chars) that was logging "no tool calls parsed"
    @Test
    fun parse_exactLogCalculatorToolCall_parsesOne() {
        val text = """<tool_call>{"name":"calculator","args":{"expression":"15*12"}}</tool_call>"""
        assertEquals(74, text.length)
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("calculator", calls[0].first)
        assertEquals("15*12", calls[0].second["expression"])
    }

    // Same content with Unicode curly quotes (U+201C, U+201D) — parser normalizes and parses
    @Test
    fun parse_calculatorToolCallWithUnicodeQuotes_parsesOne() {
        val text = "<tool_call>{\u201Cname\u201D:\u201Ccalculator\u201D,\u201Cargs\u201D:{\u201Cexpression\u201D:\u201C15*12\u201D}}}</tool_call>"
        val calls = ToolCallParser.parseToolCalls(text)
        assertEquals(1, calls.size)
        assertEquals("calculator", calls[0].first)
        assertEquals("15*12", calls[0].second["expression"])
    }

}
