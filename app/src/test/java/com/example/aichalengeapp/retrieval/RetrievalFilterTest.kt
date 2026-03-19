package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrievalFilterTest {
    private val filter = RetrievalFilter()

    @Test
    fun `null threshold keeps all chunks`() {
        val chunks = listOf(
            chunk("a", 0.12),
            chunk("b", 0.42)
        )

        val filtered = filter.apply(chunks, threshold = null, fallbackCount = 3)

        assertEquals(2, filtered.size)
    }

    @Test
    fun `threshold drops weak chunks`() {
        val chunks = listOf(
            chunk("low", 0.21),
            chunk("high", 0.44)
        )

        val filtered = filter.apply(chunks, threshold = 0.28, fallbackCount = 3)

        assertEquals(1, filtered.size)
        assertEquals("high", filtered.single().chunkId)
    }

    @Test
    fun `threshold returns empty when all candidates are below threshold`() {
        val chunks = listOf(
            chunk("a", 0.11),
            chunk("b", 0.10),
            chunk("c", 0.09),
            chunk("d", 0.08)
        )

        val filtered = filter.apply(chunks, threshold = 0.28, fallbackCount = 3)

        assertTrue(filtered.isEmpty())
    }

    private fun chunk(id: String, similarity: Double): RetrievedChunk {
        return RetrievedChunk(
            source = "android-app",
            titleOrFile = "$id.kt",
            section = null,
            chunkId = id,
            strategy = "semantic",
            text = "Chunk $id",
            similarity = similarity
        )
    }
}
