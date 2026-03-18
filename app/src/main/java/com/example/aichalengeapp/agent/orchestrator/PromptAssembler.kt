package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import javax.inject.Inject

class PromptAssembler @Inject constructor() {

    fun assemble(
        basePrompt: String,
        profile: AssistantProfile,
        invariants: InvariantsProfile,
        taskState: TaskState?,
        longTermJson: String,
        workingJson: String
    ): String {
        val lt = longTermJson.ifBlank { "{}" }
        val wk = workingJson.ifBlank { "{}" }
        val response = profile.responseProfile
        val planning = profile.planningProfile
        val style = response.style.ifBlank { "Friendly, supportive, calm" }
        val format = response.format.ifBlank { "Short and structured. Use bullets when helpful." }
        val constraints = response.constraints.ifBlank { "No special constraints." }

        val guardActive = !invariants.isEmpty()
        val shortAnswerRequired = requiresShortAnswer(response)

        val responseContract = buildString {
            appendLine("RESPONSE RULES (HIGHEST PRIORITY)")
            appendLine("You MUST follow these rules in every answer.")
            appendLine("- Style requirement: $style")
            appendLine("- Format requirement: $format")
            appendLine("- Constraint requirement: $constraints")
            if (shortAnswerRequired) {
                appendLine("- Keep the answer brief: maximum 3 to 5 sentences.")
                appendLine("- Prefer short bullet points when possible.")
                appendLine("- Do NOT write long explanations.")
                appendLine("- Do NOT add extra context unless the user asks for it.")
                appendLine("- If your draft is longer than 5 sentences, rewrite it shorter before responding.")
            } else {
                appendLine("- Stay consistent with the requested style and format.")
                appendLine("- Avoid unnecessary filler and repetition.")
            }
        }.trimIndent()

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
            $responseContract

            $basePrompt

            RESPONSE PROFILE:
            style=$style
            format=$format
            constraints=$constraints

            PLANNING PROFILE:
            autoDetectComplexity=${planning.autoDetectComplexity}
            complexitySensitivity=${planning.complexitySensitivity}
            requirePlanApproval=${planning.requirePlanApproval}
            allowAutoContinueExecution=${planning.allowAutoContinueExecution}
            requireValidationBeforeDone=${planning.requireValidationBeforeDone}

            $guardBlock

            $taskBlock

            MEMORY LAYERS:
            LONG_TERM_JSON=$lt
            WORKING_JSON=$wk
        """.trimIndent()
    }

    private fun requiresShortAnswer(responseProfile: com.example.aichalengeapp.agent.profile.ResponseProfile): Boolean {
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
}
