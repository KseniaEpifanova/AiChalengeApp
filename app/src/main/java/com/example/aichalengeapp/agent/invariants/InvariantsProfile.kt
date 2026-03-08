package com.example.aichalengeapp.agent.invariants

data class InvariantsProfile(
    val techDecisions: String = "",
    val businessRules: String = ""
) {
    fun isEmpty(): Boolean {
        return techDecisions.isBlank() && businessRules.isBlank()
    }
}
