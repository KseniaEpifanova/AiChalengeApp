package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import javax.inject.Inject

class RequestClassifier @Inject constructor() {

    data class ClassificationDecision(
        val kind: RequestKind,
        val score: Int,
        val threshold: Int,
        val ambiguous: Boolean,
        val reasons: List<String>
    ) {
        val reason: String
            get() = buildString {
                append("score=")
                append(score)
                append(" threshold=")
                append(threshold)
                append(" ambiguous=")
                append(ambiguous)
                if (reasons.isNotEmpty()) {
                    append(" signals=")
                    append(reasons.joinToString(","))
                }
            }
    }

    private val intentMarkers = setOf(
        "design", "architect", "implement", "build", "develop", "plan", "structure",
        "create", "refactor", "workflow", "system", "feature",
        "спроект", "разработ", "реализ", "архитект", "продум", "созда", "сдела", "помоги"
    )
    private val structureMarkers = setOf(
        "screen", "ui", "state", "data", "api", "navigation", "filters", "search", "flow", "system", "feature",
        "экран", "интерфейс", "состояние", "данные", "апи", "навига", "фильтр", "поиск", "поток", "система", "фича"
    )
    private val requirementMarkers = setOf(
        "with", "and", "plus", "including", "requirements", "step-by-step",
        "плюс", "включая", "требования", "по шагам", "шаг"
    )
    private val quickQuestionMarkers = setOf(
        "time", "capital", "definition", "what is", "who is", "when is",
        "время", "столица", "что такое", "кто такой", "когда"
    )
    private val developerDomainMarkers = setOf(
        "screen", "ui", "feature", "architecture", "flow", "state", "navigation", "search", "filters", "implementation",
        "экран", "интерфейс", "фича", "архитект", "поток", "состояние", "навига", "поиск", "фильтр", "реализац"
    )

    fun classify(message: String, planningProfile: PlanningProfile): RequestKind {
        return classifyDetailed(message, planningProfile).kind
    }

    fun classifyForProfile(message: String, profile: AssistantProfile): ClassificationDecision {
        return classifyDetailed(
            message = message,
            planningProfile = profile.planningProfile,
            profileConstraints = profile.responseProfile.constraints,
            profileId = profile.id,
            profileName = profile.name
        )
    }

    fun classifyWithOptionalFallback(
        message: String,
        profile: AssistantProfile,
        fallback: ((String) -> RequestKind?)? = null
    ): ClassificationDecision {
        val local = classifyForProfile(message, profile)
        if (!local.ambiguous || fallback == null) return local

        val fallbackKind = fallback(message) ?: return local
        return local.copy(
            kind = fallbackKind,
            reasons = local.reasons + "fallback_override=$fallbackKind",
            ambiguous = false
        )
    }

    fun classifyDetailed(message: String, planningProfile: PlanningProfile): ClassificationDecision {
        return classifyDetailed(
            message = message,
            planningProfile = planningProfile,
            profileConstraints = "",
            profileId = "",
            profileName = ""
        )
    }

    private fun classifyDetailed(
        message: String,
        planningProfile: PlanningProfile,
        profileConstraints: String,
        profileId: String,
        profileName: String
    ): ClassificationDecision {
        val text = message.trim().lowercase()
        if (text.isEmpty()) {
            return ClassificationDecision(
                kind = RequestKind.SIMPLE,
                score = 0,
                threshold = Int.MAX_VALUE,
                ambiguous = false,
                reasons = listOf("empty_message")
            )
        }

        if (!planningProfile.autoDetectComplexity) {
            return ClassificationDecision(
                kind = RequestKind.SIMPLE,
                score = 0,
                threshold = Int.MAX_VALUE,
                ambiguous = false,
                reasons = listOf("autoDetectComplexity=false")
            )
        }

        var score = 0
        val reasons = mutableListOf<String>()

        val intentHits = intentMarkers.count { marker -> text.contains(marker) }
        if (intentHits > 0) {
            score += if (intentHits >= 2) 3 else 2
            reasons += "intent_hits=$intentHits"
        }

        val structureHits = structureMarkers.count { marker -> text.contains(marker) }
        if (structureHits >= 2) {
            score += 2
            reasons += "structure_hits=$structureHits"
        } else if (structureHits == 1) {
            score += 1
            reasons += "structure_hit=1"
        }

        val requirementHits = requirementMarkers.count { marker -> text.contains(marker) }
        val commaCount = text.count { it == ',' }
        if (requirementHits >= 2 || commaCount >= 2) {
            score += 2
            reasons += "multi_requirements"
        }

        if (text.length >= 220) {
            score += 2
            reasons += "long_message=${text.length}"
        } else if (text.length >= 120) {
            score += 1
            reasons += "medium_message=${text.length}"
        }

        if (text.count { it == '\n' } >= 2) {
            score += 1
            reasons += "multiline"
        }

        val isQuestion = text.endsWith("?")
        val quickHits = quickQuestionMarkers.count { marker -> text.contains(marker) }
        if (quickHits > 0) {
            score -= 3
            reasons += "quick_question_markers=$quickHits"
        } else if (isQuestion && text.length <= 50 && structureHits == 0 && intentHits == 0) {
            score -= 2
            reasons += "short_question_pattern"
        }

        val constraints = profileConstraints.lowercase()
        if (constraints.contains("step-by-step") || constraints.contains("architecture") || constraints.contains("детально")) {
            score += 1
            reasons += "profile_bias=detailed"
        } else if (constraints.contains("short answers") || constraints.contains("brief") || constraints.contains("коротко")) {
            score -= 1
            reasons += "profile_bias=brief"
        }

        val profileKey = "$profileId $profileName".lowercase()
        val isMobileDeveloperProfile = profileKey.contains("mobile_developer") ||
            profileKey.contains("mobile developer") ||
            profileKey.contains("developer")
        if (isMobileDeveloperProfile) {
            val developerHits = developerDomainMarkers.count { marker -> text.contains(marker) }
            if (developerHits >= 2) {
                score += 3
                reasons += "developer_profile_boost=3"
            } else if (developerHits == 1) {
                score += 2
                reasons += "developer_profile_boost=2"
            }
            if (text.contains("помоги") && (intentHits > 0 || structureHits > 0)) {
                score += 1
                reasons += "developer_help_intent_boost=1"
            }
        }

        val threshold = when (planningProfile.complexitySensitivity) {
            ComplexitySensitivity.LOW -> 5
            ComplexitySensitivity.MEDIUM -> 4
            ComplexitySensitivity.HIGH -> 3
        }

        val kind = if (score >= threshold) RequestKind.COMPLEX else RequestKind.SIMPLE
        val ambiguous = kotlin.math.abs(score - threshold) <= 1
        return ClassificationDecision(
            kind = kind,
            score = score,
            threshold = threshold,
            ambiguous = ambiguous,
            reasons = reasons + "sensitivity=${planningProfile.complexitySensitivity}"
        )
    }
}
