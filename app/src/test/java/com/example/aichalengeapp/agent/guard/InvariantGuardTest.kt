package com.example.aichalengeapp.agent.guard

import org.junit.Assert.assertTrue
import org.junit.Test

class InvariantGuardTest {

    private val guard = InvariantGuard()

    @Test
    fun `no violation when response is compliant`() {
        val profile = InvariantsProfile(techDecisions = "Compose only", businessRules = "")
        val result = guard.check("Use Compose LazyColumn", profile)
        assertTrue(result is GuardResult.Ok)
    }

    @Test
    fun `violation detected for forbidden tech decision`() {
        val profile = InvariantsProfile(techDecisions = "no RxJava", businessRules = "")
        val result = guard.check("You can use RxJava for streams", profile)
        assertTrue(result is GuardResult.Violation)
    }

    @Test
    fun `violation contains informative message`() {
        val profile = InvariantsProfile(techDecisions = "Compose only", businessRules = "")
        val result = guard.check("Use XML layout and fragment", profile)
        assertTrue(result is GuardResult.Violation)
        val violation = result as GuardResult.Violation
        assertTrue(violation.invariant.contains("Compose"))
    }

    @Test
    fun `empty invariants keep guard inactive`() {
        val result = guard.check("Use whatever stack fits", InvariantsProfile())
        assertTrue(result is GuardResult.Ok)
    }
}
