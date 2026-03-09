package com.example.aichalengeapp.agent.orchestrator

import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileResolverTest {

    private val resolver = ProfileResolver()

    @Test
    fun `active profile is returned when found`() {
        val a = AssistantProfile("a", "A", ResponseProfile(), PlanningProfile(), isDefault = true)
        val b = AssistantProfile("b", "B", ResponseProfile(), PlanningProfile(), isDefault = false)

        val resolved = resolver.resolve(listOf(a, b), "b")
        assertEquals("b", resolved.id)
    }

    @Test
    fun `fallback to default when active missing`() {
        val a = AssistantProfile("a", "A", ResponseProfile(), PlanningProfile(), isDefault = true)
        val b = AssistantProfile("b", "B", ResponseProfile(), PlanningProfile(), isDefault = false)

        val resolved = resolver.resolve(listOf(a, b), "missing")
        assertEquals("a", resolved.id)
    }

    @Test
    fun `fallback to first when no default`() {
        val a = AssistantProfile("a", "A", ResponseProfile(), PlanningProfile(), isDefault = false)
        val b = AssistantProfile("b", "B", ResponseProfile(), PlanningProfile(), isDefault = false)

        val resolved = resolver.resolve(listOf(a, b), null)
        assertEquals("a", resolved.id)
    }
}
