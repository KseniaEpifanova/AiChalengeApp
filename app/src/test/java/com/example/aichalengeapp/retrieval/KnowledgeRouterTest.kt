package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRouterTest {
    private val router = KnowledgeRouter()

    @Test
    fun `does not match self introduction question`() {
        assertFalse(router.shouldRetrieve("Who are you?"))
    }

    @Test
    fun `does not match capability question`() {
        assertFalse(router.shouldRetrieve("What can you do?"))
    }

    @Test
    fun `does not match greeting`() {
        assertFalse(router.shouldRetrieve("Hello"))
    }

    @Test
    fun `does not match general knowledge question`() {
        assertFalse(router.shouldRetrieve("What is Android?"))
    }

    @Test
    fun `matches chat agent question`() {
        assertTrue(router.shouldRetrieve("How does ChatAgent process messages?"))
    }

    @Test
    fun `matches viewmodel question`() {
        assertTrue(router.shouldRetrieve("Where is ViewModel state updated?"))
    }

    @Test
    fun `matches mcp client manager question`() {
        assertTrue(router.shouldRetrieve("What does McpClientManager do?"))
    }

    @Test
    fun `matches document retriever question`() {
        assertTrue(router.shouldRetrieve("How does DocumentRetriever find relevant chunks?"))
    }

    @Test
    fun `does not match currency request`() {
        assertFalse(router.shouldRetrieve("Сколько будет 100 EUR в USD?"))
    }
}
