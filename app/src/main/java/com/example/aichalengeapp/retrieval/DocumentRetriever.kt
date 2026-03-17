package com.example.aichalengeapp.retrieval

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.aichalengeapp.mcp.McpTrace
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentRetriever {
    suspend fun retrieve(query: String, topK: Int = 4): List<RetrievedChunk>
}

@Singleton
class DocumentRetrieverImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingProvider: QueryEmbeddingProvider
) : DocumentRetriever {
    override suspend fun retrieve(query: String, topK: Int): List<RetrievedChunk> {
        McpTrace.d("event" to "retrieval_start", "query" to query, "topK" to topK)

        val dbFile = ensureIndexDbAvailable() ?: run {
            McpTrace.d("event" to "retrieval_success", "reason" to "index_db_missing", "query" to query)
            McpTrace.d("event" to "retrieved_chunks_count", "count" to 0)
            return emptyList()
        }

        val chunks = runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                McpTrace.d("event" to "sqlite_index_opened", "path" to dbFile.absolutePath)

                val candidates = db.query(
                    "chunks",
                    arrayOf("source", "title_or_file", "section", "chunk_id", "strategy", "text", "embedding_json"),
                    null,
                    null,
                    null,
                    null,
                    null
                ).use { cursor ->
                    val candidates = mutableListOf<ChunkCandidate>()
                    while (cursor.moveToNext()) {
                        val embeddingJson = cursor.getString(6) ?: continue
                        val chunkEmbedding = parseEmbedding(embeddingJson) ?: continue
                        candidates += ChunkCandidate(
                            source = cursor.getString(0).orEmpty(),
                            titleOrFile = cursor.getString(1).orEmpty(),
                            section = cursor.getString(2),
                            chunkId = cursor.getString(3).orEmpty(),
                            strategy = cursor.getString(4),
                            text = cursor.getString(5).orEmpty(),
                            embedding = chunkEmbedding
                        )
                    }
                    candidates
                }

                McpTrace.d("event" to "chunk_candidates_loaded", "count" to candidates.size)
                val embeddingDimensions = candidates.firstOrNull()?.embedding?.size
                if (embeddingDimensions == null || embeddingDimensions == 0) {
                    emptyList()
                } else {
                    val queryEmbedding = runCatching { embeddingProvider.embed(query, embeddingDimensions) }
                        .getOrElse { error ->
                            McpTrace.d(
                                "event" to "retrieval_failure",
                                "stage" to "query_embedding",
                                "query" to query,
                                "error" to (error.message ?: error::class.java.simpleName)
                            )
                            return emptyList()
                        }

                    if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                        emptyList()
                    } else {
                        val results = candidates.map { candidate ->
                            RetrievedChunk(
                                source = candidate.source,
                                titleOrFile = candidate.titleOrFile,
                                section = candidate.section,
                                chunkId = candidate.chunkId,
                                strategy = candidate.strategy,
                                text = candidate.text,
                                similarity = CosineSimilarity.compute(queryEmbedding, candidate.embedding)
                            )
                        }
                            .sortedByDescending { it.similarity }
                            .take(topK)

                        McpTrace.d(
                            "event" to "similarity_search_success",
                            "query" to query,
                            "dimensions" to embeddingDimensions,
                            "returned" to results.size,
                            "topSimilarity" to results.firstOrNull()?.similarity
                        )
                        results
                    }
                }
            }
        }.getOrElse {
            McpTrace.d(
                "event" to "retrieval_failure",
                "stage" to "sqlite_query",
                "query" to query,
                "error" to (it.message ?: it::class.java.simpleName)
            )
            McpTrace.d("event" to "retrieval_success", "reason" to "sqlite_error", "error" to (it.message ?: it::class.java.simpleName))
            emptyList()
        }

        McpTrace.d("event" to "retrieval_success", "query" to query, "topSimilarity" to chunks.firstOrNull()?.similarity)
        McpTrace.d("event" to "retrieved_chunks_count", "count" to chunks.size)
        return chunks
    }

    private fun ensureIndexDbAvailable(): File? {
        val assetName = "index.db"
        val outputDir = File(context.filesDir, "retrieval").apply { mkdirs() }
        val outputFile = File(outputDir, assetName)
        if (outputFile.exists() && outputFile.length() > 0) return outputFile

        return runCatching {
            context.assets.open(assetName).use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
            outputFile
        }.getOrNull()
    }

    private fun parseEmbedding(raw: String): FloatArray? {
        return runCatching {
            val json = JSONArray(raw)
            FloatArray(json.length()) { index -> json.optDouble(index).toFloat() }
        }.getOrNull()
    }

    private data class ChunkCandidate(
        val source: String,
        val titleOrFile: String,
        val section: String?,
        val chunkId: String,
        val strategy: String?,
        val text: String,
        val embedding: FloatArray
    )
}
