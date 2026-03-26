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
    suspend fun retrieve(query: String, mode: RetrievalMode = RetrievalMode.IMPROVED): List<RetrievedChunk>
}

@Singleton
class DocumentRetrieverImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddingProvider: QueryEmbeddingProvider,
    private val queryRewriter: QueryRewriter,
    private val retrievalFilter: RetrievalFilter,
    private val retrievalReranker: RetrievalReranker
) : DocumentRetriever {
    private companion object {
        private const val PRESELECT_BOOST_FACTOR = 0.65
    }

    override suspend fun retrieve(query: String, mode: RetrievalMode): List<RetrievedChunk> {
        val config = RetrievalConfig.forMode(mode)
        val rewrittenQuery = queryRewriter.rewrite(query, enabled = config.rewriteEnabled)
        val rewriteApplied = rewrittenQuery != query.trim()
        val lowConfidenceThreshold = config.similarityThreshold ?: 0.18
        McpTrace.d(
            "event" to "retrieval_start",
            "query" to preview(query),
            "mode" to modeLabel(mode),
            "topKBefore" to config.topKBefore,
            "topKAfter" to config.topKAfter
        )

        val dbFile = ensureIndexDbAvailable() ?: run {
            McpTrace.d("event" to "retrieval_success", "reason" to "index_db_missing", "query" to query)
            McpTrace.d("event" to "retrieved_chunks_count", "count" to 0)
            return emptyList()
        }

        val chunks = runCatching {
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
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

                val embeddingDimensions = candidates.firstOrNull()?.embedding?.size
                if (embeddingDimensions == null || embeddingDimensions == 0) {
                    emptyList()
                } else {
                    val queryEmbedding = runCatching { embeddingProvider.embed(rewrittenQuery, embeddingDimensions) }
                        .getOrElse { error ->
                            McpTrace.d(
                                "event" to "retrieval_failure",
                                "stage" to "query_embedding",
                                "query" to rewrittenQuery,
                                "error" to (error.message ?: error::class.java.simpleName)
                            )
                            return emptyList()
                        }

                    if (queryEmbedding == null || queryEmbedding.isEmpty()) {
                        emptyList()
                    } else {
                        val scored = candidates.map { candidate ->
                            val similarity = CosineSimilarity.compute(queryEmbedding, candidate.embedding)
                            val entityEvidence = CodeEntityMatchScorer.evaluate(
                                query = rewrittenQuery,
                                titleOrFile = candidate.titleOrFile,
                                section = candidate.section,
                                text = candidate.text
                            )
                            RetrievedChunk(
                                source = candidate.source,
                                titleOrFile = candidate.titleOrFile,
                                section = candidate.section,
                                chunkId = candidate.chunkId,
                                strategy = candidate.strategy,
                                text = candidate.text,
                                similarity = similarity,
                                finalScore = similarity + (entityEvidence.boost * PRESELECT_BOOST_FACTOR),
                                boostReasons = entityEvidence.reasons
                            )
                        }
                            .sortedByDescending { it.finalScore }

                        val topBefore = scored.take(config.topKBefore)
                        val topCandidate = topBefore.firstOrNull()
                        McpTrace.d(
                            "event" to "retrieval_topk_before_count",
                            "count" to topBefore.size
                        )

                        val filtered = retrievalFilter.apply(
                            chunks = topBefore,
                            threshold = config.similarityThreshold,
                            fallbackCount = config.topKAfter
                        )
                        if (config.similarityThreshold != null) {
                            val strictKeptCount = topBefore.count { it.similarity >= config.similarityThreshold }
                            McpTrace.d(
                                "event" to "retrieval_threshold_applied",
                                "threshold" to config.similarityThreshold,
                                "inputCount" to topBefore.size,
                                "keptCount" to strictKeptCount,
                                "fallbackApplied" to false
                            )
                        }
                        McpTrace.d(
                            "event" to "retrieval_rerank_applied",
                            "enabled" to config.rerankEnabled
                        )
                        val reranked = retrievalReranker.rerank(rewrittenQuery, filtered, enabled = config.rerankEnabled)
                        val finalResults = reranked.take(config.topKAfter)
                        if (topCandidate != null && topCandidate.similarity < lowConfidenceThreshold) {
                            McpTrace.d(
                                "event" to "retrieval_low_confidence",
                                "query" to preview(query),
                                "mode" to modeLabel(mode),
                                "topSimilarity" to topCandidate.similarity,
                                "topChunk" to topCandidate.titleOrFile.take(60)
                            )
                        }
                        if (finalResults.isEmpty()) {
                            McpTrace.d(
                                "event" to "retrieval_no_match",
                                "query" to preview(query),
                                "mode" to modeLabel(mode),
                                "topKBefore" to topBefore.size,
                                "threshold" to config.similarityThreshold,
                                "topSimilarity" to topCandidate?.similarity
                            )
                        }
                        McpTrace.d(
                            "event" to "retrieval_topk_after_count",
                            "count" to finalResults.size
                        )
                        McpTrace.d("event" to "retrieval_final_chunks_count", "count" to finalResults.size)

                        McpTrace.d(
                            "event" to "retrieval_summary",
                            "query" to preview(query),
                            "mode" to modeLabel(mode),
                            "rewriteApplied" to rewriteApplied,
                            "topKBefore" to topBefore.size,
                            "topKAfter" to finalResults.size,
                            "top1Chunk" to finalResults.firstOrNull()?.titleOrFile?.take(60),
                            "threshold" to config.similarityThreshold,
                            "rerank" to config.rerankEnabled
                        )
                        finalResults
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

    private fun preview(value: String, maxLength: Int = 120): String {
        return if (value.length <= maxLength) value else value.take(maxLength) + "..."
    }

    private fun modeLabel(mode: RetrievalMode): String {
        return when (mode) {
            RetrievalMode.BASELINE -> "BASELINE"
            RetrievalMode.IMPROVED -> "FILTERED"
        }
    }
}
