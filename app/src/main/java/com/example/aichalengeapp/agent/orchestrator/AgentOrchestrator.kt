package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskState
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

        return AgentExecutionContext(
            activeProfile = activeProfile,
            invariants = invariants,
            requestKind = decision.kind,
            classificationReason = decision.reason,
            taskState = taskState,
            strategyConfig = strategyConfig,
            longTermJson = longTermJson,
            workingJson = workingJson
        )
    }

    fun buildSystemPrompt(
        basePrompt: String,
        context: AgentExecutionContext
    ): String {
        return promptAssembler.assemble(
            basePrompt = basePrompt,
            profile = context.activeProfile,
            invariants = context.invariants,
            taskState = context.taskState,
            longTermJson = context.longTermJson,
            workingJson = context.workingJson
        )
    }
}
