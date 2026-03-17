package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosineSimilarityTest {
    @Test
    fun `returns one for identical vectors`() {
        val score = CosineSimilarity.compute(
            floatArrayOf(1f, 2f, 3f),
            floatArrayOf(1f, 2f, 3f)
        )
        assertEquals(1.0, score, 0.0001)
    }

    @Test
    fun `returns zero for mismatched vector sizes`() {
        val score = CosineSimilarity.compute(
            floatArrayOf(1f, 2f),
            floatArrayOf(1f, 2f, 3f)
        )
        assertEquals(0.0, score, 0.0001)
    }

    @Test
    fun `returns positive similarity for related vectors`() {
        val score = CosineSimilarity.compute(
            floatArrayOf(1f, 0f, 1f),
            floatArrayOf(0.8f, 0.1f, 0.9f)
        )
        assertTrue(score > 0.9)
    }
}
