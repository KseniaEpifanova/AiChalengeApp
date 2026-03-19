package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    private val assembler = PromptAssembler()

    @Test
    fun `includes profile configuration`() {
        val contextPrompt = assembler.buildContextPrompt(
            basePrompt = "Base",
            planningProfile = AssistantProfile.default().planningProfile,
            invariants = InvariantsProfile(),
            taskState = null,
            longTermJson = "{}",
            workingJson = "{}"
        )
        val profilePrompt = assembler.buildProfilePrompt(AssistantProfile.default())

        assertTrue(contextPrompt.contains("PLANNING PROFILE"))
        assertTrue(profilePrompt.contains("RESPONSE PROFILE (HIGHEST PRIORITY)"))
        assertTrue(profilePrompt.contains("Style requirement"))
    }

    @Test
    fun `includes invariants only when profile is not empty`() {
        val profile = AssistantProfile.default()
        val invariants = InvariantsProfile("Compose only", "No paid APIs")

        val withGuard = assembler.buildContextPrompt("Base", profile.planningProfile, invariants, null, "{}", "{}")
        val withoutGuard = assembler.buildContextPrompt("Base", profile.planningProfile, InvariantsProfile(), null, "{}", "{}")

        assertTrue(withGuard.contains("INVARIANT GUARD"))
        assertFalse(withoutGuard.contains("INVARIANT GUARD"))
    }

    @Test
    fun `includes task state when provided`() {
        val prompt = assembler.buildContextPrompt(
            basePrompt = "Base",
            planningProfile = AssistantProfile.default().planningProfile,
            invariants = InvariantsProfile(),
            taskState = TaskState(goal = "G", stage = TaskStage.EXECUTION),
            longTermJson = "{}",
            workingJson = "{}"
        )

        assertTrue(prompt.contains("TASK LIFECYCLE"))
        assertTrue(prompt.contains("EXECUTION"))
    }

    @Test
    fun `uses validation response contract when stage is validation`() {
        val prompt = assembler.buildContextPrompt(
            basePrompt = "Base",
            planningProfile = AssistantProfile.default().planningProfile,
            invariants = InvariantsProfile(),
            taskState = TaskState(goal = "G", stage = TaskStage.VALIDATION),
            longTermJson = "{}",
            workingJson = "{}"
        )

        assertTrue(prompt.contains("STRICT STAGE CONTRACT"))
        assertTrue(prompt.contains("Current task stage: VALIDATION"))
        assertTrue(prompt.contains("Allowed behavior in VALIDATION"))
        assertTrue(prompt.contains("Forbidden behavior in VALIDATION"))
        assertTrue(prompt.contains("do not continue implementation"))
    }

    @Test
    fun `short-answer profile adds strict prompt-only brevity rules`() {
        val prompt = assembler.buildProfilePrompt(
            AssistantProfile(
                id = "short",
                name = "Short",
                responseProfile = ResponseProfile(
                    style = "Friendly",
                    format = "Short and structured",
                    constraints = "Short answers only"
                ),
                planningProfile = PlanningProfile()
            )
        )

        assertTrue(prompt.contains("You MUST answer briefly"))
        assertTrue(prompt.contains("Maximum length: 3 to 5 sentences"))
        assertFalse(prompt.contains("rewrite it shorter"))
    }

    @Test
    fun `empty profile does not inject default formatting`() {
        val prompt = assembler.buildProfilePrompt(
            AssistantProfile(
                id = "blank",
                name = "Blank",
                responseProfile = ResponseProfile(),
                planningProfile = PlanningProfile()
            )
        )

        assertFalse(prompt.contains("Short and structured"))
        assertFalse(prompt.contains("Friendly, supportive, calm"))
    }
}
