package com.example.aichalengeapp.agent.context

import javax.inject.Inject

class ContextStrategySelector @Inject constructor(
    private val sliding: SlidingWindowStrategy,
    private val facts: StickyFactsStrategy,
    private val branching: BranchingStrategy
) {
    fun select(config: StrategyConfig): ContextStrategy =
        when (config) {
            is StrategyConfig.SlidingWindow -> sliding
            is StrategyConfig.StickyFacts -> facts
            is StrategyConfig.Branching -> branching
        }
}
