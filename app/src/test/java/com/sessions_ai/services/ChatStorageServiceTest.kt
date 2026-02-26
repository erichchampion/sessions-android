package com.sessions_ai.services

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class ChatStorageServiceTest {

    @Test
    fun sanitizedFolderName_empty_returnsChat() {
        assertEquals("Chat", ChatStorageService.sanitizedFolderName(""))
        assertEquals("Chat", ChatStorageService.sanitizedFolderName("   "))
    }

    @Test
    fun sanitizedFolderName_replacesSlashAndColon() {
        assertEquals("My-Chat-2025", ChatStorageService.sanitizedFolderName("My/Chat:2025"))
    }

    @Test
    fun listChatFiles_emptyDir_returnsEmpty() {
        val dir = File.createTempFile("chats", null).apply { delete(); mkdirs() }
        try {
            val service = ChatStorageService(dir)
            assertTrue(service.listChatFiles().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun createNewChatFolder_createsDirAndChatMd() {
        val dir = File.createTempFile("chats", null).apply { delete(); mkdirs() }
        try {
            val service = ChatStorageService(dir)
            val chatFile = service.createNewChatFolder("Test Chat")
            assertTrue(chatFile.isFile)
            assertEquals(ChatStorageService.CHAT_FILE_NAME, chatFile.name)
            assertEquals("", chatFile.readText())
            assertEquals(1, service.listChatFiles().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun appendUserTurn_and_appendAssistantTurn_then_turns() {
        val dir = File.createTempFile("chats", null).apply { delete(); mkdirs() }
        try {
            val service = ChatStorageService(dir)
            val chatFile = service.createNewChatFolder("Append Test")
            service.appendUserTurn("Hello", chatFile)
            service.appendAssistantTurn("Hi there!", chatFile)
            val turns = service.turns(chatFile)
            assertEquals(2, turns.size)
            assertEquals(ChatTurn.Role.USER, turns[0].role)
            assertEquals("Hello", turns[0].content)
            assertEquals(ChatTurn.Role.ASSISTANT, turns[1].role)
            assertEquals("Hi there!", turns[1].content)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deleteChat_removesFolder() {
        val dir = File.createTempFile("chats", null).apply { delete(); mkdirs() }
        try {
            val service = ChatStorageService(dir)
            val chatFile = service.createNewChatFolder("To Delete")
            assertTrue(chatFile.parentFile?.isDirectory == true)
            service.deleteChat(chatFile)
            assertFalse(chatFile.parentFile?.exists() == true)
            assertTrue(service.listChatFiles().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun renameChat_movesFolderAndReturnsNewFile() {
        val dir = File.createTempFile("chats", null).apply { delete(); mkdirs() }
        try {
            val service = ChatStorageService(dir)
            val chatFile = service.createNewChatFolder("Old Name")
            service.appendUserTurn("Hi", chatFile)
            val newFile = service.renameChat(chatFile, "New Name")
            assertFalse(chatFile.exists())
            assertTrue(newFile.isFile)
            assertEquals("New Name", newFile.parentFile?.name)
            val turns = service.turns(newFile)
            assertEquals(1, turns.size)
            assertEquals("Hi", turns[0].content)
        } finally {
            dir.deleteRecursively()
        }
    }
}
