package com.example.aichalengeapp.agent.guard

data class InvariantsProfile(
    val techDecisions: String = "",
    val businessRules: String = ""
) {
    fun isEmpty(): Boolean = techDecisions.isBlank() && businessRules.isBlank()
}
