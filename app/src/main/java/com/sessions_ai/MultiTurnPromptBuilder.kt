package com.sessions_ai

/**
 * Builds a full prompt string from conversation turns and optional system prompt for a given chat template.
 */
object MultiTurnPromptBuilder {
    /**
     * @param turns Ordered user/assistant turns; the last turn should be the current user message.
     * @param systemPrompt Optional system/instruction prompt.
     * @param templateFormat Chat template (e.g. MISTRAL, LLAMA32).
     * @return Formatted prompt string for the model, or empty if turns are empty.
     */
    fun build(turns: List<ChatMessage>, systemPrompt: String?, templateFormat: ChatTemplateFormat): String {
        if (turns.isEmpty()) return ""
        
        val tokens = templateFormat.tokens
        return when (templateFormat) {
            ChatTemplateFormat.MISTRAL -> buildMistral(turns, systemPrompt, tokens)
            ChatTemplateFormat.LLAMA32 -> buildLlama32(turns, systemPrompt, tokens)
            ChatTemplateFormat.QWEN -> buildQwen(turns, systemPrompt, tokens, null)
            ChatTemplateFormat.QWEN3 -> buildQwen(turns, systemPrompt, tokens, "<think>\n\n</think>\n\n")
            ChatTemplateFormat.PHI31 -> buildPhi31(turns, systemPrompt, tokens)
        }
    }

    private fun buildMistral(turns: List<ChatMessage>, systemPrompt: String?, tokens: ChatTemplateTokens): String {
        val bos = tokens.beginSequence ?: ""
        val start = tokens.instructStart ?: ""
        val end = tokens.instructEnd ?: ""
        
        val parts = mutableListOf<String>()
        parts.add(bos)
        
        var first = true
        for (turn in turns) {
            when (turn.role) {
                ChatMessage.Role.USER -> {
                    if (!first) parts.add(end)
                    var userBlock = start
                    if (first && !systemPrompt.isNullOrEmpty()) {
                        userBlock += " User: ${turn.content}\n\n$systemPrompt"
                    } else {
                        userBlock += " ${turn.content}"
                    }
                    parts.add(userBlock)
                    first = false
                }
                ChatMessage.Role.ASSISTANT -> {
                    parts.add(end)
                    parts.add(" ${turn.content}")
                }
                ChatMessage.Role.SYSTEM -> {} // Handled in User block for Mistral
            }
        }
        parts.add(end)
        return parts.joinToString("")
    }

    private fun buildLlama32(turns: List<ChatMessage>, systemPrompt: String?, tokens: ChatTemplateTokens): String {
        val bos = tokens.beginSequence ?: ""
        val eot = tokens.endOfTurn ?: ""
        val sys = tokens.systemHeader ?: ""
        val usr = tokens.userHeader ?: ""
        val ast = tokens.assistantHeader ?: ""
        
        val parts = mutableListOf<String>()
        parts.add(bos)
        
        if (!systemPrompt.isNullOrEmpty()) {
            parts.add("${sys}\n\n${systemPrompt}${eot}")
        }
        
        for (turn in turns) {
            when (turn.role) {
                ChatMessage.Role.USER -> parts.add("${usr}\n\n${turn.content}${eot}")
                ChatMessage.Role.ASSISTANT -> parts.add("${ast}\n\n${turn.content}${eot}")
                ChatMessage.Role.SYSTEM -> {} // Ignored, passed via systemPrompt
            }
        }
        parts.add("${ast}\n\n")
        return parts.joinToString("")
    }

    private fun buildQwen(turns: List<ChatMessage>, systemPrompt: String?, tokens: ChatTemplateTokens, assistantGenerationSuffix: String?): String {
        val eot = tokens.endOfTurn ?: ""
        val sys = tokens.systemHeader ?: ""
        val usr = tokens.userHeader ?: ""
        val ast = tokens.assistantHeader ?: ""
        
        val sb = java.lang.StringBuilder()
        
        if (!systemPrompt.isNullOrEmpty()) {
            sb.append("${sys}${systemPrompt}${eot}")
        }
        
        for (turn in turns) {
            when (turn.role) {
                ChatMessage.Role.USER -> sb.append("${usr}${turn.content}${eot}")
                ChatMessage.Role.ASSISTANT -> sb.append("${ast}${turn.content}${eot}")
                ChatMessage.Role.SYSTEM -> {} // Ignored
            }
        }
        sb.append(ast)
        if (assistantGenerationSuffix != null) {
            sb.append(assistantGenerationSuffix)
        }
        return sb.toString()
    }

    private fun buildPhi31(turns: List<ChatMessage>, systemPrompt: String?, tokens: ChatTemplateTokens): String {
        val eot = tokens.endOfTurn ?: ""
        val sys = tokens.systemHeader ?: ""
        val usr = tokens.userHeader ?: ""
        val ast = tokens.assistantHeader ?: ""
        
        val sb = java.lang.StringBuilder()
        
        if (!systemPrompt.isNullOrEmpty()) {
            sb.append("${sys}${systemPrompt}${eot}")
        }
        
        for (turn in turns) {
            when (turn.role) {
                ChatMessage.Role.USER -> sb.append("${usr}${turn.content}${eot}")
                ChatMessage.Role.ASSISTANT -> sb.append("${ast}${turn.content}${eot}")
                ChatMessage.Role.SYSTEM -> {} // Ignored
            }
        }
        sb.append(ast)
        return sb.toString()
    }
}
