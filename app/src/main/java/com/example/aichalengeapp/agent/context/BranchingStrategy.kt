package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentRole
import javax.inject.Inject

class BranchingStrategy @Inject constructor() : ContextStrategy {

    override fun build(memory: AgentMemoryState, config: StrategyConfig): ContextPlan {
        val cfg = config as? StrategyConfig.Branching
            ?: error("BranchingStrategy requires StrategyConfig.Branching")

        val historyNoSystem = memory.history.filter { it.role != AgentRole.SYSTEM }
        val branchNoSystem = memory.branching.branches[cfg.branchId]
            ?.messages
            .orEmpty()
            .filter { it.role != AgentRole.SYSTEM }

        val checkpoint =
            memory.branching.checkpointIndex ?: // no checkpoint yet -> prefer active branch tail
            return ContextPlan(
                messagesForLlm = (if (branchNoSystem.isNotEmpty()) {
                    branchNoSystem
                } else {
                    historyNoSystem
                }).takeLast(cfg.tailMessageCount),
                debugLabel = "Branching(no-checkpoint, branch=${cfg.branchId}, branchSize=${branchNoSystem.size}, hist=${historyNoSystem.size}, tail=${cfg.tailMessageCount})"
            )

        val base = historyNoSystem.take(checkpoint)

        val branchTail = branchNoSystem.takeLast(cfg.tailMessageCount)

        return ContextPlan(
            messagesForLlm = base + branchTail,
            debugLabel = "Branching(branch=${cfg.branchId}, base=${base.size}, branchTail=${branchTail.size}, tail=${cfg.tailMessageCount})"
        )
    }
}
