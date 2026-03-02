package com.example.aichalengeapp.agent.context

interface ContextStrategy {
    fun build(memory: AgentMemoryState, config: StrategyConfig): ContextPlan
}
