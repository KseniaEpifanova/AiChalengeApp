package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import org.junit.Assert.assertEquals
import org.junit.Test

class BranchingStrategyTest {

    private val strategy = BranchingStrategy()

    @Test
    fun `no checkpoint uses active branch tail when branch has messages`() {
        val memory = AgentMemoryState(
            history = listOf(
                AgentMessage(AgentRole.USER, "h1"),
                AgentMessage(AgentRole.ASSISTANT, "h2")
            ),
            branching = BranchingState(
                checkpointIndex = null,
                branches = mapOf(
                    "A" to BranchData(
                        messages = listOf(
                            AgentMessage(AgentRole.USER, "a1"),
                            AgentMessage(AgentRole.ASSISTANT, "a2"),
                            AgentMessage(AgentRole.USER, "a3")
                        )
                    )
                ),
                activeBranchId = "A"
            )
        )

        val plan = strategy.build(memory, StrategyConfig.Branching(branchId = "A", tailMessageCount = 2))

        assertEquals(listOf("a2", "a3"), plan.messagesForLlm.map { it.content })
    }

    @Test
    fun `no checkpoint falls back to history when branch is empty`() {
        val memory = AgentMemoryState(
            history = listOf(
                AgentMessage(AgentRole.USER, "h1"),
                AgentMessage(AgentRole.ASSISTANT, "h2"),
                AgentMessage(AgentRole.USER, "h3")
            ),
            branching = BranchingState(
                checkpointIndex = null,
                branches = mapOf("A" to BranchData(messages = emptyList())),
                activeBranchId = "A"
            )
        )

        val plan = strategy.build(memory, StrategyConfig.Branching(branchId = "A", tailMessageCount = 2))

        assertEquals(listOf("h2", "h3"), plan.messagesForLlm.map { it.content })
    }

    @Test
    fun `with checkpoint returns base plus branch tail`() {
        val memory = AgentMemoryState(
            history = listOf(
                AgentMessage(AgentRole.USER, "h1"),
                AgentMessage(AgentRole.ASSISTANT, "h2"),
                AgentMessage(AgentRole.USER, "h3"),
                AgentMessage(AgentRole.ASSISTANT, "h4")
            ),
            branching = BranchingState(
                checkpointIndex = 2,
                branches = mapOf(
                    "B" to BranchData(
                        messages = listOf(
                            AgentMessage(AgentRole.USER, "b1"),
                            AgentMessage(AgentRole.ASSISTANT, "b2"),
                            AgentMessage(AgentRole.USER, "b3")
                        )
                    )
                ),
                activeBranchId = "B"
            )
        )

        val plan = strategy.build(memory, StrategyConfig.Branching(branchId = "B", tailMessageCount = 2))

        assertEquals(listOf("h1", "h2", "b2", "b3"), plan.messagesForLlm.map { it.content })
    }
}
