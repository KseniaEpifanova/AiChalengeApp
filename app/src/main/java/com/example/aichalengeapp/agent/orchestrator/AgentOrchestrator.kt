package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import com.example.aichalengeapp.debug.TaskTrace
import javax.inject.Inject

class AgentOrchestrator @Inject constructor(
    private val profileResolver: ProfileResolver,
    private val requestClassifier: RequestClassifier,
    private val promptAssembler: PromptAssembler
) {

    fun buildExecutionContext(
        profiles: List<AssistantProfile>,
        activeProfileId: String?,
        invariants: InvariantsProfile,
        taskState: TaskState?,
        strategyConfig: StrategyConfig,
        message: String,
        longTermJson: String,
        workingJson: String
    ): AgentExecutionContext {
        val activeProfile = profileResolver.resolve(profiles, activeProfileId)
        val decision = requestClassifier.classifyForProfile(message, activeProfile)
        val hasActiveTask = taskState != null &&
            taskState.stage != TaskStage.DONE &&
            taskState.stage != TaskStage.CANCELLED
        val effectiveKind = if (hasActiveTask) RequestKind.TASK else decision.kind
        val effectiveReason = if (hasActiveTask) {
            "Active task ${taskState?.stage} forces TASK routing"
        } else {
            decision.reason
        }
        TaskTrace.d(
            "event" to "orchestrator_context_built",
            "source" to "chat",
            "taskId" to TaskTrace.taskId(taskState),
            "profileId" to activeProfile.id,
            "profileName" to activeProfile.name,
            "requestKind" to effectiveKind,
            "reason" to effectiveReason,
            "taskStage" to taskState?.stage
        )

        return AgentExecutionContext(
            activeProfile = activeProfile,
            invariants = invariants,
            requestKind = effectiveKind,
            classificationReason = effectiveReason,
            taskState = taskState,
            strategyConfig = strategyConfig,
            longTermJson = longTermJson,
            workingJson = workingJson
        )
    }

    fun buildContextSystemPrompt(
        basePrompt: String,
        context: AgentExecutionContext
    ): String {
        return promptAssembler.buildContextPrompt(
            basePrompt = basePrompt,
            planningProfile = context.activeProfile.planningProfile,
            invariants = context.invariants,
            taskState = context.taskState,
            longTermJson = context.longTermJson,
            workingJson = context.workingJson
        )
    }

    fun buildProfileSystemPrompt(context: AgentExecutionContext): String {
        return promptAssembler.buildProfilePrompt(context.activeProfile)
    }
}
