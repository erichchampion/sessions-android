package com.sessions_ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ChatMessage(val role: Role, val content: String) {
    enum class Role {
        USER, ASSISTANT, SYSTEM
    }
}

class ChatOrchestrator(private val llmService: LLMService) {



    suspend fun generateReply(
        messages: List<ChatMessage>,
        modelConfig: LLMModelConfiguration,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        if (!llmService.isModelLoaded) {
            throw LLMServiceError.ModelNotLoaded("Model is not loaded.")
        }

        val prompt = MultiTurnPromptBuilder.build(messages, systemPrompt, modelConfig.chatTemplate)
        
        llmService.generateStream(
            prompt = prompt,
            maxTokens = modelConfig.defaultMaxTokens,
            stopSequences = modelConfig.chatTemplate.stopSequences,
            temperature = modelConfig.defaultTemperature
        ).collect { token ->
            emit(token)
        }
    }
}
