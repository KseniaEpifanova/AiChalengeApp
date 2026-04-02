package com.example.aichalengeapp.support

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.mcp.McpTrace
import com.example.aichalengeapp.repo.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

interface SupportAssistant {
    suspend fun answer(question: String): SupportAnswer
}

@Singleton
class SupportAssistantImpl @Inject constructor(
    private val knowledgeRepository: SupportKnowledgeRepository,
    private val contextRepository: SupportContextRepository,
    private val chatRepository: ChatRepository
) : SupportAssistant {

    override suspend fun answer(question: String): SupportAnswer {
        val normalizedQuestion = question.trim()
        require(normalizedQuestion.isNotBlank()) { "Support question is blank" }

        McpTrace.d("event" to "support_request_received", "question" to normalizedQuestion)
        val knowledgeChunks = knowledgeRepository.retrieve(normalizedQuestion)
        McpTrace.d(
            "event" to "support_docs_retrieval_success",
            "question" to normalizedQuestion,
            "chunks" to knowledgeChunks.size,
            "sources" to knowledgeChunks.joinToString(",") { it.title }
        )
        val supportContext = contextRepository.loadContext(normalizedQuestion)
        McpTrace.d(
            "event" to "support_context_retrieval_success",
            "question" to normalizedQuestion,
            "hasContext" to (supportContext != null),
            "ticketId" to supportContext?.recentTicketId
        )

        val prompt = buildPrompt(
            question = normalizedQuestion,
            knowledgeChunks = knowledgeChunks,
            supportContext = supportContext
        )
        val result = chatRepository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, SUPPORT_SYSTEM_PROMPT),
                AgentMessage(AgentRole.USER, prompt)
            ),
            maxOutputTokens = SUPPORT_MAX_OUTPUT_TOKENS
        )
        val contextSummary = supportContext?.let {
            buildString {
                append("User ").append(it.userName)
                append(" on ").append(it.plan)
                append(", app ").append(it.appVersion)
                if (!it.recentTicketId.isNullOrBlank()) {
                    append(", recent ticket ").append(it.recentTicketId)
                    if (!it.recentTicketStatus.isNullOrBlank()) {
                        append(" (").append(it.recentTicketStatus).append(")")
                    }
                }
            }
        }
        return SupportAnswer(
            text = result.text,
            sources = knowledgeChunks.map { it.title },
            contextSummary = contextSummary
        )
    }

    private fun buildPrompt(
        question: String,
        knowledgeChunks: List<SupportKnowledgeChunk>,
        supportContext: SupportUserContext?
    ): String {
        val docsSection = if (knowledgeChunks.isEmpty()) {
            "Support docs: no matching FAQ context found."
        } else {
            buildString {
                appendLine("Support docs:")
                knowledgeChunks.forEach { chunk ->
                    appendLine("- ${chunk.title}: ${chunk.text}")
                }
            }.trim()
        }
        val contextSection = if (supportContext == null) {
            "User/ticket context: unavailable."
        } else {
            buildString {
                appendLine("User/ticket context:")
                appendLine("- user_id: ${supportContext.userId}")
                appendLine("- user_name: ${supportContext.userName}")
                appendLine("- plan: ${supportContext.plan}")
                appendLine("- app_version: ${supportContext.appVersion}")
                appendLine("- recent_ticket_id: ${supportContext.recentTicketId ?: "n/a"}")
                appendLine("- recent_ticket_summary: ${supportContext.recentTicketSummary ?: "n/a"}")
                appendLine("- recent_ticket_status: ${supportContext.recentTicketStatus ?: "n/a"}")
            }.trim()
        }
        return """
            Customer support question:
            $question

            $docsSection

            $contextSection

            Write a clear support answer.
            Requirements:
            - answer like customer support, not developer docs
            - explain the likely cause only if grounded in the context above
            - give concrete next steps
            - if context is insufficient, say what is missing
            - do not invent account or ticket facts
        """.trimIndent()
    }

    private companion object {
        private const val SUPPORT_MAX_OUTPUT_TOKENS = 480
        private const val SUPPORT_SYSTEM_PROMPT =
            "You are a user support assistant for a mobile AI app. Be concise, clear, grounded, and practical."
    }
}
