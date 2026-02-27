package com.example.aichalengeapp.di

import com.example.aichalengeapp.agent.context.ContextManager
import com.example.aichalengeapp.agent.context.SummaryContextManager
import com.example.aichalengeapp.agent.memory.ChatMemoryStore
import com.example.aichalengeapp.agent.memory.DataStoreChatMemoryStore
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

    @Binds @Singleton
    abstract fun bindMemoryStore(impl: DataStoreChatMemoryStore): ChatMemoryStore

    @Binds @Singleton
    abstract fun bindSummarizer(impl: LlmSummarizer): Summarizer

    @Binds @Singleton
    abstract fun bindContextManager(impl: SummaryContextManager): ContextManager
}
