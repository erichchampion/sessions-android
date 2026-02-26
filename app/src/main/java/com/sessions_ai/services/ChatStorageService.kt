package com.sessions_ai.services

import java.io.File

/**
 * Manages chat transcripts as markdown files in per-chat folders.
 * Each chat is a folder containing `chat.md`. Matches iOS ChatStorageService API.
 */
class ChatStorageService(private val chatsRootDir: File) {

    companion object {
        const val CHAT_FILE_NAME = "chat.md"

        /** Folder name from display name: trim, replace / and : with -, empty → "Chat". */
        fun sanitizedFolderName(displayName: String): String {
            val sanitized = displayName.trim()
                .replace("/", "-")
                .replace(":", "-")
            return if (sanitized.isEmpty()) "Chat" else sanitized
        }
    }

    /**
     * Lists chat files: each chat is a subdirectory containing `chat.md`.
     * Returns paths to each `.../FolderName/chat.md`, newest first by modification time.
     */
    fun listChatFiles(): List<File> {
        if (!chatsRootDir.isDirectory) return emptyList()
        val dirs = chatsRootDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs
            .mapNotNull { dir ->
                val chatFile = File(dir, CHAT_FILE_NAME)
                if (chatFile.isFile) chatFile else null
            }
            .sortedByDescending { it.lastModified() }
    }

    /**
     * Creates a new chat folder with the given display name, writes empty `chat.md`, returns the chat file.
     */
    fun createNewChatFolder(displayName: String): File {
        val folderName = sanitizedFolderName(displayName)
        val folder = File(chatsRootDir, folderName)
        folder.mkdirs()
        val chatFile = File(folder, CHAT_FILE_NAME)
        chatFile.writeText("")
        return chatFile
    }

    /** Returns conversation turns for a chat file (parsed from markdown). */
    fun turns(chatFile: File): List<ChatTurn> {
        val content = readContent(chatFile)
        return ChatMarkdownParser.parse(content)
    }

    /** Reads the full content of a chat file. */
    fun readContent(chatFile: File): String {
        if (!chatFile.isFile) return ""
        return chatFile.readText(Charsets.UTF_8)
    }

    /** Writes full content to a chat file (overwrites). */
    fun writeContent(content: String, chatFile: File) {
        chatFile.parentFile?.mkdirs()
        chatFile.writeText(content, Charsets.UTF_8)
    }

    /** Appends a user message. Format: \n\n## User\n\n{message}\n\n */
    fun appendUserTurn(message: String, chatFile: File) {
        append("\n\n## User\n\n$message\n\n", chatFile)
    }

    /** Appends an assistant message. Format: \n\n## Assistant\n\n{message}\n\n */
    fun appendAssistantTurn(message: String, chatFile: File) {
        append("\n\n## Assistant\n\n$message\n\n", chatFile)
    }

    private fun append(block: String, chatFile: File) {
        val existing = if (chatFile.isFile) chatFile.readText(Charsets.UTF_8) else ""
        chatFile.parentFile?.mkdirs()
        chatFile.writeText(existing + block, Charsets.UTF_8)
    }

    /** Deletes a chat by removing its entire folder. */
    fun deleteChat(chatFile: File) {
        val folder = chatFile.parentFile ?: return
        if (folder.isDirectory) folder.deleteRecursively()
    }

    /**
     * Renames a chat folder to a new display name. Returns the new chat file.
     * Caller must migrate any stores keyed by old path.
     */
    fun renameChat(fromChatFile: File, toDisplayName: String): File {
        if (!fromChatFile.isFile) return fromChatFile
        val folder = fromChatFile.parentFile ?: return fromChatFile
        val newFolderName = sanitizedFolderName(toDisplayName)
        val newFolder = File(chatsRootDir, newFolderName)
        if (newFolder.canonicalPath == folder.canonicalPath) return fromChatFile
        folder.renameTo(newFolder)
        return File(newFolder, CHAT_FILE_NAME)
    }
}
