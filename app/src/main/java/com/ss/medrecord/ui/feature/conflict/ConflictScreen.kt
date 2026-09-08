package com.ss.medrecord.ui.feature.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.ConflictEntityType
import com.ss.medrecord.domain.model.ConflictResolution
import com.ss.medrecord.domain.model.SyncConflict
import com.ss.medrecord.ui.components.MedBarAction
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ConflictRoute(
    onNavigateBack: () -> Unit,
    viewModel: ConflictViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ConflictEffect.NavigateBack -> onNavigateBack()
                is ConflictEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ConflictScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * One card per parked record, with both timestamps and two equally weighted
 * buttons.
 *
 * Neither choice is styled as the default and neither is preselected. The app
 * genuinely does not know which copy is right, and a highlighted "recommended"
 * button would be the app pretending it does while leaving the user with the
 * consequences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(
    state: ConflictUiState,
    onEvent: (ConflictEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    MedScreen(
        modifier = modifier,
        glow = MedTheme.colors.coral,
        topBar = {
            MedTopBar(
                title = "Review changes",
                subtitle = "Choose which copy of each record to keep",
                onBack = { onEvent(ConflictEvent.BackClicked) },
                actions = {
                    MedBarAction(
                        text = "Refresh",
                        onClick = { onEvent(ConflictEvent.Refresh) },
                    )
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            FullScreenLoading(modifier = Modifier.padding(innerPadding))
            return@MedScreen
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.errorMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (state.isEmpty) {
                item {
                    Column(modifier = Modifier.padding(vertical = 40.dp)) {
                        Text(
                            text = "Nothing to review",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Records changed in two places at once appear here so " +
                                "you can choose which copy to keep. There are none right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else if (state.conflicts.isNotEmpty()) {
                item {
                    Text(
                        text = "These records were changed on this device and somewhere " +
                            "else. They have stopped syncing until you choose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.conflicts, key = { "${it.entityType}:${it.entityId}" }) { conflict ->
                ConflictCard(
                    conflict = conflict,
                    isResolving = state.resolvingId == conflict.entityId,
                    onResolve = { resolution ->
                        onEvent(ConflictEvent.Resolved(conflict, resolution))
                    },
                )
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    isResolving: Boolean,
    onResolve: (ConflictResolution) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = conflict.entityType.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = conflict.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            conflict.detail?.let { detail ->
                Text(text = detail, style = MaterialTheme.typography.bodySmall)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VersionColumn(
                    title = "On this device",
                    timestamp = formatInstant(conflict.localUpdatedAt),
                    isNewer = !conflict.remoteIsNewer && !conflict.isRemoteUnknown,
                    modifier = Modifier.weight(1f),
                )
                VersionColumn(
                    title = "Synced copy",
                    timestamp = conflict.remoteUpdatedAt?.let(::formatInstant)
                        // Not a fabricated time. Offline, or removed server-side.
                        ?: "Could not be read",
                    isNewer = conflict.remoteIsNewer,
                    modifier = Modifier.weight(1f),
                )
            }

            if (isResolving) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                return@Column
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Both outlined, same weight, no default. The app does not know
                // which is right and should not imply that it does.
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.KEEP_LOCAL) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Keep this device's")
                }
                OutlinedButton(
                    onClick = { onResolve(ConflictResolution.KEEP_REMOTE) },
                    enabled = !conflict.isRemoteUnknown,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = "Keep synced")
                }
            }
        }
    }
}

@Composable
private fun VersionColumn(
    title: String,
    timestamp: String,
    isNewer: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isNewer) {
            // Stated, not acted on: newer is not the same as correct.
            Text(
                text = "More recent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatInstant(millis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Preview(showBackground = true)
@Composable
private fun ConflictScreenPreview() {
    val now = System.currentTimeMillis()
    MedRecordTheme {
        ConflictScreen(
            state = ConflictUiState(
                isLoading = false,
                conflicts = listOf(
                    SyncConflict(
                        entityType = ConflictEntityType.VISIT,
                        entityId = "v1",
                        label = "3 Mar 2026",
                        detail = "Dr Mehta",
                        localUpdatedAt = now - 3_600_000,
                        remoteUpdatedAt = now,
                    ),
                    SyncConflict(
                        entityType = ConflictEntityType.MEDICINE,
                        entityId = "m1",
                        label = "Metformin",
                        detail = "500 mg",
                        localUpdatedAt = now - 7_200_000,
                        remoteUpdatedAt = null,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
