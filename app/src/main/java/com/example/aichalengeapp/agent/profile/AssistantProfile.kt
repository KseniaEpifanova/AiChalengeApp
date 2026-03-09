package com.example.aichalengeapp.agent.profile

data class AssistantProfile(
    val id: String,
    val name: String,
    val responseProfile: ResponseProfile,
    val planningProfile: PlanningProfile,
    val isDefault: Boolean = false
) {
    companion object {
        const val DEFAULT_ID = "default"

        fun default(): AssistantProfile {
            return AssistantProfile(
                id = DEFAULT_ID,
                name = "Default",
                responseProfile = ResponseProfile(
                    style = "Friendly, supportive, calm",
                    format = "Short and structured. Use bullets when helpful.",
                    constraints = "No special constraints."
                ),
                planningProfile = PlanningProfile(),
                isDefault = true
            )
        }

        fun mobileDeveloper(): AssistantProfile {
            return AssistantProfile(
                id = "mobile_developer",
                name = "Mobile Developer",
                responseProfile = ResponseProfile(
                    style = "Technical, concise, implementation-focused",
                    format = "Step-by-step with concrete code-level guidance",
                    constraints = "Prefer practical architecture-safe changes"
                ),
                planningProfile = PlanningProfile(
                    autoDetectComplexity = true,
                    complexitySensitivity = ComplexitySensitivity.HIGH,
                    requirePlanApproval = true,
                    allowAutoContinueExecution = false,
                    requireValidationBeforeDone = true
                ),
                isDefault = false
            )
        }

        fun developer(): AssistantProfile = mobileDeveloper()
    }
}
