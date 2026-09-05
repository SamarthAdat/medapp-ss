package com.ss.medrecord.ui.feature.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.ui.theme.MedRecordTheme

@Composable
fun ConsentRoute(
    onConsentGranted: () -> Unit,
    onDeclined: () -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ConsentEffect.ConsentGranted -> onConsentGranted()
                ConsentEffect.Declined -> onDeclined()
                is ConsentEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ConsentScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentScreen(
    state: ConsentUiState,
    onEvent: (ConsentEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.isReconsent) {
                            "Updated consent"
                        } else {
                            "Before you begin"
                        },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.isReconsent) {
                    "We have updated how we describe the handling of your records. Please review and accept to continue."
                } else {
                    "Please read and accept each item. Nothing is stored until you do."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ConsentTexts.clauses.forEach { clause ->
                ConsentClauseCard(
                    title = clause.title,
                    body = clause.body,
                    checked = state.isAccepted(clause.type),
                    enabled = !state.isSubmitting,
                    onCheckedChange = { checked ->
                        onEvent(ConsentEvent.ToggleConsent(clause.type, checked))
                    },
                )
            }

            Text(
                text = "Consent version ${state.version}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onEvent(ConsentEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (state.isSubmitting) {
                    Box(modifier = Modifier.size(20.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else {
                    Text(text = "I accept")
                }
            }

            TextButton(
                onClick = { onEvent(ConsentEvent.Decline) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Decline and sign out")
            }
        }
    }
}

@Composable
private fun ConsentClauseCard(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            // The whole card is one toggle target, so the checkbox itself is
            // merged away from the accessibility tree rather than announced twice.
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun ConsentScreenPreview() {
    MedRecordTheme {
        ConsentScreen(state = ConsentUiState(version = 1), onEvent = {})
    }
}
