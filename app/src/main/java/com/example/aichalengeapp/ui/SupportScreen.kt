package com.example.aichalengeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.aichalengeapp.vm.SupportViewModel

@Composable
fun SupportScreen(
    viewModel: SupportViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("User Support", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Ask a customer support question. The answer uses support FAQ context and user/ticket context.",
            style = MaterialTheme.typography.bodyMedium
        )

        OutlinedTextField(
            value = state.question,
            onValueChange = viewModel::updateQuestion,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Support question") },
            minLines = 3
        )

        Button(
            onClick = viewModel::submit,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get support answer")
        }

        when {
            state.isLoading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Generating support answer…")
                    }
                }
            }

            state.error != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = state.error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            state.answer != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Support Answer", style = MaterialTheme.typography.titleMedium)
                        Text(state.answer.text, style = MaterialTheme.typography.bodyMedium)
                        state.answer.contextSummary?.takeIf { it.isNotBlank() }?.let {
                            Text("Context: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.answer.sources.isNotEmpty()) {
                            Text(
                                "Sources: ${state.answer.sources.joinToString()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Start with a support question like: Why is my LOCAL model timing out?",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
