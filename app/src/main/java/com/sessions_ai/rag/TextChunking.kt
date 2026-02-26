package com.sessions_ai.rag

/**
 * Splits text into chunks suitable for retrieval (paragraph/size-aware with overlap).
 * Matches iOS TextChunking constants and behavior.
 */
object TextChunking {

    const val targetChunkChars = 600
    const val maxChunkChars = 900
    const val overlapChars = 80

    /**
     * Returns chunks with (chunkIndex, text). Paragraph-aware then size-limited.
     */
    fun chunk(
        text: String,
        documentId: String,
        source: String,
        title: String
    ): List<Pair<Int, String>> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val paragraphs = trimmed.split("\n\n")
        val result = mutableListOf<Pair<Int, String>>()
        var chunkIndex = 0
        for (para in paragraphs) {
            val block = para.trim()
            if (block.isEmpty()) continue
            if (block.length <= maxChunkChars) {
                result.add(chunkIndex to block)
                chunkIndex++
                continue
            }
            val subChunks = splitBySize(block, maxChunkChars, overlapChars)
            for (sub in subChunks) {
                result.add(chunkIndex to sub)
                chunkIndex++
            }
        }
        return result
    }

    private fun splitBySize(text: String, maxSize: Int, overlap: Int): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxSize).coerceAtMost(text.length)
            if (end < text.length) {
                val sub = text.substring(start, end)
                val lastSpaceInSub = sub.lastIndexOf(' ')
                val lastNewlineInSub = sub.lastIndexOf('\n')
                end = when {
                    lastSpaceInSub >= 0 -> start + lastSpaceInSub + 1
                    lastNewlineInSub >= 0 -> start + lastNewlineInSub + 1
                    else -> end
                }
            }
            result.add(text.substring(start, end))
            if (end >= text.length) break
            start = (end - overlap).coerceAtLeast(start).coerceIn(0, text.length)
            if (start >= end) start = end
        }
        return result
    }
}
