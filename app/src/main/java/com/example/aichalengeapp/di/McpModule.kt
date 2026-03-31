package com.example.aichalengeapp.di

import com.example.aichalengeapp.BuildConfig
import com.example.aichalengeapp.mcp.McpServerRegistry
import com.example.aichalengeapp.mcp.McpServerConfig
import com.example.aichalengeapp.mcp.McpServerTarget
import com.example.aichalengeapp.mcp.currency.FrankfurterApi
import com.example.aichalengeapp.mcp.currency.FrankfurterHttpApi
import com.example.aichalengeapp.mcp.currency.McpCurrencyService
import com.example.aichalengeapp.mcp.currency.McpCurrencyServiceImpl
import com.example.aichalengeapp.mcp.git.McpGitService
import com.example.aichalengeapp.mcp.git.McpGitServiceImpl
import com.example.aichalengeapp.mcp.pipeline.McpPipelineService
import com.example.aichalengeapp.mcp.pipeline.McpPipelineServiceImpl
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
    fun provideMcpServerRegistry(): McpServerRegistry {
        val singleServerConfig = McpServerConfig.remote(BuildConfig.MCP_BASE_URL)
        return McpServerRegistry(
            configs = mapOf(
                McpServerTarget.DEVELOPER to singleServerConfig
            )
        )
    }

    @Provides
    @Singleton
    fun provideFrankfurterApi(impl: FrankfurterHttpApi): FrankfurterApi = impl

    @Provides
    @Singleton
    fun provideMcpCurrencyService(impl: McpCurrencyServiceImpl): McpCurrencyService = impl

    @Provides
    @Singleton
    fun provideMcpGitService(impl: McpGitServiceImpl): McpGitService = impl

    @Provides
    @Singleton
    fun provideMcpPipelineService(impl: McpPipelineServiceImpl): McpPipelineService = impl
}
