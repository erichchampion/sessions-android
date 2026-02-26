package com.sessions_ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sessions_ai.*
import com.sessions_ai.rag.KnowledgeBaseStore
import com.sessions_ai.tools.Plan
import com.sessions_ai.tools.PlanningStore
import com.sessions_ai.services.ChatStorageService
import com.sessions_ai.services.ChatTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppState(
    val messages: List<ChatMessage> = emptyList(),
    val currentModel: LLMModelConfiguration? = null,
    val isGenerating: Boolean = false,
    val currentGeneration: String = "",
    val downloadStates: Map<LLMModelID, DownloadState> = emptyMap(),
    val chatFilePaths: List<String> = emptyList(),
    val currentChatPath: String? = null,
    val currentPlan: Plan? = null
) {
    val displayGeneration: String
        get() = com.sessions_ai.tools.ToolCallParser.scrubText(currentGeneration)
}

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val llmService: LLMService = LlamaCppService.shared
    private val knowledgeBaseStore = KnowledgeBaseStore(application)
    private val orchestrator: ChatOrchestrator = ChatOrchestrator(llmService, knowledgeBaseStore)
    private val downloadManager = LLMModelDownloadManager(application)
    private val chatStorage = ChatStorageService(File(application.filesDir, "chats"))
    private val prefs = application.getSharedPreferences("chat_prefs", 0)

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

        loadChatList()
        prefs.getString("last_chat_path", null)?.let { path ->
            if (File(path).isFile) loadSelectedChat(path)
        }
    }

    fun loadChatList() {
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { chatStorage.listChatFiles() }
            val paths = files.map { it.absolutePath }
            _uiState.value = _uiState.value.copy(chatFilePaths = paths)
        }
    }

    fun loadSelectedChat(path: String) {
        viewModelScope.launch {
            val turns = withContext(Dispatchers.IO) {
                chatStorage.turns(File(path))
            }
            val messages = turns.map { turn ->
                val role = when (turn.role) {
                    ChatTurn.Role.USER -> ChatMessage.Role.USER
                    ChatTurn.Role.ASSISTANT -> ChatMessage.Role.ASSISTANT
                }
                ChatMessage(role, turn.content)
            }
            prefs.edit().putString("last_chat_path", path).apply()
            PlanningStore.shared.setCurrentChat(path)
            _uiState.value = _uiState.value.copy(
                messages = messages,
                currentChatPath = path,
                currentPlan = PlanningStore.shared.planFor(path)
            )
        }
    }

    fun selectNewChat() {
        viewModelScope.launch {
            val name = "Chat-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
            val chatFile = withContext(Dispatchers.IO) { chatStorage.createNewChatFolder(name) }
            prefs.edit().putString("last_chat_path", chatFile.absolutePath).apply()
            PlanningStore.shared.setCurrentChat(chatFile.absolutePath)
            val files = withContext(Dispatchers.IO) { chatStorage.listChatFiles() }
            val paths = files.map { it.absolutePath }
            _uiState.value = _uiState.value.copy(
                messages = emptyList(),
                currentChatPath = chatFile.absolutePath,
                chatFilePaths = paths,
                currentPlan = PlanningStore.shared.planFor(chatFile.absolutePath)
            )
        }
    }

    fun deleteChat(path: String) {
        viewModelScope.launch {
            PlanningStore.shared.removePlan(path)
            withContext(Dispatchers.IO) { chatStorage.deleteChat(File(path)) }
            val paths = _uiState.value.chatFilePaths.filter { it != path }
            val wasCurrent = _uiState.value.currentChatPath == path
            val newCurrent = if (wasCurrent) null else _uiState.value.currentChatPath
            val newMessages = if (wasCurrent) emptyList() else _uiState.value.messages
            val newPlan = if (wasCurrent) null else _uiState.value.currentPlan
            if (wasCurrent) prefs.edit().remove("last_chat_path").apply()
            _uiState.value = _uiState.value.copy(
                chatFilePaths = paths,
                currentChatPath = newCurrent,
                messages = newMessages,
                currentPlan = newPlan
            )
        }
    }

    fun clearCurrentPlan() {
        val path = _uiState.value.currentChatPath ?: return
        PlanningStore.shared.setCurrentChat(path)
        PlanningStore.shared.clearPlan()
        _uiState.value = _uiState.value.copy(currentPlan = null)
    }

    fun renameChat(path: String, newDisplayName: String) {
        viewModelScope.launch {
            val newFile = withContext(Dispatchers.IO) { chatStorage.renameChat(File(path), newDisplayName) }
            PlanningStore.shared.migratePlan(path, newFile.absolutePath)
            val paths = _uiState.value.chatFilePaths.map { if (it == path) newFile.absolutePath else it }
            val newCurrent = if (_uiState.value.currentChatPath == path) newFile.absolutePath else _uiState.value.currentChatPath
            if (_uiState.value.currentChatPath == path) prefs.edit().putString("last_chat_path", newFile.absolutePath).apply()
            val newPlan = if (_uiState.value.currentChatPath == path) PlanningStore.shared.planFor(newFile.absolutePath) else _uiState.value.currentPlan
            _uiState.value = _uiState.value.copy(chatFilePaths = paths, currentChatPath = newCurrent, currentPlan = newPlan)
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
                // Ensure a chat exists (parity with iOS: create on first send when none selected)
                var pathToUse = _uiState.value.currentChatPath
                if (pathToUse == null) {
                    val name = "Chat-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}"
                    val chatFile = withContext(Dispatchers.IO) { chatStorage.createNewChatFolder(name) }
                    pathToUse = chatFile.absolutePath
                    prefs.edit().putString("last_chat_path", pathToUse).apply()
                    PlanningStore.shared.setCurrentChat(pathToUse)
                    val files = withContext(Dispatchers.IO) { chatStorage.listChatFiles() }
                    val paths = files.map { it.absolutePath }
                    _uiState.value = _uiState.value.copy(
                        currentChatPath = pathToUse,
                        chatFilePaths = paths,
                        currentPlan = PlanningStore.shared.planFor(pathToUse)
                    )
                }
                PlanningStore.shared.setCurrentChat(pathToUse)
                orchestrator.generateReply(updatedMessages, modelConfig, currentChatPath = pathToUse).collect { token ->
                    _uiState.value = _uiState.value.copy(
                        currentGeneration = _uiState.value.currentGeneration + token
                    )
                }
                
                val assistantContent = _uiState.value.displayGeneration
                val botMessage = ChatMessage(ChatMessage.Role.ASSISTANT, assistantContent)
                val pathAfterGen = _uiState.value.currentChatPath
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages + botMessage,
                    isGenerating = false,
                    currentGeneration = "",
                    currentPlan = pathAfterGen?.let { PlanningStore.shared.planFor(it) }
                )
                _uiState.value.currentChatPath?.let { path ->
                    val file = File(path)
                    withContext(Dispatchers.IO) {
                        chatStorage.appendUserTurn(content, file)
                        chatStorage.appendAssistantTurn(assistantContent, file)
                    }
                }

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
