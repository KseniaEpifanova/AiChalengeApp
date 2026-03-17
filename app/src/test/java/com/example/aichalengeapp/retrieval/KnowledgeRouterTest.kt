package com.example.aichalengeapp.retrieval

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeRouterTest {
    private val router = KnowledgeRouter()

    @Test
    fun `matches english project knowledge question`() {
        assertTrue(router.shouldRetrieve("How does MCP connection work in this project?"))
    }

    @Test
    fun `matches russian project knowledge question`() {
        assertTrue(router.shouldRetrieve("Где хранится логика профиля?"))
    }

    @Test
    fun `does not match currency request`() {
        assertFalse(router.shouldRetrieve("Сколько будет 100 EUR в USD?"))
    }

    @Test
    fun `does not match casual chat`() {
        assertFalse(router.shouldRetrieve("Привет, как дела?"))
    }
}
