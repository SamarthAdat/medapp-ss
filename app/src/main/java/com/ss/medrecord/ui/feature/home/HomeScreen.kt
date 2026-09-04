package com.ss.medrecord.ui.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.ui.theme.MedRecordTheme

/**
 * Stateful entry point: owns the ViewModel and translates one-shot effects into
 * navigation calls. Kept thin so the stateless [HomeScreen] stays previewable.
 */
@Composable
fun HomeRoute(
    onNavigateToPatients: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToPatients -> onNavigateToPatients()
                HomeEffect.NavigateToSettings -> onNavigateToSettings()
            }
        }
    }

    HomeScreen(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = "MedRecord Keeper") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Foundation ready",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (state.isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = { onEvent(HomeEvent.OpenPatients) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Patients")
            }

            OutlinedButton(
                onClick = { onEvent(HomeEvent.OpenSettings) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Settings")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    MedRecordTheme {
        HomeScreen(state = HomeUiState(), onEvent = {})
    }
}
