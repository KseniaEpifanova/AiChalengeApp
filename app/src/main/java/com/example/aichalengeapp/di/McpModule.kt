package com.example.aichalengeapp.di

import com.example.aichalengeapp.BuildConfig
import com.example.aichalengeapp.mcp.McpServerConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object McpModule {

    @Provides
    @Singleton
    fun provideMcpServerConfig(): McpServerConfig = McpServerConfig.remote(BuildConfig.MCP_BASE_URL)
}
