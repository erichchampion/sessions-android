package com.sessions_ai

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiTurnPromptBuilderTest {

    @Test
    fun testBuildMistral() {
        val turns = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hello"),
            ChatMessage(ChatMessage.Role.ASSISTANT, "Hi there"),
            ChatMessage(ChatMessage.Role.USER, "How are you?")
        )
        val systemPrompt = "You are a helpful assistant."
        
        val actual = MultiTurnPromptBuilder.build(turns, systemPrompt, ChatTemplateFormat.MISTRAL)
        val expected = "<s>[INST] User: Hello\n\nYou are a helpful assistant.[/INST] Hi there[/INST][INST] How are you?[/INST]"
        assertEquals(expected, actual)
    }

    @Test
    fun testBuildLlama32() {
        val turns = listOf(
            ChatMessage(ChatMessage.Role.USER, "Hello")
        )
        val systemPrompt = "You are a helpful assistant."
        
        val actual = MultiTurnPromptBuilder.build(turns, systemPrompt, ChatTemplateFormat.LLAMA32)
        val expected = "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n\n\nYou are a helpful assistant.<|eot_id|>\n<|start_header_id|>user<|end_header_id|>\n\n\n\nHello<|eot_id|>\n<|start_header_id|>assistant<|end_header_id|>\n\n\n\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testBuildQwen3() {
        val turns = listOf(
            ChatMessage(ChatMessage.Role.USER, "What is 2+2?")
        )
        val systemPrompt = "You are a calculator."
        
        val actual = MultiTurnPromptBuilder.build(turns, systemPrompt, ChatTemplateFormat.QWEN3)
        // Qwen3 appends the <think> suffix for non-reasoning generation
        val expected = "<|im_start|>system\nYou are a calculator.<|im_end|>\n<|im_start|>user\nWhat is 2+2?<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testBuildPhi31() {
        val turns = listOf(
            ChatMessage(ChatMessage.Role.USER, "Tell me a joke")
        )
        val systemPrompt = "You are a comedian."
        
        val actual = MultiTurnPromptBuilder.build(turns, systemPrompt, ChatTemplateFormat.PHI31)
        val expected = "<|system|>\nYou are a comedian.<|end|><|user|>\nTell me a joke<|end|><|assistant|>\n"
        assertEquals(expected, actual)
    }

    @Test
    fun testEmptyTurns() {
        val actual = MultiTurnPromptBuilder.build(emptyList(), "System", ChatTemplateFormat.QWEN3)
        assertEquals("", actual)
    }
}
