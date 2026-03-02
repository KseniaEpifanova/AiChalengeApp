package com.example.aichalengeapp.agent.context

sealed class StrategyConfig {

    abstract val tailMessageCount: Int

    data class SlidingWindow(
        override val tailMessageCount: Int = 12
    ) : StrategyConfig()

    data class StickyFacts(
        override val tailMessageCount: Int = 12
    ) : StrategyConfig()

    data class Branching(
        val branchId: String,
        override val tailMessageCount: Int = 50
    ) : StrategyConfig()
}
