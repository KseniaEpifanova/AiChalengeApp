package com.example.aichalengeapp.di

import com.example.aichalengeapp.agent.SimpleCharTokenEstimator
import com.example.aichalengeapp.agent.TokenEstimator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenModule {

    @Binds
    @Singleton
    abstract fun bindTokenEstimator(
        impl: SimpleCharTokenEstimator
    ): TokenEstimator
}
