package com.sessions_ai.rag

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper

/**
 * On-device knowledge base: document ingestion (chunking), persistence, and full-text retrieval.
 * Uses SQLite with FTS5 for keyword search when available; falls back to LIKE when the device
 * SQLite build does not include FTS5. Matches iOS KnowledgeBaseStore pattern.
 */
class KnowledgeBaseStore(context: Context, dbName: String = "knowledge_base.sqlite") {

    private val helper = OpenHelper(context, dbName, this)

    /** True if FTS5 was created successfully; false on devices where SQLite has no FTS5 module. */
    @Volatile
    var fts5Available: Boolean = true
        internal set

    fun open() {
        helper.writableDatabase
    }

    /** Called when FTS5 is unavailable (creation failed or table missing). */
    internal fun disableFts5() {
        fts5Available = false
    }

    private fun db(): SQLiteDatabase = helper.writableDatabase

    /**
     * Add a document: chunk and index. Overwrites if documentId exists.
     */
    fun addDocument(documentId: String, path: String, title: String, text: String) {
        removeDocument(documentId)
        val chunks = TextChunking.chunk(text, documentId, path, title)
        val db = db()
        val now = System.currentTimeMillis() / 1000.0
        db.execSQL(
            "INSERT OR REPLACE INTO documents (id, path, title, added_at) VALUES (?, ?, ?, ?)",
            arrayOf<Any>(documentId, path, title, now)
        )
        for ((idx, chunkText) in chunks) {
            val chunkId = "${documentId}_$idx"
            db.execSQL(
                "INSERT INTO chunks (id, document_id, chunk_index, source, title, text) VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(chunkId, documentId, idx, path, title, chunkText)
            )
        }
    }

    fun removeDocument(documentId: String) {
        val db = db()
        db.execSQL("DELETE FROM chunks WHERE document_id = ?", arrayOf(documentId))
        db.execSQL("DELETE FROM documents WHERE id = ?", arrayOf(documentId))
    }

    /**
     * Retrieve top-K chunks: FTS5 when available, then LIKE fallback.
     */
    fun retrieve(query: String, topK: Int = 5): List<DocumentChunk> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || topK <= 0) return emptyList()
        if (fts5Available) {
            val chunks = retrieveFTS5(trimmed, topK)
            if (chunks.isNotEmpty()) return chunks
        }
        return retrieveFallback(trimmed, topK)
    }

    private fun retrieveFTS5(query: String, topK: Int): List<DocumentChunk> {
        val escaped = query.replace("\"", "\"\"")
        val db = db()
        return try {
            val cursor = db.rawQuery(
                "SELECT c.id, c.document_id, c.source, c.title, c.chunk_index, c.text FROM chunks c WHERE c.rowid IN (SELECT rowid FROM chunks_fts WHERE chunks_fts MATCH ? LIMIT ?)",
                arrayOf(escaped, topK.toString())
            )
            cursor.use {
                readChunksFromCursor(it)
            }
        } catch (e: SQLiteException) {
            if (e.message?.contains("no such table") == true) {
                disableFts5()
            }
            emptyList()
        }
    }

    private fun readChunksFromCursor(cursor: Cursor): List<DocumentChunk> {
        val result = mutableListOf<DocumentChunk>()
        val idIdx = cursor.getColumnIndexOrThrow("id")
        val docIdIdx = cursor.getColumnIndexOrThrow("document_id")
        val sourceIdx = cursor.getColumnIndexOrThrow("source")
        val titleIdx = cursor.getColumnIndexOrThrow("title")
        val chunkIdx = cursor.getColumnIndexOrThrow("chunk_index")
        val textIdx = cursor.getColumnIndexOrThrow("text")
        while (cursor.moveToNext()) {
            result.add(
                DocumentChunk(
                    id = cursor.getString(idIdx),
                    documentId = cursor.getString(docIdIdx),
                    source = cursor.getString(sourceIdx),
                    title = cursor.getString(titleIdx),
                    chunkIndex = cursor.getInt(chunkIdx),
                    text = cursor.getString(textIdx)
                )
            )
        }
        return result
    }

    private fun retrieveFallback(query: String, topK: Int): List<DocumentChunk> {
        val like = "%$query%"
        val db = db()
        val cursor = db.rawQuery(
            "SELECT id, document_id, source, title, chunk_index, text FROM chunks WHERE text LIKE ? LIMIT ?",
            arrayOf(like, topK.toString())
        )
        return cursor.use { readChunksFromCursor(it) }
    }

    fun retrieveChunksForDocument(documentId: String): List<DocumentChunk> {
        val db = db()
        val cursor = db.rawQuery(
            "SELECT id, document_id, source, title, chunk_index, text FROM chunks WHERE document_id = ? ORDER BY chunk_index",
            arrayOf(documentId)
        )
        return cursor.use { readChunksFromCursor(it) }
    }

    fun hasDocuments(): Boolean {
        val cursor = db().rawQuery("SELECT 1 FROM documents LIMIT 1", null)
        cursor.use { return it.moveToFirst() }
    }

    private class OpenHelper(
        context: Context,
        dbName: String,
        private val store: KnowledgeBaseStore
    ) : SQLiteOpenHelper(context, dbName, null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            createBaseTables(db)
            createFts5TablesAndTriggers(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createFts5TablesAndTriggers(db)
            }
        }

        /** Returns true if this SQLite build has the FTS5 module (avoids "no such module: fts5" on some devices). */
        private fun isFts5Available(db: SQLiteDatabase): Boolean {
            return try {
                db.rawQuery("SELECT fts5_version()", null).use { it.moveToFirst() }
            } catch (e: SQLiteException) {
                false
            }
        }

        private fun createBaseTables(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS documents (id TEXT PRIMARY KEY, path TEXT NOT NULL, title TEXT NOT NULL, added_at REAL NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS chunks (id TEXT PRIMARY KEY, document_id TEXT NOT NULL, chunk_index INTEGER NOT NULL, source TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL)"
            )
        }

        private fun createFts5TablesAndTriggers(db: SQLiteDatabase) {
            if (!isFts5Available(db)) {
                store.disableFts5()
                return
            }
            try {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(text, content='chunks', content_rowid='rowid')"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS chunks_ai AFTER INSERT ON chunks BEGIN INSERT INTO chunks_fts(rowid, text) VALUES (new.rowid, new.text); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS chunks_ad AFTER DELETE ON chunks BEGIN INSERT INTO chunks_fts(chunks_fts, rowid, text) VALUES('delete', old.rowid, old.text); END"
                )
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS chunks_au AFTER UPDATE ON chunks BEGIN INSERT INTO chunks_fts(chunks_fts, rowid, text) VALUES('delete', old.rowid, old.text); INSERT INTO chunks_fts(rowid, text) VALUES (new.rowid, new.text); END"
                )
                // Backfill FTS for existing chunks (migration from v1)
                db.execSQL("INSERT INTO chunks_fts(rowid, text) SELECT rowid, text FROM chunks")
            } catch (e: Exception) {
                store.disableFts5()
            }
        }
    }
}
