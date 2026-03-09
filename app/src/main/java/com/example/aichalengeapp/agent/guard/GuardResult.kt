package com.example.aichalengeapp.agent.guard

sealed class GuardResult {
    data object Ok : GuardResult()
    data class Violation(
        val invariant: String,
        val explanation: String
    ) : GuardResult()
}
