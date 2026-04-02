package com.example.aichalengeapp.di

import com.example.aichalengeapp.support.JsonSupportContextRepository
import com.example.aichalengeapp.support.JsonSupportKnowledgeRepository
import com.example.aichalengeapp.support.SupportAssistant
import com.example.aichalengeapp.support.SupportAssistantImpl
import com.example.aichalengeapp.support.SupportContextRepository
import com.example.aichalengeapp.support.SupportKnowledgeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SupportModule {

    @Binds
    @Singleton
    abstract fun bindSupportAssistant(impl: SupportAssistantImpl): SupportAssistant

    @Binds
    @Singleton
    abstract fun bindSupportKnowledgeRepository(impl: JsonSupportKnowledgeRepository): SupportKnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindSupportContextRepository(impl: JsonSupportContextRepository): SupportContextRepository
}
