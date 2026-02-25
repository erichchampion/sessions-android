package com.sessions_ai

fun main() {
    val mistralTurns = listOf(
        ChatMessage(ChatMessage.Role.USER, "Hello"),
        ChatMessage(ChatMessage.Role.ASSISTANT, "Hi there"),
        ChatMessage(ChatMessage.Role.USER, "How are you?")
    )
    val mistralSys = "You are a helpful assistant."
    println("--- MISTRAL EXPECTED ---")
    println("<s>[INST] User: Hello\n\nYou are a helpful assistant.[/INST] Hi there</s>[INST] How are you?[/INST]")
    println("--- MISTRAL ACTUAL ---")
    println(MultiTurnPromptBuilder.build(mistralTurns, mistralSys, ChatTemplateFormat.MISTRAL))
    
    val llamaTurns = listOf(ChatMessage(ChatMessage.Role.USER, "Hello"))
    val llamaSys = "You are a helpful assistant."
    println("--- LLAMA EXPECTED ---")
    println("<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n\n\nYou are a helpful assistant.<|eot_id|>\n<|start_header_id|>user<|end_header_id|>\n\n\n\nHello<|eot_id|>\n<|start_header_id|>assistant<|end_header_id|>\n\n")
    println("--- LLAMA ACTUAL ---")
    println(MultiTurnPromptBuilder.build(llamaTurns, llamaSys, ChatTemplateFormat.LLAMA32))
}
