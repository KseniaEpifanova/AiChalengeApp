package com.example.aichalengeapp.agent.invariants

import javax.inject.Inject

class InvariantGuard @Inject constructor() {

    sealed class GuardResult {
        data object Ok : GuardResult()
        data class Violation(
            val invariant: String,
            val explanation: String
        ) : GuardResult()
    }

    fun check(
        assistantResponse: String,
        profile: InvariantsProfile,
        enabled: Boolean
    ): GuardResult {
        if (!enabled || profile.isEmpty()) return GuardResult.Ok

        val response = assistantResponse.lowercase()
        val tech = profile.techDecisions.lowercase()
        val business = profile.businessRules.lowercase()

        if (tech.contains("compose only") && (response.contains("xml") || response.contains("fragment"))) {
            return GuardResult.Violation(
                invariant = "Technical decisions = Compose only",
                explanation = "I cannot suggest XML/Fragment based UI as a primary solution."
            )
        }

        if ((tech.contains("coroutines") || tech.contains("no rxjava")) && response.contains("rxjava")) {
            return GuardResult.Violation(
                invariant = "Technical decisions = Coroutines / no RxJava",
                explanation = "I cannot suggest RxJava for this project."
            )
        }

        if (business.contains("no paid api") && (response.contains("paid api") || response.contains("subscription api"))) {
            return GuardResult.Violation(
                invariant = "Business rules = no paid APIs",
                explanation = "I cannot suggest a paid API dependency for this feature."
            )
        }

        if (business.contains("no secrets") &&
            (response.contains("store api key") || response.contains("hardcode api key") || response.contains("put key in source"))
        ) {
            return GuardResult.Violation(
                invariant = "Business rules = no secrets storage",
                explanation = "I cannot suggest storing secrets in app code or local persistent storage."
            )
        }

        return GuardResult.Ok
    }
}
