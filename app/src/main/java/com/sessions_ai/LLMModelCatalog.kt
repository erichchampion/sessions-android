package com.sessions_ai

enum class LLMModelID(val stringValue: String) {
    MINISTRAL_3B_INSTRUCT("ministral-3-3b-instruct"),
    MINISTRAL_8B_INSTRUCT("ministral-3-8b-instruct"),
    MISTRAL_7B_INSTRUCT_V03("mistral-7b-instruct-v0.3"),
    LLAMA32_1B_INSTRUCT("llama-3.2-1b-instruct"),
    QWEN3_0_6B_INSTRUCT("qwen3-0.6b-instruct"),
    QWEN3_4B_INSTRUCT("qwen3-4b-instruct"),
    QWEN3_8B_INSTRUCT("qwen3-8b-instruct"),
    DEEPSEEK_R1_0528_QWEN3_8B("deepseek-r1-0528-qwen3-8b"),
    PHI4_MINI_INSTRUCT("phi-4-mini-instruct"),
    PHI4_INSTRUCT("phi-4-instruct");

    val displayName: String
        get() = when (this) {
            MINISTRAL_3B_INSTRUCT -> "Ministral 3B Instruct"
            MINISTRAL_8B_INSTRUCT -> "Ministral 3 8B Instruct"
            MISTRAL_7B_INSTRUCT_V03 -> "Mistral 7B Instruct v0.3"
            LLAMA32_1B_INSTRUCT -> "Llama 3.2 1B Instruct"
            QWEN3_0_6B_INSTRUCT -> "Qwen3 0.6B Instruct"
            QWEN3_4B_INSTRUCT -> "Qwen3 4B Instruct"
            QWEN3_8B_INSTRUCT -> "Qwen3 8B Instruct"
            DEEPSEEK_R1_0528_QWEN3_8B -> "DeepSeek-R1-0528 Qwen3 8B"
            PHI4_MINI_INSTRUCT -> "Phi-4 mini Instruct"
            PHI4_INSTRUCT -> "Phi-4 Instruct"
        }
}

enum class ChatTemplateFormat {
    MISTRAL, LLAMA32, QWEN, QWEN3, PHI31;

    val tokens: ChatTemplateTokens
        get() = when (this) {
            MISTRAL -> ChatTemplateTokens("<s>", "[INST]", "[/INST]", "</s>", null, null, null)
            LLAMA32 -> ChatTemplateTokens("<|begin_of_text|>", "<|start_header_id|>", "<|end_header_id|>\n\n", "<|eot_id|>\n", "<|start_header_id|>system<|end_header_id|>\n\n", "<|start_header_id|>user<|end_header_id|>\n\n", "<|start_header_id|>assistant<|end_header_id|>\n\n")
            QWEN -> ChatTemplateTokens(null, null, null, "<|im_end|>\n", "<|im_start|>system\n", "<|im_start|>user\n", "<|im_start|>assistant\n")
            QWEN3 -> ChatTemplateTokens(null, null, null, "<|im_end|>\n", "<|im_start|>system\n", "<|im_start|>user\n", "<|im_start|>assistant\n") // In Swift there were think block nuances
            PHI31 -> ChatTemplateTokens(null, null, null, "<|end|>", "<|system|>\n", "<|user|>\n", "<|assistant|>\n")
        }

    val stopSequences: List<String>
        get() = when (this) {
            MISTRAL -> listOf("</s>", "[INST]", "[/INST]")
            LLAMA32 -> listOf("<|eot_id|>", "<|eom_id|>")
            QWEN, QWEN3 -> listOf("<|im_end|>", "<|im_end|", "<|im_start|>")
            PHI31 -> listOf("<|end|>", "<|user|>", "<|system|>", "<|assistant|>")
        }
}

data class ChatTemplateTokens(
    val beginSequence: String?,
    val instructStart: String?,
    val instructEnd: String?,
    val endOfTurn: String?,
    val systemHeader: String?,
    val userHeader: String?,
    val assistantHeader: String?
)

