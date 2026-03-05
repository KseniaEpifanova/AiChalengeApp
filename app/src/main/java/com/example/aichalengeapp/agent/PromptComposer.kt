package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.profile.UserProfile
import javax.inject.Inject

class PromptComposer @Inject constructor() {

    fun buildProfileDirective(profile: UserProfile): String {
        val style = profile.style.trim().ifBlank { "Нейтральный, дружелюбный." }
        val format = profile.format.trim().ifBlank { "Коротко и по делу, при необходимости списком." }
        val constraints = profile.constraints.trim().ifBlank { "Если не уверен — скажи, что не уверен." }

        return """
            ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ (обязательные правила для ответа):
            1) Стиль: $style
            2) Формат: $format
            3) Ограничения: $constraints

            Выполняй правила профиля автоматически в каждом ответе.
        """.trimIndent()
    }

    fun buildSystemPromptWithMemoryLayers(
        base: String,
        profileDirective: String,
        longTerm: String,
        working: String
    ): String {
        val lt = if (longTerm.isBlank()) "{}" else longTerm
        val wk = if (working.isBlank()) "{}" else working

        return """
            $base

            $profileDirective

            MEMORY LAYERS:
            1) SHORT-TERM: current dialog messages (provided below).
            2) WORKING: task state (JSON).
            3) LONG-TERM: stable memory (JSON).

            If conflict: WORKING overrides LONG-TERM.

            [LONG_TERM_JSON]
            $lt

            [WORKING_JSON]
            $wk
        """.trimIndent()
    }
}
