package com.example.aichalengeapp.agent.summary

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import javax.inject.Inject

class LlmSummarizer @Inject constructor(
    private val llmRepository: ChatRepository
) : Summarizer {

    override suspend fun summarizeChunk(existingSummary: String, chunk: List<AgentMessage>): String {
        val system = AgentMessage(
            AgentRole.SYSTEM,
            """
You compress chat history.
Return a concise summary.
Keep: user goals, constraints, decisions, entities, open questions.
Do NOT include code blocks. Use bullet points.
If existing summary is provided, update it (merge).
""".trimIndent()
        )

        val user = AgentMessage(
            AgentRole.USER,
            buildString {
                appendLine("EXISTING SUMMARY:")
                appendLine(existingSummary.ifBlank { "(none)" })
                appendLine()
                appendLine("NEW CHUNK TO MERGE:")
                chunk.forEach { m ->
                    val role = if (m.role == AgentRole.USER) "User" else "Assistant"
                    appendLine("$role: ${m.content}")
                }
                appendLine()
                appendLine("Return UPDATED SUMMARY only.")
            }
        )

        return llmRepository.ask(listOf(system, user), maxOutputTokens = 250).text.trim()
    }
}
