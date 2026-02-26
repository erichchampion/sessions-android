package com.sessions_ai

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyDouble
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.stubbing.Answer

class ChatOrchestratorTest {

    private lateinit var llmService: LLMService
    private lateinit var orchestrator: ChatOrchestrator

    @Before
    fun setup() {
        llmService = mock(LLMService::class.java)
        orchestrator = ChatOrchestrator(llmService)
    }

    @After
    fun tearDown() {
        reset(llmService)
    }

    @Test
    fun promptIncludesToolInstructionsWhenBuilt() {
        runBlocking {
            val toolInstructions = com.sessions_ai.tools.ToolRegistry.shared.toolInstructionsForPrompt()
            assertTrue(toolInstructions.contains("web_search"))
            assertTrue(toolInstructions.contains("<tool_call>"))
            `when`(llmService.isModelLoaded).thenReturn(true)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hi"))
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!
            val fullSystemPrompt = "Be helpful.\n\n$toolInstructions"
            val prompt = MultiTurnPromptBuilder.build(messages, fullSystemPrompt, modelConfig.chatTemplate)
            assertTrue(prompt.contains("web_search"))
            assertTrue(prompt.contains("<tool_call>"))
        }
    }

    /** Production path: ViewModel passes no system prompt; orchestrator uses tool instructions only. */
    @Test
    fun promptWithNullSystemPromptContainsToolInstructions() {
        runBlocking {
            val toolInstructions = com.sessions_ai.tools.ToolRegistry.shared.toolInstructionsForPrompt(includeImageGeneration = false)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "What is 2+2?"))
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!
            val fullSystemPrompt = toolInstructions // same as orchestrator when systemPrompt == null
            val prompt = MultiTurnPromptBuilder.build(messages, fullSystemPrompt, modelConfig.chatTemplate)
            assertTrue("Prompt must contain tool instructions so the LLM sees tools", prompt.contains("web_search"))
            assertTrue("Prompt must contain tool call format", prompt.contains("<tool_call>"))
            assertTrue("Prompt must start with system block for Qwen3", prompt.startsWith("<|im_start|>system\n"))
        }
    }

    @Test
    fun testGenerateReplyDelegatesToBuilderAndService() {
        runBlocking {
            `when`(llmService.isModelLoaded).thenReturn(true)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
            val systemPrompt = "System logic"
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!
            val toolInstructions = com.sessions_ai.tools.ToolRegistry.shared.toolInstructionsForPrompt()
            val fullSystemPrompt = "$systemPrompt\n\n$toolInstructions"
            val expectedPrompt = MultiTurnPromptBuilder.build(messages, fullSystemPrompt, modelConfig.chatTemplate)

            `when`(llmService.generateStream(
                expectedPrompt,
                modelConfig.defaultMaxTokens,
                modelConfig.chatTemplate.stopSequences,
                modelConfig.defaultTemperature
            )).thenReturn(flowOf("reply token"))

            val result = orchestrator.generateReply(messages, modelConfig, systemPrompt).toList()

            assertEquals(listOf("reply token"), result)
            verify(llmService).generateStream(
                expectedPrompt,
                modelConfig.defaultMaxTokens,
                modelConfig.chatTemplate.stopSequences,
                modelConfig.defaultTemperature
            )
        }
    }

    @Test
    fun multiTurn_withToolCall_appendsToolResultAndCallsLlmAgain() {
        runBlocking {
            `when`(llmService.isModelLoaded).thenReturn(true)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "What's my plan?"))
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!

            var callCount = 0
            `when`(llmService.generateStream(org.mockito.Mockito.anyString(), anyInt(), anyList(), anyDouble())).thenAnswer(Answer { _ ->
                callCount++
                if (callCount == 1) {
                    flowOf("<tool_call>{\"name\":\"get_plan\",\"args\":{}}</tool_call>")
                } else {
                    flowOf("Final answer.")
                }
            })

            orchestrator.generateReply(messages, modelConfig, null).toList()
            assertEquals(2, callCount)
        }
    }

    @Test
    fun toolNotFound_appendsToolNotFoundAndContinues() {
        runBlocking {
            `when`(llmService.isModelLoaded).thenReturn(true)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Do something"))
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!

            var callCount = 0
            `when`(llmService.generateStream(org.mockito.Mockito.anyString(), anyInt(), anyList(), anyDouble())).thenAnswer(Answer { _ ->
                callCount++
                if (callCount == 1) {
                    flowOf("<tool_call>{\"name\":\"nonexistent_tool_abc\",\"args\":{}}</tool_call>")
                } else {
                    flowOf("I cannot do that.")
                }
            })

            orchestrator.generateReply(messages, modelConfig, null).toList()
            assertEquals(2, callCount)
        }
    }

    @Test
    fun maxTurns_exitsAfterFiveLlmCalls() {
        runBlocking {
            `when`(llmService.isModelLoaded).thenReturn(true)
            val messages = listOf(ChatMessage(ChatMessage.Role.USER, "Loop"))
            val modelConfig = LLMModelCatalog.models[LLMModelID.QWEN3_4B_INSTRUCT]!!

            var callCount = 0
            `when`(llmService.generateStream(org.mockito.Mockito.anyString(), anyInt(), anyList(), anyDouble())).thenAnswer(Answer { _ ->
                callCount++
                flowOf("<tool_call>{\"name\":\"get_plan\",\"args\":{}}</tool_call>")
            })

            orchestrator.generateReply(messages, modelConfig, null).toList()

            assertEquals(5, callCount)
        }
    }
}
