package com.sessions_ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sessions_ai.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val messages: List<ChatMessage> = emptyList(),
    val currentModel: LLMModelConfiguration? = null,
    val isGenerating: Boolean = false,
    val currentGeneration: String = "",
    val downloadStates: Map<LLMModelID, DownloadState> = emptyMap()
)

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val llmService: LLMService = LlamaCppService.shared
    private val orchestrator: ChatOrchestrator = ChatOrchestrator(llmService)
    private val downloadManager = LLMModelDownloadManager(application)

    private val _uiState = MutableStateFlow(AppState())
    val uiState: StateFlow<AppState> = _uiState.asStateFlow()

    init {
        // Initialize inference engine singleton early
        (llmService as? LlamaCppService)?.initialize(application)
        
        // Initialize download states from disk
        val initialStates = mutableMapOf<LLMModelID, DownloadState>()
        for (config in LLMModelCatalog.models.values) {
            if (downloadManager.isModelDownloaded(config)) {
                initialStates[config.id] = DownloadState.Completed
            } else {
                initialStates[config.id] = DownloadState.Idle
            }
        }
        _uiState.value = _uiState.value.copy(downloadStates = initialStates)

        // Select default model if downloaded
        val defaultConfig = LLMModelCatalog.getConfiguration(LLMModelCatalog.defaultModelID)
        if (defaultConfig != null && downloadManager.isModelDownloaded(defaultConfig)) {
            loadModel(defaultConfig)
        }
    }

    fun selectModel(id: LLMModelID) {
        val config = LLMModelCatalog.getConfiguration(id) ?: return
        
        if (downloadManager.isModelDownloaded(config)) {
            loadModel(config)
        } else {
            // Trigger download instead
            viewModelScope.launch {
                downloadManager.downloadModel(config).collect { state ->
                    val newStates = _uiState.value.downloadStates.toMutableMap()
                    newStates[config.id] = state
                    _uiState.value = _uiState.value.copy(downloadStates = newStates)
                    
                    if (state is DownloadState.Completed) {
                        loadModel(config)
                    }
                }
            }
        }
    }

    private fun loadModel(config: LLMModelConfiguration) {
        _uiState.value = _uiState.value.copy(currentModel = config)
        viewModelScope.launch {
            try {
                val file = downloadManager.getModelFile(config)
                llmService.loadModel(file.absolutePath, config.contextWindow) { progress ->
                    // Could track loading progress
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(ChatMessage.Role.USER, content)
        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(messages = updatedMessages, isGenerating = true, currentGeneration = "")

        viewModelScope.launch {
            try {
                val modelConfig = _uiState.value.currentModel 
                if (modelConfig == null) {
                    val botMessage = ChatMessage(ChatMessage.Role.ASSISTANT, "Error: Please download and select a model first.")
                    _uiState.value = _uiState.value.copy(messages = updatedMessages + botMessage, isGenerating = false)
                    return@launch
                }
                
                orchestrator.generateReply(updatedMessages, modelConfig).collect { token ->
                    _uiState.value = _uiState.value.copy(
                        currentGeneration = _uiState.value.currentGeneration + token
                    )
                }
                
                val botMessage = ChatMessage(ChatMessage.Role.ASSISTANT, _uiState.value.currentGeneration)
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages + botMessage,
                    isGenerating = false,
                    currentGeneration = ""
                )

            } catch (e: Exception) {
                val botMessage = ChatMessage(ChatMessage.Role.ASSISTANT, "Error: ${e.message}")
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages + botMessage,
                    isGenerating = false,
                    currentGeneration = ""
                )
                e.printStackTrace()
            }
        }
    }
}
