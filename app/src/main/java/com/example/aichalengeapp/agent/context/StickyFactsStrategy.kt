package com.example.aichalengeapp.agent.context

import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.AgentRole
import javax.inject.Inject

class StickyFactsStrategy @Inject constructor() : ContextStrategy {

    override fun build(memory: AgentMemoryState, config: StrategyConfig): ContextPlan {
        val factsCfg = config as? StrategyConfig.StickyFacts
            ?: error("StickyFactsStrategy requires StrategyConfig.StickyFacts")

        val tail = memory.history
            .filter { it.role != AgentRole.SYSTEM }
            .takeLast(factsCfg.tailMessageCount)

        val withFacts = buildList {
            if (memory.factsJson.isNotBlank()) {
                add(
                    AgentMessage(
                        role = AgentRole.SYSTEM,
                        content = "FACTS (key-value memory, JSON):\n${memory.factsJson}"
                    )
                )
            }
            addAll(tail)
        }

        return ContextPlan(
            messagesForLlm = withFacts,
            debugLabel = "StickyFacts(tail=${factsCfg.tailMessageCount}, facts=${memory.factsJson.isNotBlank()})"
        )
    }
}
