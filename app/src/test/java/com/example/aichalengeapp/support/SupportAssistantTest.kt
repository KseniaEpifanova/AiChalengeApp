package com.example.aichalengeapp.support

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.LlmResult
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportAssistantTest {

    @Test
    fun `support answer uses docs and support context`() = runBlocking {
        val knowledgeRepository = object : SupportKnowledgeRepository {
            override suspend fun retrieve(question: String): List<SupportKnowledgeChunk> {
                return listOf(
                    SupportKnowledgeChunk(
                        source = "support-faq",
                        title = "LOCAL model timeout",
                        text = "Reduce prompt size and verify local endpoint.",
                        score = 2
                    )
                )
            }
        }
        val contextRepository = object : SupportContextRepository {
            override suspend fun loadContext(question: String): SupportUserContext {
                return SupportUserContext(
                    userId = "user-1",
                    userName = "Casey",
                    plan = "Pro",
                    appVersion = "1.0.0",
                    recentTicketId = "SUP-1",
                    recentTicketSummary = "Timeout during local request",
                    recentTicketStatus = "open"
                )
            }
        }
        val chatRepository = RecordingChatRepository("Support reply")
        val assistant = SupportAssistantImpl(knowledgeRepository, contextRepository, chatRepository)

        val answer = assistant.answer("Why is my LOCAL model timing out?")

        assertEquals("Support reply", answer.text)
        assertEquals(listOf("LOCAL model timeout"), answer.sources)
        assertTrue(chatRepository.lastMessages.last().content.contains("Reduce prompt size"))
        assertTrue(chatRepository.lastMessages.last().content.contains("recent_ticket_id: SUP-1"))
    }

    @Test
    fun `blank support question is rejected`() = runBlocking {
        val assistant = SupportAssistantImpl(
            knowledgeRepository = object : SupportKnowledgeRepository {
                override suspend fun retrieve(question: String): List<SupportKnowledgeChunk> = emptyList()
            },
            contextRepository = object : SupportContextRepository {
                override suspend fun loadContext(question: String): SupportUserContext? = null
            },
            chatRepository = RecordingChatRepository("unused")
        )

        runCatching { assistant.answer("   ") }
            .onSuccess { error("Expected failure") }
            .onFailure { assertTrue(it is IllegalArgumentException) }
    }

    private class RecordingChatRepository(
        private val text: String
    ) : ChatRepository {
        var lastMessages: List<AgentMessage> = emptyList()

        override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
            lastMessages = messages
            return LlmResult(text)
        }
    }
}
