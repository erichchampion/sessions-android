package com.sessions_ai

import com.arm.aichat.InferenceEngine
import com.arm.aichat.internal.InferenceEngineImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

class LlamaCppService : LLMService {
    
    companion object {
        val shared = LlamaCppService()
    }
    
    // We must pass a Context to InferenceEngineImpl.getInstance(context)
    // The safest way is to inject it upon service creation. 
    private var inferenceEngine: InferenceEngine? = null

    fun initialize(context: android.content.Context) {
        if (inferenceEngine == null) {
            inferenceEngine = InferenceEngineImpl.getInstance(context.applicationContext)
        }
    }
    
    override var isModelLoaded: Boolean = false
        private set

    override suspend fun loadModel(modelUrl: String, contextSize: Int, onProgress: (Float) -> Unit) {
        // We will mock progress since InferenceEngine doesn't have a progress callback yet
        onProgress(0.1f)
        try {
            inferenceEngine!!.loadModel(modelUrl)
            
            // Wait for it to be ready
            inferenceEngine!!.state.first { 
                it is InferenceEngine.State.ModelReady || it is InferenceEngine.State.Error 
            }
            
            val currentState = inferenceEngine!!.state.value
            if (currentState is InferenceEngine.State.Error) {
                isModelLoaded = false
                throw LLMServiceError.InvalidInput(currentState.exception.message ?: "Failed to load model")
            } else {
                isModelLoaded = true
                onProgress(1.0f)
            }
        } catch (e: Exception) {
            isModelLoaded = false
            throw LLMServiceError.InvalidInput(e.message ?: "Error loading model")
        }
    }

    override fun unloadModel() {
        if (isModelLoaded) {
            inferenceEngine?.cleanUp()
            isModelLoaded = false
        }
    }

    override suspend fun generateStream(
        prompt: String,
        maxTokens: Int,
        stopSequences: List<String>,
        temperature: Double
    ): Flow<String> {
        if (!isModelLoaded) {
            throw LLMServiceError.ModelNotLoaded("Cannot generate, model not loaded.")
        }
        
        // Use the sendUserPrompt from InferenceEngine
        // Note: InferenceEngine currently doesn't expose temperature, stop sequences, etc 
        // through its simplified API. We will just pass the prompt for now.
        return inferenceEngine!!.sendUserPrompt(prompt, maxTokens)
    }
}
