package com.sessions_ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LLMModelCatalogTest {

    @Test
    fun testFormatFileSizeGb() {
        val size = 5L * 1024 * 1024 * 1024 // 5 GB
        val formatted = LLMModelConfiguration.formatFileSize(size, approximate = true)
        assertEquals("~5.0 GB", formatted)
    }

    @Test
    fun testFormatFileSizeMbExact() {
        val size = 500L * 1024 * 1024 // 500 MB
        val formatted = LLMModelConfiguration.formatFileSize(size, approximate = false)
        assertEquals("500 MB", formatted)
    }

    @Test
    fun testDefaultModelExistsInCatalog() {
        val defaultId = LLMModelCatalog.defaultModelID
        val config = LLMModelCatalog.getConfiguration(defaultId)
        assertNotNull("Default model configuration should exist in the catalog", config)
        assertEquals(defaultId, config?.id)
    }
    
    @Test
    fun testChatTemplateTokens() {
        val qwenFormat = ChatTemplateFormat.QWEN3
        val tokens = qwenFormat.tokens
        
        assertEquals("<|im_start|>user\n", tokens.userHeader)
        assertEquals("<|im_end|>\n", tokens.endOfTurn)
        
        val stopSequences = qwenFormat.stopSequences
        assert(stopSequences.contains("<|im_end|>"))
    }
}
