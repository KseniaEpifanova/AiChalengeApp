package com.example.aichalengeapp.agent.profile

data class PlanningProfile(
    val autoDetectComplexity: Boolean = true,
    val complexitySensitivity: ComplexitySensitivity = ComplexitySensitivity.MEDIUM,
    val requirePlanApproval: Boolean = true,
    val allowAutoContinueExecution: Boolean = false,
    val requireValidationBeforeDone: Boolean = true
)
