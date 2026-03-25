package com.example.aichalengeapp.agent

import com.example.aichalengeapp.agent.context.AgentMemoryState
import com.example.aichalengeapp.agent.context.BranchingStrategy
import com.example.aichalengeapp.agent.context.ContextStrategySelector
import com.example.aichalengeapp.agent.context.SlidingWindowStrategy
import com.example.aichalengeapp.agent.context.StickyFactsStrategy
import com.example.aichalengeapp.agent.context.StrategyConfig
import com.example.aichalengeapp.agent.facts.FactsUpdater
import com.example.aichalengeapp.agent.guard.InvariantGuard
import com.example.aichalengeapp.agent.guard.InvariantsProfile
import com.example.aichalengeapp.agent.guard.InvariantsStore
import com.example.aichalengeapp.agent.memory.AgentMemoryStore
import com.example.aichalengeapp.agent.memory.LongTermMemoryStore
import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.agent.orchestrator.AgentOrchestrator
import com.example.aichalengeapp.agent.orchestrator.ProfileResolver
import com.example.aichalengeapp.agent.orchestrator.PromptAssembler
import com.example.aichalengeapp.agent.orchestrator.RequestClassifier
import com.example.aichalengeapp.agent.orchestrator.TaskChatIntent
import com.example.aichalengeapp.agent.orchestrator.TaskConflictDetector
import com.example.aichalengeapp.agent.orchestrator.TaskIntentDetector
import com.example.aichalengeapp.agent.profile.AssistantProfile
import com.example.aichalengeapp.agent.profile.AssistantProfilesStore
import com.example.aichalengeapp.agent.profile.PlanningProfile
import com.example.aichalengeapp.agent.profile.ResponseProfile
import com.example.aichalengeapp.agent.profile.UserProfile
import com.example.aichalengeapp.agent.profile.UserProfileStore
import com.example.aichalengeapp.agent.task.TaskManager
import com.example.aichalengeapp.agent.task.TaskStage
import com.example.aichalengeapp.agent.task.TaskStateMachine
import com.example.aichalengeapp.agent.task.TaskPlanner
import com.example.aichalengeapp.data.AgentMessage
import com.example.aichalengeapp.data.LlmResult
import com.example.aichalengeapp.mcp.currency.CurrencyRequestParser
import com.example.aichalengeapp.mcp.currency.CurrencyToolResponse
import com.example.aichalengeapp.mcp.currency.CurrencyToolRouter
import com.example.aichalengeapp.mcp.currency.McpCurrencyService
import com.example.aichalengeapp.mcp.orchestration.CompositeRequestRouter
import com.example.aichalengeapp.mcp.orchestration.McpOrchestrator
import com.example.aichalengeapp.mcp.pipeline.McpPipelineService
import com.example.aichalengeapp.mcp.pipeline.PipelineToolResponse
import com.example.aichalengeapp.mcp.pipeline.PipelineToolRouter
import com.example.aichalengeapp.retrieval.DocumentRetriever
import com.example.aichalengeapp.retrieval.KnowledgeRouter
import com.example.aichalengeapp.retrieval.RetrievalMode
import com.example.aichalengeapp.retrieval.RetrievalPromptBuilder
import com.example.aichalengeapp.retrieval.RetrievedChunk
import com.example.aichalengeapp.repo.ChatRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

@Ignore("Requires Android JSON runtime or instrumented/Robolectric environment")
class ChatAgentTest {

    @Test
    fun `simple request does not auto start task`() = runSuspending {
        val fixture = fixture("All good")
        fixture.agent.init()
        fixture.agent.handleUserMessage("What is Kotlin?", StrategyConfig.SlidingWindow())
        assertNull(fixture.agent.getTaskState())
    }

    @Test
    fun `complex request auto starts planning task`() = runSuspending {
        val fixture = fixture("Planning reply")
        fixture.agent.init()
        fixture.agent.handleUserMessage("Design and implement a multi-module architecture with constraints", StrategyConfig.SlidingWindow())
        val state = fixture.agent.getTaskState()
        assertNotNull(state)
        assertEquals(TaskStage.PLANNING, state?.stage)
    }

