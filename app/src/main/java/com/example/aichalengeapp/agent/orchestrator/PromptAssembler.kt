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

            RESPONSE PROFILE:
            style=${response.style.ifBlank { "friendly" }}
            format=${response.format.ifBlank { "short and structured" }}
            constraints=${response.constraints.ifBlank { "none" }}

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
}
