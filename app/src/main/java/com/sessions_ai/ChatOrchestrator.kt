package com.sessions_ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ChatMessage(val role: Role, val content: String) {
    enum class Role {
        USER, ASSISTANT, SYSTEM
    }
}

class ChatOrchestrator(private val llmService: LLMService) {

    fun buildPromptTemplate(
        messages: List<ChatMessage>,
        templateFormat: ChatTemplateFormat,
        systemPrompt: String? = null
    ): String {
        val tokens = templateFormat.tokens
        val sb = StringBuilder()
        
        // Add Begin Sequence if present
        tokens.beginSequence?.let { sb.append(it) }
        
        // Add System prompt if defined
        if (systemPrompt != null && tokens.systemHeader != null) {
            sb.append(tokens.systemHeader)
            sb.append(systemPrompt)
            tokens.endOfTurn?.let { sb.append(it) }
        }
        
        for (message in messages) {
            when (message.role) {
                ChatMessage.Role.USER -> {
                    if (tokens.instructStart != null) {
                        sb.append(tokens.instructStart)
                        sb.append(message.content)
                        tokens.instructEnd?.let { sb.append(it) }
                    } else if (tokens.userHeader != null) {
                        sb.append(tokens.userHeader)
                        sb.append(message.content)
                        tokens.endOfTurn?.let { sb.append(it) }
                    }
                }
                ChatMessage.Role.ASSISTANT -> {
                    if (tokens.instructStart != null) {
                        // For mistral where instruct is for user
                        sb.append(message.content)
                        tokens.endOfTurn?.let { sb.append(it) }
                    } else if (tokens.assistantHeader != null) {
                        sb.append(tokens.assistantHeader)
                        sb.append(message.content)
                        tokens.endOfTurn?.let { sb.append(it) }
                    }
                }
                ChatMessage.Role.SYSTEM -> {} // Checked at the start
            }
        }
        
        // Prepare for the assistant's reply
        if (tokens.instructStart == null && tokens.assistantHeader != null) {
            sb.append(tokens.assistantHeader)
        }
        
        return sb.toString()
    }

    suspend fun generateReply(
        messages: List<ChatMessage>,
        modelConfig: LLMModelConfiguration,
        systemPrompt: String? = null
    ): Flow<String> = flow {
        if (!llmService.isModelLoaded) {
            throw LLMServiceError.ModelNotLoaded("Model is not loaded.")
        }

        val prompt = buildPromptTemplate(messages, modelConfig.chatTemplate, systemPrompt)
        
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
