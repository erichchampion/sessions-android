package com.sessions_ai

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class ChatOrchestratorTest {

    private lateinit var llmService: LLMService
    private lateinit var orchestrator: ChatOrchestrator

    @Before
    fun setup() {
        llmService = mock(LLMService::class.java)
        orchestrator = ChatOrchestrator(llmService)
    }

    @Test
    fun testMistralTemplateFormatting() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "Hi there"),
            ChatMessage(ChatMessage.Role.USER, "How are you?")
        )
        
        val template = orchestrator.buildPromptTemplate(messages, ChatTemplateFormat.MISTRAL)
        val expected = "<s>[INST]Hello[/INST]Hi there</s>\n[INST]How are you?[/INST]"
        
        // My simple templater added newlines or not based on the token definition. 
        // Let's refine the test based on actual Tokens defined.
        val actual = orchestrator.buildPromptTemplate(messages, ChatTemplateFormat.MISTRAL)
        
        // Mistral tokens: beginSequence="<s>", instructStart="[INST]", instructEnd="[/INST]", endOfTurn="</s>"
        // Actual generated sequence should be:
        // <s>[INST]Hello[/INST]Hi there</s>[INST]How are you?[/INST]
        assertEquals("<s>[INST]Hello[/INST]Hi there</s>[INST]How are you?[/INST]", actual)
    }

    @Test
    fun testQwen3TemplateFormattingWithSystem() {
        val messages = listOf(
            ChatMessage(ChatMessage.Role.USER, "What is 2+2?")
        )
        val system = "You are a helpful assistant."
        
        val actual = orchestrator.buildPromptTemplate(messages, ChatTemplateFormat.QWEN3, systemPrompt = system)
        val expected = "<|im_start|>system\nYou are a helpful assistant.<|im_end|>\n<|im_start|>user\nWhat is 2+2?<|im_end|>\n<|im_start|>assistant\n"
        assertEquals(expected, actual)
    }
}
