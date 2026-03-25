package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestClassifierTest {

    private val classifier = RequestClassifier()

    private fun profile(
        sensitivity: ComplexitySensitivity = ComplexitySensitivity.MEDIUM,
        autoDetect: Boolean = true
    ): AssistantProfile {
        return AssistantProfile.mobileDeveloper().copy(
            planningProfile = PlanningProfile(
                autoDetectComplexity = autoDetect,
                complexitySensitivity = sensitivity,
                requirePlanApproval = true,
                allowAutoContinueExecution = false,
                requireValidationBeforeDone = true
            )
        )
    }

    @Test
    fun `greeting is simple`() {
        val decision = classifier.classifyForProfile("привет", profile())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `identity question is simple`() {
        val decision = classifier.classifyForProfile("Кто ты?", profile())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `explanatory request is simple`() {
        val decision = classifier.classifyForProfile("How does ChatAgent work?", profile())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `exploration request is simple`() {
        val decision = classifier.classifyForProfile("Я хочу понять, как устроен чат", profile())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `execution request is complex`() {
        val decision = classifier.classifyForProfile("Исправь баг", profile())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `english execution request is complex`() {
        val decision = classifier.classifyForProfile("Implement reranker", profile())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `simple message without execution evidence stays simple`() {
        val decision = classifier.classifyForProfile("Что ты умеешь?", profile())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `auto detect disabled keeps execution request simple`() {
        val decision = classifier.classifyForProfile("Fix bug in task trigger", profile(autoDetect = false))
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `legacy classify api remains compatible for explicit execution`() {
        val kind = classifier.classify(
            message = "Build settings screen",
            planningProfile = PlanningProfile(complexitySensitivity = ComplexitySensitivity.MEDIUM)
        )
        assertEquals(RequestKind.COMPLEX, kind)
    }
}
