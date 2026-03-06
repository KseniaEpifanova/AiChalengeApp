package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.invariants.InvariantsProfile
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

    fun buildInvariantGuardDirective(profile: InvariantsProfile): String {
        val tech = profile.techDecisions.trim().ifBlank { "not specified" }
        val business = profile.businessRules.trim().ifBlank { "not specified" }

        return """
            INVARIANT GUARD (NON-NEGOTIABLE)

            Technical decisions:
            $tech

            Business rules:
            $business

            You must not propose solutions that violate these constraints.
            If a request conflicts with them, refuse and suggest compliant alternatives.
        """.trimIndent()
    }

    fun buildSystemPromptWithMemoryLayers(
        base: String,
        profileDirective: String,
        longTerm: String,
        working: String,
        invariantGuardDirective: String = ""
    ): String {
        val lt = if (longTerm.isBlank()) "{}" else longTerm
        val wk = if (working.isBlank()) "{}" else working
        val guardBlock = if (invariantGuardDirective.isBlank()) "" else "\n\n$invariantGuardDirective"

        return """
            $base

            $profileDirective$guardBlock

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
