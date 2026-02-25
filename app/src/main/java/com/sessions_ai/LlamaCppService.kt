package com.sessions_ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import android.util.Log

class LlamaCppService : LLMService {
    
    companion object {
        val shared = LlamaCppService()
    }
    
    private val engine = SessionsAIEngine()
    private var isInitialized = false
    
    override var isModelLoaded: Boolean = false
        private set

    fun initialize(context: Context) {
        if (!isInitialized) {
            engine.init(context.applicationInfo.nativeLibraryDir)
            isInitialized = true
        }
    }

    override suspend fun loadModel(modelUrl: String, contextSize: Int, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            onProgress(0.1f)
            val result = engine.load(modelUrl)
            if (result != 0) {
                isModelLoaded = false
                throw LLMServiceError.InvalidInput("Failed to load model. Error code: $result")
            } else {
                isModelLoaded = true
                onProgress(1.0f)
            }
        }
    }

    override fun unloadModel() {
        if (isModelLoaded) {
            engine.unload()
            isModelLoaded = false
        }
    }

    override suspend fun generateStream(
        prompt: String,
        maxTokens: Int,
        stopSequences: List<String>,
        temperature: Double
    ): Flow<String> = flow {
        if (!isModelLoaded) {
            throw LLMServiceError.ModelNotLoaded("Cannot generate, model not loaded.")
        }
        
        val processResult = engine.processPrompt(prompt, maxTokens)
        if (processResult != 0) {
            throw LLMServiceError.InvalidInput("Failed to process prompt. Error code: $processResult")
        }

        while (coroutineContext.isActive) {
            val token = engine.generateNextToken()
            if (token == null) {
                break
            }
            emit(token)
        }
    }.flowOn(Dispatchers.IO)
}
