package com.sessions_ai

import android.util.Log
import com.sessions_ai.rag.DocumentChunk
import com.sessions_ai.rag.KnowledgeBaseStore
import com.sessions_ai.tools.PlanningStore
import com.sessions_ai.tools.ToolRegistry
import com.sessions_ai.tools.ToolResultFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

data class ChatMessage(val role: Role, val content: String) {
    enum class Role {
        USER, ASSISTANT, SYSTEM
    }
}

class ChatOrchestrator(
    private val llmService: LLMService,
    private val knowledgeBaseStore: KnowledgeBaseStore? = null
) {

    suspend fun generateReply(
        messages: List<ChatMessage>,
        modelConfig: LLMModelConfiguration,
        systemPrompt: String? = null,
        currentChatPath: String? = null
    ): Flow<String> = flow {
        if (!llmService.isModelLoaded) {
            throw LLMServiceError.ModelNotLoaded("Model is not loaded.")
        }
        PlanningStore.shared.setCurrentChat(currentChatPath)

        val toolRegistry = com.sessions_ai.tools.ToolRegistry.shared
        var currentMessages = messages.toList()
        var turnCount = 0

        while (turnCount < 5) {
            turnCount++
            try {
                if (turnCount > 1) Log.d("ChatOrchestrator", "follow-up turn $turnCount (after tool results)")
            } catch (_: Throwable) { /* no-op */ }
            val lastUserMessage = currentMessages.lastOrNull { it.role == ChatMessage.Role.USER }?.content
            val toolInstructions = ToolRegistry.shared.toolInstructionsForPrompt()
            var baseSystemPrompt = systemPrompt?.let { "$it\n\n$toolInstructions" } ?: toolInstructions
            if (ComplexTaskDetector.isComplexTask(lastUserMessage)) {
                baseSystemPrompt += "\n\nThis is a multi-step task. You MUST call create_plan first with concrete steps (e.g. Research topic, Draft outline, Write section 1, …), then use other tools and update_step as you go."
            }
            val retrievedContext = withContext(Dispatchers.IO) {
                retrieveRAGContext(currentMessages, currentChatPath)
            }
            if (retrievedContext.isNotEmpty()) {
                baseSystemPrompt = "$baseSystemPrompt\n\nRetrieved context (use when relevant to answer):\n$retrievedContext"
            }
            if (turnCount > 1) {
                val planPrefix = PlanningStore.shared.planSummaryWithUpdateInstructionsFor(currentChatPath)
                if (planPrefix != null) {
                    baseSystemPrompt = "$baseSystemPrompt\n\n$planPrefix"
                }
            }
            val fullSystemPrompt = baseSystemPrompt
            val prompt = MultiTurnPromptBuilder.build(currentMessages, fullSystemPrompt, modelConfig.chatTemplate)
            try {
                Log.d("ChatOrchestrator", "prompt prefix (tool instructions should appear here): ${prompt.take(1000)}")
            } catch (_: Throwable) { /* no-op when Log is not mocked (e.g. unit tests) */ }

            if (turnCount > 1) {
                emit("\n\n")
                emit("_Generating response…_")
                emit("\n\n")
            }
            var fullResponse = ""
            llmService.generateStream(
                prompt = prompt,
                maxTokens = modelConfig.defaultMaxTokens,
                stopSequences = modelConfig.chatTemplate.stopSequences,
                temperature = modelConfig.defaultTemperature
            ).collect { token ->
                fullResponse += token
                emit(token)
            }

            // If this is a follow-up turn after tools and the model returned nothing, show a short fallback
            if (fullResponse.isBlank() && turnCount > 1) {
                emit("\n(Using the results above.)")
            }

            val toolCallsArray = com.sessions_ai.tools.ToolCallParser.parseToolCalls(fullResponse)
            try {
                if (toolCallsArray.isNotEmpty()) {
                    Log.d("ChatOrchestrator", "parsed ${toolCallsArray.size} tool call(s): ${toolCallsArray.map { it.first }}")
                } else if (fullResponse.isNotBlank()) {
                    val suffix = fullResponse.takeLast(600).replace(Regex("[\\x00-\\x1F]"), " ")
                    val i = fullResponse.indexOf('"').let { if (it >= 0) it else fullResponse.indexOf('\u201C').let { j -> if (j >= 0) j else fullResponse.indexOf('\u201D') } }
                    val quoteInfo = if (i >= 0) " firstQuoteAt=$i code=0x${fullResponse[i].code.toString(16)}" else " noQuote"
                    Log.d("ChatOrchestrator", "no tool calls parsed. response length=${fullResponse.length}, suffix=[$suffix]$quoteInfo")
                }
            } catch (_: Throwable) { /* no-op */ }

            if (toolCallsArray.isNotEmpty()) {
                val assistantMsg = ChatMessage(ChatMessage.Role.ASSISTANT, fullResponse)
                currentMessages = currentMessages + assistantMsg

                val toolResults = mutableListOf<String>()
                for (toolCall in toolCallsArray) {
                    val name = toolCall.first
                    val argsMap = toolCall.second

                    val tool = toolRegistry.tool(name)
                    if (tool != null) {
                        try {
                            val result = withContext(Dispatchers.IO) { tool.execute(argsMap) }
                            toolResults.add(ToolResultFormat.result(name, result))
                        } catch (e: Exception) {
                            toolResults.add(ToolResultFormat.error(name, e.message ?: "Unknown error"))
                        }
                    } else {
                        toolResults.add(ToolResultFormat.result(name, "Tool not found."))
                    }
                }

                if (toolResults.isNotEmpty()) {
                    val combinedResults = toolResults.joinToString("\n")
                    val toolMsg = ChatMessage(ChatMessage.Role.USER, combinedResults)
                    currentMessages = currentMessages + toolMsg

                    currentChatPath?.let { path ->
                        knowledgeBaseStore?.let { store ->
                            val sessionDocId = "session_${path.hashCode().toLong().and(0x7FFFFFFFL).toString(16)}"
                            withContext(Dispatchers.IO) {
                                store.addDocument(sessionDocId, "", "Session", combinedResults)
                            }
                        }
                    }

                    emit("\n\n---\n\n")
                    emit(combinedResults)
                    emit("\n\n---\n\n")
                    continue
                } else {
                    break
                }
            } else {
                break
            }
        }
    }

    private fun retrieveRAGContext(messages: List<ChatMessage>, currentChatPath: String?): String {
        val store = knowledgeBaseStore ?: return ""
        if (!store.hasDocuments()) return ""
        val lastUserMessage = messages.lastOrNull { msg -> msg.role == ChatMessage.Role.USER }?.content ?: return ""
        val query = lastUserMessage.trim()
        if (query.isEmpty()) return ""

        val ragChunks: List<DocumentChunk> = store.retrieve(query, 5)
        val sessionChunks: List<DocumentChunk> = currentChatPath?.let { path ->
            val sessionDocId = "session_${path.hashCode().toLong().and(0x7FFFFFFFL).toString(16)}"
            store.retrieveChunksForDocument(sessionDocId)
        } ?: emptyList()
        val allChunks: List<DocumentChunk> = (sessionChunks + ragChunks).distinctBy { chunk -> chunk.id }
        return formatChunkContext(allChunks)
    }

    private fun formatChunkContext(chunks: List<DocumentChunk>): String =
        chunks.joinToString("\n\n") { "[${it.title}] ${it.text}" }
}
