package com.example.aichalengeapp.repo

import com.example.aichalengeapp.mcp.McpTrace
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class EmbeddingRepositoryImpl @Inject constructor() : EmbeddingRepository {
    override suspend fun embed(text: String, dimensions: Int?): FloatArray? {
        val normalized = text.trim()
        val targetDimensions = (dimensions ?: DEFAULT_DIMENSIONS).coerceAtLeast(64)
        if (normalized.isBlank()) return null

        val requestUrl = "local://keyword-hash-embedding?dimensions=$targetDimensions"
        McpTrace.d(
            "event" to "query_embedding_start",
            "provider" to PROVIDER_NAME,
            "query" to normalized,
            "dimensions" to targetDimensions
        )
        McpTrace.d(
            "event" to "query_embedding_request_url",
            "provider" to PROVIDER_NAME,
            "url" to requestUrl
        )

        val vector = FloatArray(targetDimensions)
        tokenize(normalized).forEach { token ->
            val bucket = positiveHash(token) % targetDimensions
            vector[bucket] += 1f
        }

        normalize(vector)

        McpTrace.d(
            "event" to "query_embedding_success",
            "provider" to PROVIDER_NAME,
            "dimensions" to vector.size,
            "nonZeroBuckets" to vector.count { it != 0f }
        )
        return vector
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.US)
            .split(Regex("""[^\p{L}\p{N}_]+"""))
            .filter { it.isNotBlank() }
    }

    private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE

    private fun normalize(vector: FloatArray) {
        var normSquared = 0.0
        vector.forEach { value -> normSquared += value * value }
        if (normSquared == 0.0) return
        val norm = sqrt(normSquared).toFloat()
        for (i in vector.indices) {
            vector[i] = vector[i] / norm
        }
    }

    private companion object {
        private const val PROVIDER_NAME = "local-keyword-hash"
        private const val DEFAULT_DIMENSIONS = 384
    }
}