data class LLMModelConfiguration(
    val id: LLMModelID,
    val repository: String,
    val fileName: String,
    val localFileName: String,
    val expectedFileSize: Long,
    val fileSizeTolerance: Double,
    val chatTemplate: ChatTemplateFormat,
    val defaultMaxTokens: Int,
    val defaultTemperature: Double,
    val contextWindow: Int,
    val license: String,
    val recommended: Boolean
) {
    companion object {
        fun formatFileSize(bytes: Long, approximate: Boolean = true): String {
            val d = bytes.toDouble()
            val kb = d / 1024
            val mb = kb / 1024
            val gb = mb / 1024
            val prefix = if (approximate) "~" else ""
            return when {
                gb >= 1.0 -> String.format("%s%.1f GB", prefix, gb)
                mb >= 1.0 -> String.format("%s%.0f MB", prefix, mb)
                kb >= 1.0 -> String.format("%s%.0f KB", prefix, kb)
                else -> "$bytes bytes"
            }
        }
    }
}

object LLMModelCatalog {
    val defaultModelID = LLMModelID.QWEN3_4B_INSTRUCT
    
    val models: Map<LLMModelID, LLMModelConfiguration> = mapOf(
        LLMModelID.MINISTRAL_3B_INSTRUCT to LLMModelConfiguration(
            id = LLMModelID.MINISTRAL_3B_INSTRUCT,
            repository = "bartowski/mistralai_Ministral-3-3B-Instruct-2512-GGUF",
            fileName = "mistralai_Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
            localFileName = "ministral-3-3b-instruct-q4km.gguf",
            expectedFileSize = 2000000000L, // Placeholder size, you'd match Swift's exact bytes
            fileSizeTolerance = 0.05,
            chatTemplate = ChatTemplateFormat.MISTRAL,
            defaultMaxTokens = 512,
            defaultTemperature = 0.7,
            contextWindow = 32768,
            license = "Apache 2.0",
            recommended = false
        ),
        LLMModelID.LLAMA32_1B_INSTRUCT to LLMModelConfiguration(
            id = LLMModelID.LLAMA32_1B_INSTRUCT,
            repository = "bartowski/Llama-3.2-1B-Instruct-GGUF",
            fileName = "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            localFileName = "llama-3.2-1b-instruct.gguf",
            expectedFileSize = 847249408L,
            fileSizeTolerance = 0.05,
            chatTemplate = ChatTemplateFormat.LLAMA32,
            defaultMaxTokens = 512,
            defaultTemperature = 0.7,
            contextWindow = 8192,
            license = "Meta Llama 3 Community License",
            recommended = true
        ),
        LLMModelID.QWEN3_0_6B_INSTRUCT to LLMModelConfiguration(
            id = LLMModelID.QWEN3_0_6B_INSTRUCT,
            repository = "unsloth/Qwen3-0.6B-GGUF",
            fileName = "Qwen3-0.6B-Q4_K_M.gguf",
            localFileName = "qwen3-0.6b-instruct.gguf",
            expectedFileSize = 396705472L,
            fileSizeTolerance = 0.05,
            chatTemplate = ChatTemplateFormat.QWEN3,
            defaultMaxTokens = 512,
            defaultTemperature = 0.7,
            contextWindow = 32768,
            license = "Apache 2.0",
            recommended = true
        ),
        LLMModelID.QWEN3_4B_INSTRUCT to LLMModelConfiguration(
            id = LLMModelID.QWEN3_4B_INSTRUCT,
            repository = "unsloth/Qwen3-4B-GGUF",
            fileName = "Qwen3-4B-Q4_K_M.gguf",
            localFileName = "qwen3-4b-instruct.gguf",
            expectedFileSize = 2497281312L,
            fileSizeTolerance = 0.05,
            chatTemplate = ChatTemplateFormat.QWEN3,
            defaultMaxTokens = 512,
            defaultTemperature = 0.7,
            contextWindow = 32768,
            license = "Apache 2.0",
            recommended = true
        ),
        LLMModelID.QWEN3_8B_INSTRUCT to LLMModelConfiguration(
            id = LLMModelID.QWEN3_8B_INSTRUCT,
            repository = "unsloth/Qwen3-8B-GGUF",
            fileName = "Qwen3-8B-Q4_K_M.gguf",
            localFileName = "qwen3-8b-instruct.gguf",
            expectedFileSize = 5027784512L,
            fileSizeTolerance = 0.05,
            chatTemplate = ChatTemplateFormat.QWEN3,
            defaultMaxTokens = 512,
            defaultTemperature = 0.7,
            contextWindow = 32768,
            license = "Apache 2.0",
            recommended = false
        )
    )

    fun getConfiguration(id: LLMModelID): LLMModelConfiguration? {
        return models[id]
    }
}
