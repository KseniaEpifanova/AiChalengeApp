package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import javax.inject.Inject

class SlidingWindowStrategy @Inject constructor() : ContextStrategy {

    override fun build(memory: AgentMemoryState, config: StrategyConfig): ContextPlan {
        val sliding = config as? StrategyConfig.SlidingWindow
            ?: error("SlidingWindowStrategy requires StrategyConfig.SlidingWindow")

        val tail = memory.history
            .filter { it.role != AgentRole.SYSTEM }
            .takeLast(sliding.tailMessageCount)

        return ContextPlan(
            messagesForLlm = tail,
            debugLabel = "SlidingWindow(tail=${sliding.tailMessageCount})"
        )
    }
}
