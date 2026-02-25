package com.sessions_ai

import kotlinx.coroutines.flow.Flow

sealed class LLMServiceError : Exception() {
    class InvalidInput(message: String) : LLMServiceError()
    class ContextTooSmall(message: String) : LLMServiceError()
    class ModelNotLoaded(message: String) : LLMServiceError()
}

data class PartialJSONResponse(val text: String, val isFinal: Boolean)

interface LLMService {
    val isModelLoaded: Boolean
    
    suspend fun loadModel(
        modelUrl: String, // Or local file path
        contextSize: Int,
        onProgress: (Float) -> Unit
    )
    
    fun unloadModel()
    
    suspend fun generateStream(
        prompt: String,
        maxTokens: Int,
        stopSequences: List<String>,
        temperature: Double
    ): Flow<String> // Emits token by token
}
