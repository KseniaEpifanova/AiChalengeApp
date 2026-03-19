package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import javax.inject.Inject

class PromptAssembler @Inject constructor() {

    fun buildContextPrompt(
        basePrompt: String,
        planningProfile: com.example.aichalengeapp.agent.profile.PlanningProfile,
        invariants: InvariantsProfile,
        taskState: TaskState?,
        longTermJson: String,
        workingJson: String
    ): String {
        val lt = longTermJson.ifBlank { "{}" }
        val wk = workingJson.ifBlank { "{}" }
        val guardActive = !invariants.isEmpty()

        val guardBlock = if (guardActive) {
            """
            INVARIANT GUARD (NON-NEGOTIABLE)
            Technical decisions: ${invariants.techDecisions.ifBlank { "not specified" }}
            Business rules: ${invariants.businessRules.ifBlank { "not specified" }}
            """.trimIndent()
        } else {
            ""
        }

        val taskBlock = if (taskState != null) {
            val allowedBehavior = when (taskState.stage) {
                TaskStage.PLANNING, TaskStage.PLAN_REVIEW ->
                    "review goal, clarify constraints, propose/refine plan"
                TaskStage.EXECUTION ->
                    "implement planned work, propose concrete coding actions"
                TaskStage.VALIDATION ->
                    "review solution, validate requirements, check edge cases, summarize findings"
                TaskStage.DONE ->
                    "return completion summary"
                TaskStage.CANCELLED ->
                    "explain cancellation state and possible restart options"
            }
            val forbiddenBehavior = when (taskState.stage) {
                TaskStage.PLANNING, TaskStage.PLAN_REVIEW ->
                    "do not claim implementation or completion"
                TaskStage.EXECUTION ->
                    "do not claim task completion or validation passed"
                TaskStage.VALIDATION ->
                    "do not continue implementation"
                TaskStage.DONE ->
                    "do not continue implementation or validation"
                TaskStage.CANCELLED ->
                    "do not continue lifecycle steps"
            }
            """
            TASK LIFECYCLE:
            stage=${taskState.stage}
            paused=${taskState.paused}
            currentStep=${com.example.aichalengeapp.agent.task.stageToStep(taskState.stage).id}
            planApproved=${taskState.planApproved}

            STRICT STAGE CONTRACT:
            Current task stage: ${taskState.stage}
            Allowed behavior in ${taskState.stage}:
            - $allowedBehavior
            Forbidden behavior in ${taskState.stage}:
            - $forbiddenBehavior
            """.trimIndent()
        } else {
            ""
        }

        return """
            $basePrompt

            PLANNING PROFILE:
            autoDetectComplexity=${planningProfile.autoDetectComplexity}
            complexitySensitivity=${planningProfile.complexitySensitivity}
            requirePlanApproval=${planningProfile.requirePlanApproval}
            allowAutoContinueExecution=${planningProfile.allowAutoContinueExecution}
            requireValidationBeforeDone=${planningProfile.requireValidationBeforeDone}

            $guardBlock

            $taskBlock

            MEMORY LAYERS:
            LONG_TERM_JSON=$lt
            WORKING_JSON=$wk
        """.trimIndent()
    }

    fun buildProfilePrompt(profile: AssistantProfile): String {
        val response = profile.responseProfile
        val style = response.style.trim()
        val format = response.format.trim()
        val constraints = response.constraints.trim()

        val rules = buildList {
            if (style.isNotBlank()) add("Style requirement: $style")
            if (format.isNotBlank()) add("Format requirement: $format")
            if (constraints.isNotBlank()) add("Constraint requirement: $constraints")

            when {
                requiresShortAnswer(response) -> {
                    add("You MUST answer briefly.")
                    add("Maximum length: 3 to 5 sentences unless the user explicitly asks for more detail.")
                    add("Prefer a compact structure such as short bullets or a short paragraph.")
                    add("Do NOT add extra explanation, examples, or background unless the user asks.")
                }
                requiresLongAnswer(response) -> {
                    add("You MUST provide a detailed answer.")
                    add("Use multiple paragraphs or structured bullets when useful.")
                    add("Include enough explanation to make the reasoning easy to follow.")
                }
            }
        }

        return if (rules.isEmpty()) {
            """
            RESPONSE PROFILE (HIGHEST PRIORITY)
            No explicit response style, format, or constraint rules are set for this profile.
            """.trimIndent()
        } else {
            buildString {
                appendLine("RESPONSE PROFILE (HIGHEST PRIORITY)")
                appendLine("You MUST follow these profile rules in every answer.")
                rules.forEach { rule -> appendLine("- $rule") }
            }.trimIndent()
        }
    }

    private fun requiresShortAnswer(responseProfile: ResponseProfile): Boolean {
        val combined = listOf(
            responseProfile.style,
            responseProfile.format,
            responseProfile.constraints
        ).joinToString(" ").lowercase()

        return combined.contains("short") ||
            combined.contains("brief") ||
            combined.contains("concise") ||
            combined.contains("3-5 sentences") ||
            combined.contains("корот") ||
            combined.contains("кратк") ||
            combined.contains("сжато")
    }

    private fun requiresLongAnswer(responseProfile: ResponseProfile): Boolean {
        val combined = listOf(
            responseProfile.style,
            responseProfile.format,
            responseProfile.constraints
        ).joinToString(" ").lowercase()

        return combined.contains("long") ||
            combined.contains("detailed") ||
            combined.contains("thorough") ||
            combined.contains("подроб") ||
            combined.contains("деталь")
    }
}
