package com.example.aichalengeapp.vm

import com.example.aichalengeapp.support.SupportAnswer
import com.example.aichalengeapp.support.SupportAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty input returns validation error`() = runTest {
        val viewModel = SupportViewModel(FakeSupportAssistant())

        viewModel.submit()

        assertEquals("Enter a support question.", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `submit enters loading state`() = runTest {
        val viewModel = SupportViewModel(FakeSupportAssistant(delayMs = 1_000))
        viewModel.updateQuestion("Why is my LOCAL model timing out?")

        viewModel.submit()
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `submit success populates answer`() = runTest {
        val answer = SupportAnswer(
            text = "Support answer",
            sources = listOf("LOCAL model timeout"),
            contextSummary = "User Casey"
        )
        val viewModel = SupportViewModel(FakeSupportAssistant(answer = answer))
        viewModel.updateQuestion("Why is my LOCAL model timing out?")

        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Support answer", viewModel.uiState.value.answer?.text)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `submit failure exposes error state`() = runTest {
        val viewModel = SupportViewModel(FakeSupportAssistant(error = IllegalStateException("Support backend failed")))
        viewModel.updateQuestion("Why is my LOCAL model timing out?")

        viewModel.submit()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Support backend failed", viewModel.uiState.value.error)
    }

    private class FakeSupportAssistant(
        private val answer: SupportAnswer = SupportAnswer(
            text = "Answer",
            sources = listOf("FAQ"),
            contextSummary = "Context"
        ),
        private val error: Throwable? = null,
        private val delayMs: Long = 0
    ) : SupportAssistant {
        override suspend fun answer(question: String): SupportAnswer {
            if (delayMs > 0) delay(delayMs)
            error?.let { throw it }
            return answer
        }
    }
}