    @Test
    fun `switching profile affects prompt immediately`() = runSuspending {
        val fixture = fixture("Any reply")
        fixture.agent.init()
        val profiles = listOf(
            AssistantProfile.default(),
            AssistantProfile(
                id = "strict",
                name = "Strict",
                responseProfile = ResponseProfile(style = "Strict style", format = "Checklist", constraints = "No fluff"),
                planningProfile = PlanningProfile(),
                isDefault = false
            )
        )
        fixture.agent.saveProfiles(profiles)
        fixture.agent.setActiveProfile("strict")

        fixture.agent.handleUserMessage("hello", StrategyConfig.SlidingWindow())

        val systemPrompt = fixture.repo.lastMessages.firstOrNull()?.content.orEmpty()
        assertTrue(systemPrompt.contains("Strict style"))
    }

    @Test
    fun `approve plan through chat moves to execution`() = runSuspending {
        val fixture = fixture("Execution response")
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleUserMessage("соглашаюсь с планом", StrategyConfig.SlidingWindow())
        val state = fixture.agent.getTaskState()
        assertEquals(TaskStage.EXECUTION, state?.stage)
    }

    @Test
    fun `task intent action continue updates real task state`() = runSuspending {
        val fixture = fixture("Execution response")
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())
        val reply = fixture.agent.handleTaskIntentAction(TaskChatIntent.CONTINUE_TASK, StrategyConfig.SlidingWindow())
        assertTrue(reply.text.isNotBlank())
        val state = fixture.agent.getTaskState()
        assertTrue(state != null)
    }

    @Test
    fun `done task blocks next step intent`() = runSuspending {
        val fixture = fixture("Done response")
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())
        fixture.agent.attemptTransition(TaskStage.VALIDATION)
        fixture.agent.attemptTransition(TaskStage.DONE)

        val reply = fixture.agent.handleTaskIntentAction(TaskChatIntent.CONTINUE_TASK, StrategyConfig.SlidingWindow())
        assertTrue(reply.text.contains("already completed", ignoreCase = true))
        assertEquals(TaskStage.DONE, fixture.agent.getTaskState()?.stage)
    }

    @Test
    fun `validation all good message transitions to done`() = runSuspending {
        val fixture = fixture(
            llmText = "Task completed successfully.",
            classifierMap = mapOf("все устраивает" to "APPROVE_PLAN")
        )
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())
        fixture.agent.attemptTransition(TaskStage.VALIDATION)

        fixture.agent.handleUserMessage("все устраивает", StrategyConfig.SlidingWindow())
        assertEquals(TaskStage.DONE, fixture.agent.getTaskState()?.stage)
    }

    @Test
    fun `validation all good in english transitions to done`() = runSuspending {
        val fixture = fixture(
            llmText = "Task completed successfully.",
            classifierMap = mapOf("all good" to "FINISH_TASK")
        )
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())
        fixture.agent.attemptTransition(TaskStage.VALIDATION)

        fixture.agent.handleUserMessage("all good", StrategyConfig.SlidingWindow())
        assertEquals(TaskStage.DONE, fixture.agent.getTaskState()?.stage)
    }

    @Test
    fun `execution request validation through chat persists validation stage`() = runSuspending {
        val fixture = fixture(
            llmText = "Let's validate the solution now.",
            classifierMap = mapOf("проверим результат" to "REQUEST_VALIDATION")
        )
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())

        fixture.agent.handleUserMessage("проверим результат", StrategyConfig.SlidingWindow())
        assertEquals(TaskStage.VALIDATION, fixture.agent.getTaskState()?.stage)
    }

    @Test
    fun `assistant text is adjusted when state stays execution`() = runSuspending {
        val fixture = fixture(
            llmText = "Task completed successfully. We are done.",
            classifierMap = mapOf("continue" to "NONE")
        )
        fixture.agent.init()
        fixture.agent.startTask("Design movie list screen")
        fixture.agent.handleTaskIntentAction(TaskChatIntent.APPROVE_PLAN, StrategyConfig.SlidingWindow())

        val reply = fixture.agent.handleUserMessage("continue coding", StrategyConfig.SlidingWindow())
        assertEquals(TaskStage.EXECUTION, fixture.agent.getTaskState()?.stage)
        assertTrue(reply.text.contains("stage is still EXECUTION"))
    }

    @Test
    fun `currency request uses mcp tool path and skips llm`() = runSuspending {
        val fixture = fixture("LLM fallback")
        fixture.agent.init()

        val reply = fixture.agent.handleUserMessage("Сколько будет 100 EUR в USD?", StrategyConfig.SlidingWindow())
        assertTrue(reply.debugLabel == "mcp-currency")
    }

    @Test
    fun `combined request uses orchestrator path and skips llm`() = runSuspending {
        val fixture = fixture("LLM fallback")
        fixture.agent.init()

        val reply = fixture.agent.handleUserMessage(
            "Найди посты по слову qui, сделай краткую сводку и сохрани в файл, а потом скажи, сколько будет 100 EUR в USD",
            StrategyConfig.SlidingWindow()
        )

        assertEquals("mcp-orchestrator", reply.debugLabel)
        assertTrue(reply.text.contains("Также 100 EUR"))
        assertTrue(fixture.repo.lastMessages.isEmpty())
    }

    @Test
    fun `unrelated request still uses llm path`() = runSuspending {
        val fixture = fixture("LLM fallback")
        fixture.agent.init()

        val reply = fixture.agent.handleUserMessage("Explain Kotlin sealed classes", StrategyConfig.SlidingWindow())

        assertNull(reply.debugLabel)
        assertTrue(fixture.repo.lastMessages.isNotEmpty())
    }

    @Test
    fun `knowledge request injects retrieved context into llm prompt`() = runSuspending {
        val fixture = fixture("LLM fallback")
        fixture.agent.init()

        fixture.agent.handleUserMessage("How does MCP connection work in this project?", StrategyConfig.SlidingWindow())

        val systemMessages = fixture.repo.lastMessages.filter { it.role == com.example.aichalengeapp.data.AgentRole.SYSTEM }
        assertTrue(systemMessages.any { it.content.contains("RETRIEVED PROJECT KNOWLEDGE") })
        assertTrue(systemMessages.any { it.content.contains("McpClientManager handles initialize") })
    }

    @Test
    fun `ordinary chat question does not inject retrieval prompt`() = runSuspending {
        val fixture = fixture("I am your assistant.")
        fixture.agent.init()

        fixture.agent.handleUserMessage("Who are you?", StrategyConfig.SlidingWindow())

        val systemMessages = fixture.repo.lastMessages.filter { it.role == com.example.aichalengeapp.data.AgentRole.SYSTEM }
        assertTrue(systemMessages.none { it.content.contains("RETRIEVED PROJECT KNOWLEDGE") })
        assertTrue(systemMessages.none { it.content.contains("I don't know based on the retrieved context.") })
    }

    @Test
    fun `knowledge retrieval failure falls back to llm for same message`() = runSuspending {
        val fixture = fixture(
            llmText = "Fallback LLM answer",
            documentRetriever = object : DocumentRetriever {
                override suspend fun retrieve(query: String, mode: RetrievalMode): List<RetrievedChunk> {
                    error("HTTP 404")
                }
            }
        )
        fixture.agent.init()

        val reply = fixture.agent.handleUserMessage("Как работает MCP подключение в проекте?", StrategyConfig.SlidingWindow())

        assertEquals("Fallback LLM answer", reply.text)
        assertTrue(fixture.repo.lastMessages.any { it.role == com.example.aichalengeapp.data.AgentRole.USER && it.content.contains("Как работает MCP подключение в проекте?") })
    }

    private fun runSuspending(block: suspend () -> Unit) = runBlocking {
        withTimeout(5_000) {
            block()
        }
    }

    private fun fixture(
        llmText: String,
        classifierMap: Map<String, String> = emptyMap(),
        documentRetriever: DocumentRetriever? = null
    ): Fixture {
        val repo = FakeRepo(llmText, classifierMap)
        val shortTermStore = FakeAgentMemoryStore()
        val workingStore = FakeWorkingMemoryStore()
        val longTermStore = FakeLongTermMemoryStore()
        val userProfileStore = FakeUserProfileStore()
        val profilesStore = FakeProfilesStore()
        val invariantsStore = FakeInvariantsStore()

        val selector = ContextStrategySelector(SlidingWindowStrategy(), StickyFactsStrategy(), BranchingStrategy())
        val tokenEstimator = SimpleCharTokenEstimator()
        val factsUpdater = object : FactsUpdater {
            override suspend fun updateFacts(existingFactsJson: String, userMessage: String): String = existingFactsJson
        }

        val taskManager = TaskManager(
            workingMemoryStore = workingStore,
            planner = TaskPlanner(),
            stateMachine = TaskStateMachine()
        )

        val agent = ChatAgent(
            llmRepository = repo,
            shortTermStore = shortTermStore,
            workingStore = workingStore,
            longTermStore = longTermStore,
            userProfileStore = userProfileStore,
            profilesStore = profilesStore,
            invariantsStore = invariantsStore,
            selector = selector,
            tokenEstimator = tokenEstimator,
            factsUpdater = factsUpdater,
            taskManager = taskManager,
            invariantGuard = InvariantGuard(),
            profileResolver = ProfileResolver(),
            orchestrator = AgentOrchestrator(ProfileResolver(), RequestClassifier(), PromptAssembler()),
            taskConflictDetector = TaskConflictDetector(),
            taskIntentDetector = TaskIntentDetector(repo),
            compositeRequestRouter = CompositeRequestRouter(PipelineToolRouter(), CurrencyToolRouter(CurrencyRequestParser())),
            mcpOrchestrator = McpOrchestrator(
                pipelineService = object : McpPipelineService {
                    override suspend fun runSearchSummaryPipeline(query: String, filename: String?, limit: Int?): PipelineToolResponse {
                        return PipelineToolResponse.Success(
                            query = query,
                            matchedCount = 2,
                            summary = "Demo summary for $query",
                            savedPath = "pipeline_output/latest_summary.txt",
                            saved = true
                        )
                    }
                },
                currencyService = object : McpCurrencyService {
                    override suspend fun getExchangeRate(base: String, target: String, amount: Double?): CurrencyToolResponse {
                        return if (base.equals("EUR", ignoreCase = true) && target.equals("USD", ignoreCase = true)) {
                            CurrencyToolResponse.Success(
                                base = "EUR",
                                target = "USD",
                                amountRequested = amount ?: 1.0,
                                convertedAmount = if (amount == null) 1.08 else amount * 1.08,
                                rate = 1.08,
                                date = "2026-03-10"
                            )
                        } else {
                            CurrencyToolResponse.InvalidCurrency(base, target)
                        }
                    }

                    override suspend fun getExchangeRateSummary(base: String, target: String): CurrencyToolResponse {
                        return if (base.equals("EUR", ignoreCase = true) && target.equals("USD", ignoreCase = true)) {
                            CurrencyToolResponse.Summary(
                                base = "EUR",
                                target = "USD",
                                sampleCount = 3,
                                latestRate = 1.0842,
                                minRate = 1.0810,
                                maxRate = 1.0861,
                                averageRate = 1.0837,
                                latestTimestamp = "2026-03-11T10:00:00.000Z"
                            )
                        } else {
                            CurrencyToolResponse.Failure("No data for $base/$target")
                        }
                    }
                }
            ),
            pipelineToolRouter = PipelineToolRouter(),
            mcpPipelineService = object : McpPipelineService {
                override suspend fun runSearchSummaryPipeline(query: String, filename: String?, limit: Int?): PipelineToolResponse {
                    return PipelineToolResponse.Success(
                        query = query,
                        matchedCount = 2,
                        summary = "Demo summary for $query",
                        savedPath = "/tmp/pipeline_output/latest_summary.txt",
                        saved = true
                    )
                }
            },
            currencyToolRouter = CurrencyToolRouter(CurrencyRequestParser()),
            mcpCurrencyService = object : McpCurrencyService {
                override suspend fun getExchangeRate(base: String, target: String, amount: Double?): CurrencyToolResponse {
                    return if (base.equals("EUR", ignoreCase = true) && target.equals("USD", ignoreCase = true)) {
                        CurrencyToolResponse.Success(
                            base = "EUR",
                            target = "USD",
                            amountRequested = amount ?: 1.0,
                            convertedAmount = if (amount == null) 1.08 else amount * 1.08,
                            rate = 1.08,
                            date = "2026-03-10"
                        )
                    } else {
                        CurrencyToolResponse.InvalidCurrency(base, target)
                    }
                }

                override suspend fun getExchangeRateSummary(base: String, target: String): CurrencyToolResponse {
                    return if (base.equals("EUR", ignoreCase = true) && target.equals("USD", ignoreCase = true)) {
                        CurrencyToolResponse.Summary(
                            base = "EUR",
                            target = "USD",
                            sampleCount = 3,
                            latestRate = 1.0842,
                            minRate = 1.0810,
                            maxRate = 1.0861,
                            averageRate = 1.0837,
                            latestTimestamp = "2026-03-11T10:00:00.000Z"
                        )
                    } else {
                        CurrencyToolResponse.Failure("No data for $base/$target")
                    }
                }
            },
            knowledgeRouter = KnowledgeRouter(),
            documentRetriever = documentRetriever ?: object : DocumentRetriever {
                override suspend fun retrieve(query: String, mode: RetrievalMode): List<RetrievedChunk> {
                    return if (query.contains("MCP connection", ignoreCase = true) || query.contains("MCP подключение", ignoreCase = true)) {
                        listOf(
                            RetrievedChunk(
                                source = "android-app",
                                titleOrFile = "McpClientManager.kt",
                                section = "connect",
                                chunkId = "mcp-connect-1",
                                strategy = "semantic",
                                text = "McpClientManager handles initialize, session tracking, tools/list and tools/call over HTTP MCP.",
                                similarity = 0.91
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            },
            retrievalPromptBuilder = RetrievalPromptBuilder()
        )

        return Fixture(agent, repo)
    }

    private data class Fixture(
        val agent: ChatAgent,
        val repo: FakeRepo
    )

    private class FakeRepo(
        private val text: String,
        private val classifierMap: Map<String, String>
    ) : ChatRepository {
        var lastMessages: List<AgentMessage> = emptyList()

        override suspend fun ask(messages: List<AgentMessage>, maxOutputTokens: Int?): LlmResult {
            lastMessages = messages
            val system = messages.firstOrNull { it.role == com.example.aichalengeapp.data.AgentRole.SYSTEM }?.content.orEmpty()
            if (system.contains("strict intent classifier")) {
                val userPayload = messages.lastOrNull { it.role == com.example.aichalengeapp.data.AgentRole.USER }?.content.orEmpty().lowercase()
                val mapped = classifierMap.entries.firstOrNull { userPayload.contains(it.key.lowercase()) }?.value ?: "NONE"
                return LlmResult(mapped)
            }
            return LlmResult(text)
        }
    }

    private class FakeAgentMemoryStore : AgentMemoryStore {
        private var state: AgentMemoryState = AgentMemoryState()

        override suspend fun load(): AgentMemoryState = state

        override suspend fun save(state: AgentMemoryState) {
            this.state = state
        }

        override suspend fun clear() {
            state = AgentMemoryState()
        }
    }

    private class FakeWorkingMemoryStore : WorkingMemoryStore {
        private var json: String = ""

        override suspend fun loadJson(): String = json

        override suspend fun saveJson(json: String) {
            this.json = json
        }

        override suspend fun clear() {
            json = ""
        }
    }

    private class FakeLongTermMemoryStore : LongTermMemoryStore {
        private var json: String = ""

        override suspend fun loadJson(): String = json

        override suspend fun saveJson(json: String) {
            this.json = json
        }

        override suspend fun clear() {
            json = ""
        }
    }

    private class FakeUserProfileStore : UserProfileStore {
        private var profile = UserProfile()

        override suspend fun load(): UserProfile = profile

        override suspend fun save(profile: UserProfile) {
            this.profile = profile
        }

        override suspend fun clear() {
            profile = UserProfile()
        }
    }

    private class FakeProfilesStore : AssistantProfilesStore {
        private var profiles: List<AssistantProfile> = listOf(AssistantProfile.default())
        private var activeId: String? = AssistantProfile.DEFAULT_ID

        override suspend fun loadProfiles(): List<AssistantProfile> = profiles

        override suspend fun saveProfiles(profiles: List<AssistantProfile>) {
            this.profiles = profiles
        }

        override suspend fun loadActiveProfileId(): String? = activeId

        override suspend fun saveActiveProfileId(profileId: String) {
            activeId = profileId
        }

        override suspend fun clear() {
            profiles = listOf(AssistantProfile.default())
            activeId = AssistantProfile.DEFAULT_ID
        }
    }

    private class FakeInvariantsStore : InvariantsStore {
        private var profile = InvariantsProfile()

        override suspend fun loadProfile(): InvariantsProfile = profile

        override suspend fun saveProfile(profile: InvariantsProfile) {
            this.profile = profile
        }

        override suspend fun clearProfile() {
            profile = InvariantsProfile()
        }
    }
}
