package com.sessions_ai

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ChatOrchestratorTest {

    private lateinit var llmService: LLMService
    private lateinit var orchestrator: ChatOrchestrator

    @Before
    fun setup() {
        llmService = mock(LLMService::class.java)
        orchestrator = ChatOrchestrator(llmService)
    }

    @Test
    fun testGenerateReplyDelegatesToBuilderAndService() {
        runBlocking {
        // Arrange
        `when`(llmService.isModelLoaded).thenReturn(true)
        val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
        val systemPrompt = "System logic"
        val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!
        
        val expectedPrompt = MultiTurnPromptBuilder.build(messages, systemPrompt, modelConfig.chatTemplate)
        
        `when`(llmService.generateStream(
            expectedPrompt, 
            modelConfig.defaultMaxTokens, 
            modelConfig.chatTemplate.stopSequences, 
            modelConfig.defaultTemperature
        )).thenReturn(flowOf("reply token"))

        // Act
        val result = orchestrator.generateReply(messages, modelConfig, systemPrompt).toList()

        // Assert
        assertEquals(listOf("reply token"), result)
        verify(llmService).generateStream(
            expectedPrompt, 
            modelConfig.defaultMaxTokens, 
            modelConfig.chatTemplate.stopSequences, 
            modelConfig.defaultTemperature
        )
    }
}
}
