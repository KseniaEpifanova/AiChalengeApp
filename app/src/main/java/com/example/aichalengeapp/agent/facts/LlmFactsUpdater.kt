package com.example.aichalengeapp.agent.facts

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import javax.inject.Inject

class LlmFactsUpdater @Inject constructor(
    private val llmRepository: ChatRepository
) : FactsUpdater {

    override suspend fun updateFacts(existingFactsJson: String, userMessage: String): String {
        val system = AgentMessage(
            AgentRole.SYSTEM,
            """
You are a memory updater for a chat assistant.
Update FACTS JSON based ONLY on the NEW_USER_MESSAGE.
Do NOT invent facts. Keep values short.
If user contradicts a fact, overwrite it and add a short note into "conflicts".
Return ONLY valid JSON (no markdown).
Allowed keys:
goal (string), constraints (array), preferences (array), decisions (array),
open_questions (array), entities (object), conflicts (array).
If a key is missing, keep it.
""".trimIndent()
        )

        val user = AgentMessage(
            AgentRole.USER,
            buildString {
                appendLine("EXISTING_FACTS_JSON:")
                appendLine(existingFactsJson.ifBlank {
                    """{"goal":"","constraints":[],"preferences":[],"decisions":[],"open_questions":[],"entities":{},"conflicts":[]}"""
                })
                appendLine()
                appendLine("NEW_USER_MESSAGE:")
                appendLine(userMessage)
            }
        )

        return llmRepository.ask(listOf(system, user), maxOutputTokens = 250).text.trim()
    }
}
