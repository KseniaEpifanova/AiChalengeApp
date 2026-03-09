package com.example.aichalengeapp.di

import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.facts.LlmFactsUpdater
import com.example.aichalengeapp.agent.guard.DataStoreInvariantsStore
import com.example.aichalengeapp.agent.guard.InvariantsStore
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.agent.memory.DataStoreAgentMemoryStore
import com.example.aichalengeapp.agent.memory.DataStoreLongTermMemoryStore
import com.example.aichalengeapp.agent.memory.DataStoreWorkingMemoryStore
import com.example.aichalengeapp.agent.memory.LongTermMemoryStore
import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.agent.profile.AssistantProfilesStore
import com.example.aichalengeapp.agent.profile.DataStoreAssistantProfilesStore
import com.example.aichalengeapp.agent.profile.DataStoreUserProfileStore
import com.example.aichalengeapp.agent.profile.UserProfileStore
import com.example.aichalengeapp.agent.summary.LlmSummarizer
import com.example.aichalengeapp.agent.summary.Summarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSummarizer(impl: LlmSummarizer): Summarizer

    @Binds
    @Singleton
    abstract fun bindMemoryStore(impl: DataStoreAgentMemoryStore): AgentMemoryStore

    @Binds
    @Singleton
    abstract fun bindFactsUpdater(impl: LlmFactsUpdater): FactsUpdater

    @Binds
    @Singleton
    abstract fun bindWorkingMemoryStore(impl: DataStoreWorkingMemoryStore): WorkingMemoryStore

    @Binds
    @Singleton
    abstract fun bindLongTermMemoryStore(impl: DataStoreLongTermMemoryStore): LongTermMemoryStore

    @Binds
    @Singleton
    abstract fun bindUserProfileStore(impl: DataStoreUserProfileStore): UserProfileStore

    @Binds
    @Singleton
    abstract fun bindLegacyInvariantsStore(
        impl: com.example.aichalengeapp.agent.invariants.DataStoreInvariantsStore
    ): com.example.aichalengeapp.agent.invariants.InvariantsStore

    @Binds
    @Singleton
    abstract fun bindGuardInvariantsStore(impl: DataStoreInvariantsStore): InvariantsStore

    @Binds
    @Singleton
    abstract fun bindAssistantProfilesStore(impl: DataStoreAssistantProfilesStore): AssistantProfilesStore
}
