package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
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
            """
            TASK LIFECYCLE:
            stage=${taskState.stage}
            paused=${taskState.paused}
            currentStep=${com.example.aichalengeapp.agent.task.stageToStep(taskState.stage).id}
            planApproved=${taskState.planApproved}
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
