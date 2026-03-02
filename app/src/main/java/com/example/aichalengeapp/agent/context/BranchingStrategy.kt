package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentRole
import javax.inject.Inject

class BranchingStrategy @Inject constructor() : ContextStrategy {

    override fun build(memory: AgentMemoryState, config: StrategyConfig): ContextPlan {
        val cfg = config as? StrategyConfig.Branching
            ?: error("BranchingStrategy requires StrategyConfig.Branching")

        val historyNoSystem = memory.history.filter { it.role != AgentRole.SYSTEM }

        val checkpoint =
            memory.branching.checkpointIndex ?: // пока чекпоинта нет — просто tail из общей истории
            return ContextPlan(
                messagesForLlm = historyNoSystem.takeLast(cfg.tailMessageCount),
                debugLabel = "Branching(no-checkpoint → tail=${cfg.tailMessageCount}, hist=${historyNoSystem.size})"
            )

        val base = historyNoSystem.take(checkpoint)

        val branchMsgs = memory.branching.branches[cfg.branchId]
            ?.messages
            .orEmpty()
            .filter { it.role != AgentRole.SYSTEM }

        val branchTail = branchMsgs.takeLast(cfg.tailMessageCount)

        return ContextPlan(
            messagesForLlm = base + branchTail,
            debugLabel = "Branching(branch=${cfg.branchId}, base=${base.size}, branchTail=${branchTail.size}, tail=${cfg.tailMessageCount})"
        )
    }
}
