package com.example.aichalengeapp.di

import com.example.aichalengeapp.repo.ChatRepository
import com.example.aichalengeapp.repo.ChatRepositoryImpl
import com.example.aichalengeapp.repo.DataStoreLlmSettingsStore
import com.example.aichalengeapp.repo.EmbeddingRepository
import com.example.aichalengeapp.repo.EmbeddingRepositoryImpl
import com.example.aichalengeapp.repo.LlmSettingsStore
import com.example.aichalengeapp.repo.LocalLlmRepository
import com.example.aichalengeapp.repo.OllamaLocalLlmRepository
import com.example.aichalengeapp.retrieval.DocumentRetriever
import com.example.aichalengeapp.retrieval.DocumentRetrieverImpl
import com.example.aichalengeapp.retrieval.QueryEmbeddingProvider
import com.example.aichalengeapp.retrieval.QueryEmbeddingProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLlmRepository(
        impl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindLocalLlmRepository(
        impl: OllamaLocalLlmRepository
    ): LocalLlmRepository

    @Binds
    @Singleton
    abstract fun bindLlmSettingsStore(
        impl: DataStoreLlmSettingsStore
    ): LlmSettingsStore

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(
        impl: EmbeddingRepositoryImpl
    ): EmbeddingRepository

    @Binds
    @Singleton
    abstract fun bindQueryEmbeddingProvider(
        impl: QueryEmbeddingProviderImpl
    ): QueryEmbeddingProvider

    @Binds
    @Singleton
    abstract fun bindDocumentRetriever(
        impl: DocumentRetrieverImpl
    ): DocumentRetriever
}
