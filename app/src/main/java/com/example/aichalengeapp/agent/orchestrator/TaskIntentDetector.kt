package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import com.example.aichalengeapp.repo.ChatRepository
import javax.inject.Inject

class TaskIntentDetector @Inject constructor(
    private val llmRepository: ChatRepository
) {

    data class IntentDecision(
        val intent: TaskChatIntent,
        val rawLabel: String
    )

    suspend fun detect(
        message: String,
        activeProfile: AssistantProfile,
        taskState: TaskState?
    ): IntentDecision {
        val normalized = message.trim()
        if (normalized.isEmpty()) {
            return IntentDecision(TaskChatIntent.NONE, "NONE")
        }

        val profileBlock = """
            PROFILE:
            id=${activeProfile.id}
            name=${activeProfile.name}
            style=${activeProfile.responseProfile.style.ifBlank { "-" }}
            format=${activeProfile.responseProfile.format.ifBlank { "-" }}
            constraints=${activeProfile.responseProfile.constraints.ifBlank { "-" }}
            planning.autoDetectComplexity=${activeProfile.planningProfile.autoDetectComplexity}
            planning.complexitySensitivity=${activeProfile.planningProfile.complexitySensitivity}
        """.trimIndent()

        val taskBlock = if (taskState == null) {
            "TASK_CONTEXT: no active task"
        } else {
            """
            TASK_CONTEXT:
            goal=${taskState.goal}
            stage=${taskState.stage}
            paused=${taskState.paused}
            planApproved=${taskState.planApproved}
            """.trimIndent()
        }

        val systemPrompt = """
            You are a strict intent classifier for a task lifecycle orchestrator.
            Classify USER_MESSAGE into exactly one label:
            START_COMPLEX_TASK, APPROVE_PLAN, CONTINUE_TASK, REQUEST_VALIDATION, FINISH_TASK, CANCEL_TASK, NONE.
            Rules:
            - Return ONLY one label.
            - No explanations, no extra text.
            - Use meaning, not exact words.
            - Respect TASK_CONTEXT and PROFILE.
            - If there is no clear lifecycle intent, return NONE.
            - If task is active and message asks to end/stop it, prefer CANCEL_TASK.
            - If task is active and user confirms the plan, prefer APPROVE_PLAN.
            - If task is active and user asks to proceed, prefer CONTINUE_TASK.
            - If task is active and user asks to check results, prefer REQUEST_VALIDATION.
            - If task is active and user asks to finish/complete, prefer FINISH_TASK.
            - If no active task and user asks to build/design/implement multi-step work, prefer START_COMPLEX_TASK.
        """.trimIndent()

        val userPrompt = """
            $profileBlock

            $taskBlock

            USER_MESSAGE:
            $normalized
        """.trimIndent()

        val result = llmRepository.ask(
            messages = listOf(
                AgentMessage(AgentRole.SYSTEM, systemPrompt),
                AgentMessage(AgentRole.USER, userPrompt)
            ),
            maxOutputTokens = 8
        )

        val raw = result.text.trim().lineSequence().firstOrNull().orEmpty()
        val parsed = parseIntent(raw)
        return IntentDecision(parsed, raw)
    }

    private fun parseIntent(raw: String): TaskChatIntent {
        val normalized = raw.trim().uppercase()
        return TaskChatIntent.entries.firstOrNull { it.name == normalized } ?: TaskChatIntent.NONE
    }
}
