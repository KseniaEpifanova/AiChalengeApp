package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentOrchestratorTest {

    @Test
    fun `active task forces request kind task`() {
        val orchestrator = AgentOrchestrator(
            profileResolver = ProfileResolver(),
            requestClassifier = RequestClassifier(),
            promptAssembler = PromptAssembler()
        )

        val context = orchestrator.buildExecutionContext(
            profiles = listOf(AssistantProfile.default()),
            activeProfileId = AssistantProfile.DEFAULT_ID,
            invariants = InvariantsProfile(),
            taskState = TaskState(goal = "G", stage = TaskStage.EXECUTION),
            strategyConfig = StrategyConfig.SlidingWindow(),
            message = "what is weather",
            longTermJson = "{}",
            workingJson = "{}"
        )

        assertEquals(RequestKind.TASK, context.requestKind)
    }
}
