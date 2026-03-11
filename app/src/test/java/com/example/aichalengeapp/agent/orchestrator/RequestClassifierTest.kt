package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.ComplexitySensitivity
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestClassifierTest {

    private val classifier = RequestClassifier()

    private fun mobileDeveloper(
        sensitivity: ComplexitySensitivity = ComplexitySensitivity.HIGH,
        autoDetect: Boolean = true
    ): AssistantProfile {
        return AssistantProfile.mobileDeveloper().copy(
            planningProfile = AssistantProfile.mobileDeveloper().planningProfile.copy(
                autoDetectComplexity = autoDetect,
                complexitySensitivity = sensitivity
            )
        )
    }

    private fun casualProfile(
        sensitivity: ComplexitySensitivity = ComplexitySensitivity.MEDIUM,
        autoDetect: Boolean = true
    ): AssistantProfile {
        return AssistantProfile(
            id = "casual_user",
            name = "Casual User",
            responseProfile = ResponseProfile(
                style = "Friendly",
                format = "Short",
                constraints = "Brief answers"
            ),
            planningProfile = PlanningProfile(
                autoDetectComplexity = autoDetect,
                complexitySensitivity = sensitivity,
                requirePlanApproval = false,
                allowAutoContinueExecution = false,
                requireValidationBeforeDone = false
            )
        )
    }

    @Test
    fun `quick question is simple for mobile developer high`() {
        val decision = classifier.classifyForProfile("Который сейчас час?", mobileDeveloper())
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `russian design request is complex for mobile developer high`() {
        val decision = classifier.classifyForProfile("спроектируй экран списка фильмов", mobileDeveloper())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `help design variant is also complex for mobile developer high`() {
        val decision = classifier.classifyForProfile("Помоги спроектировать экран списка фильмов", mobileDeveloper())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `how to make screen with search is complex for mobile developer`() {
        val decision = classifier.classifyForProfile("Как сделать экран списка фильмов с поиском", mobileDeveloper())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `screen with search and filters is complex for mobile developer high`() {
        val decision = classifier.classifyForProfile("Сделай экран списка фильмов с поиском и фильтрами", mobileDeveloper())
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `help build screen is complex for low sensitivity with detailed profile bias`() {
        val decision = classifier.classifyForProfile(
            "Помоги сделать экран",
            mobileDeveloper(sensitivity = ComplexitySensitivity.LOW)
        )
        assertEquals(RequestKind.COMPLEX, decision.kind)
    }

    @Test
    fun `casual profile can classify same message as simple`() {
        val decision = classifier.classifyForProfile(
            "Помоги спроектировать экран",
            casualProfile(sensitivity = ComplexitySensitivity.LOW)
        )
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `auto detect disabled keeps complex request simple`() {
        val decision = classifier.classifyForProfile(
            "Design and implement screen flow with state and navigation",
            mobileDeveloper(autoDetect = false)
        )
        assertEquals(RequestKind.SIMPLE, decision.kind)
    }

    @Test
    fun `legacy classify api remains compatible`() {
        val kind = classifier.classify(
            message = "Design login screen architecture",
            planningProfile = PlanningProfile(complexitySensitivity = ComplexitySensitivity.MEDIUM)
        )
        assertEquals(RequestKind.COMPLEX, kind)
    }
}
