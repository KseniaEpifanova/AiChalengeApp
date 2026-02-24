package com.example.aichalengeapp.di

import com.example.aichalengeapp.repo.ChatRepository
import com.example.aichalengeapp.repo.ChatRepositoryImpl
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
}