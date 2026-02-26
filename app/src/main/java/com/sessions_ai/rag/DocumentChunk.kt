package com.sessions_ai.rag

/**
 * A single chunk of a document for RAG retrieval. Matches iOS DocumentChunk.
 */
data class DocumentChunk(
    val id: String,
    val documentId: String,
    val source: String,
    val title: String,
    val chunkIndex: Int,
    val text: String
)
