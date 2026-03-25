package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
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
                append("score=").append(score)
                append(" threshold=").append(threshold)
                append(" ambiguous=").append(ambiguous)
                if (reasons.isNotEmpty()) {
                    append(" signals=").append(reasons.joinToString(","))
                }
            }
    }

    fun classify(message: String, planningProfile: PlanningProfile): RequestKind {
        return classifyDetailed(message, planningProfile).kind
    }

    fun classifyForProfile(message: String, profile: AssistantProfile): ClassificationDecision {
        return classifyDetailed(message, profile.planningProfile)
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
        val text = message.trim()
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

        val signals = TaskTriggerHeuristics.analyze(text)
        if (signals.hasSocialSignals && !signals.hasExecutionSignals && !signals.hasExplorationSignals) {
            return ClassificationDecision(
                kind = RequestKind.SIMPLE,
                score = 0,
                threshold = Int.MAX_VALUE,
                ambiguous = false,
                reasons = listOf("social_only") + signals.socialHits
            )
        }

        if (signals.hasExplorationSignals && !signals.hasExecutionSignals) {
            return ClassificationDecision(
                kind = RequestKind.SIMPLE,
                score = 0,
                threshold = Int.MAX_VALUE,
                ambiguous = false,
                reasons = listOf("exploration_only") + signals.explorationHits
            )
        }

        if (!signals.hasExecutionSignals) {
            return ClassificationDecision(
                kind = RequestKind.SIMPLE,
                score = 0,
                threshold = Int.MAX_VALUE,
                ambiguous = false,
                reasons = listOf("no_execution_evidence")
            )
        }

        val tokens = TaskTriggerHeuristics.tokenize(text.lowercase())
        val score = buildExecutionScore(text, tokens, signals)
        val threshold = when (planningProfile.complexitySensitivity) {
            ComplexitySensitivity.LOW -> 3
            ComplexitySensitivity.MEDIUM -> 3
            ComplexitySensitivity.HIGH -> 2
        }
        val reasons = buildList {
            addAll(signals.executionHits.map { "execution:$it" })
            if (tokens.size >= 2) add("has_object_tokens")
            if (TaskTriggerHeuristics.hasLeadingExecutionVerb(text)) add("leading_execution_verb")
            if (hasComplexityShape(text, tokens)) add("complexity_shape")
        }

        return ClassificationDecision(
            kind = if (score >= threshold) RequestKind.COMPLEX else RequestKind.SIMPLE,
            score = score,
            threshold = threshold,
            ambiguous = kotlin.math.abs(score - threshold) <= 1,
            reasons = reasons
        )
    }

    private fun buildExecutionScore(
        text: String,
        tokens: List<String>,
        signals: TaskTriggerHeuristics.TriggerSignals
    ): Int {
        var score = 2 // explicit execution evidence is mandatory and worth most of the score
        if (tokens.size >= 2) score += 1
        if (TaskTriggerHeuristics.hasLeadingExecutionVerb(text)) score += 1
        if (hasComplexityShape(text, tokens) && signals.hasExecutionSignals) score += 1
        return score
    }

    private fun hasComplexityShape(text: String, tokens: List<String>): Boolean {
        if (text.count { it == '\n' } >= 1) return true
        if (text.count { it == ',' } >= 1) return true
        if (tokens.size >= 6) return true
        return tokens.any { it == "with" || it == "including" || it == "and" || it == "с" || it == "и" }
    }
}
