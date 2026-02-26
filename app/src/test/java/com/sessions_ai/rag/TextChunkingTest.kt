package com.sessions_ai.rag

import org.junit.Assert.assertEquals
import org.junit.Test

class TextChunkingTest {

    @Test
    fun chunk_empty_returnsEmpty() {
        assertEquals(emptyList<Pair<Int, String>>(), TextChunking.chunk("", "doc", "src", "title"))
        assertEquals(emptyList<Pair<Int, String>>(), TextChunking.chunk("   \n\n  ", "doc", "src", "title"))
    }

    @Test
    fun chunk_shortParagraph_returnsOne() {
        val result = TextChunking.chunk("Hello world.", "doc", "src", "title")
        assertEquals(1, result.size)
        assertEquals(0, result[0].first)
        assertEquals("Hello world.", result[0].second)
    }

    @Test
    fun chunk_twoParagraphs_returnsTwo() {
        val result = TextChunking.chunk("First.\n\nSecond.", "doc", "src", "title")
        assertEquals(2, result.size)
        assertEquals("First.", result[0].second)
        assertEquals("Second.", result[1].second)
    }

    @Test
    fun chunk_longBlock_splitsWithOverlap() {
        val longText = "a".repeat(TextChunking.maxChunkChars + 100)
        val result = TextChunking.chunk(longText, "doc", "src", "title")
        assert(result.size >= 2)
        assert(result.all { it.second.length <= TextChunking.maxChunkChars })
    }
}
