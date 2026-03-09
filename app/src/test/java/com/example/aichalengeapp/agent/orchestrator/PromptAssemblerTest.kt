package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    private val assembler = PromptAssembler()

    @Test
    fun `includes profile configuration`() {
        val prompt = assembler.assemble(
            basePrompt = "Base",
            profile = AssistantProfile.default(),
            invariants = InvariantsProfile(),
            taskState = null,
            longTermJson = "{}",
            workingJson = "{}"
        )

        assertTrue(prompt.contains("RESPONSE PROFILE"))
        assertTrue(prompt.contains("PLANNING PROFILE"))
    }

    @Test
    fun `includes invariants only when profile is not empty`() {
        val profile = AssistantProfile.default()
        val invariants = InvariantsProfile("Compose only", "No paid APIs")

        val withGuard = assembler.assemble("Base", profile, invariants, null, "{}", "{}")
        val withoutGuard = assembler.assemble("Base", profile, InvariantsProfile(), null, "{}", "{}")

        assertTrue(withGuard.contains("INVARIANT GUARD"))
        assertFalse(withoutGuard.contains("INVARIANT GUARD"))
    }

    @Test
    fun `includes task state when provided`() {
        val prompt = assembler.assemble(
            basePrompt = "Base",
            profile = AssistantProfile.default(),
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
        val prompt = assembler.assemble(
            basePrompt = "Base",
            profile = AssistantProfile.default(),
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
}
