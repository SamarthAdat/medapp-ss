package com.ss.medrecord.ui.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.ui.components.TimelineDateHeader
import com.ss.medrecord.ui.components.TimelineRow
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.LocalDate

@Composable
fun TimelineRoute(
    onNavigateBack: () -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenMedicine: (String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                TimelineEffect.NavigateBack -> onNavigateBack()
                is TimelineEffect.NavigateToVisit -> onOpenVisit(effect.visitId)
                is TimelineEffect.NavigateToReport -> onOpenReport(effect.reportId)
                is TimelineEffect.NavigateToMedicine -> onOpenMedicine(effect.medicineId)
            }
        }
    }

    TimelineScreen(state = state, onEvent = viewModel::onEvent)
}

/**
 * One patient's history with visits, reports and medicines interleaved
 * (spec section 5.9).
 *
 * Grouped by date rather than by record type, which is the whole point: the
 * question this screen answers is "what happened in March", and three separate
 * type-sorted lists cannot answer it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onEvent: (TimelineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = state.title) },
                navigationIcon = {
                    TextButton(onClick = { onEvent(TimelineEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
                actions = {
                    if (state.hasFiltersApplied) {
                        TextButton(onClick = { onEvent(TimelineEvent.FiltersCleared) }) {
                            Text(text = "Clear")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            FullScreenLoading(modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = state.showAllPatients,
                        onClick = { onEvent(TimelineEvent.ToggleAllPatients) },
                        label = { Text(text = "All patients") },
                    )
                    TimelineKind.entries.forEach { kind ->
                        FilterChip(
                            selected = kind in state.kindFilter,
                            onClick = { onEvent(TimelineEvent.KindToggled(kind)) },
                            label = { Text(text = kind.label) },
                        )
                    }
                }
            }

            if (state.isEmpty) {
                item {
                    Column(modifier = Modifier.padding(vertical = 40.dp)) {
                        Text(
                            text = if (state.hasFiltersApplied) {
                                "Nothing matches those filters"
                            } else {
                                "Nothing recorded yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Visits, reports and medicines appear here together, " +
                                "newest first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            state.grouped.forEach { (date, entries) ->
                item(key = "header-$date") {
                    TimelineDateHeader(date = date)
                    HorizontalDivider()
                }

                items(
                    count = entries.size,
                    key = { index -> entries[index].id },
                ) { index ->
                    val entry = entries[index]
                    TimelineRow(
                        entry = entry,
                        onClick = { onEvent(TimelineEvent.EntryClicked(entry)) },
                        showPatientName = state.showAllPatients,
                        // The date header above already says the day; repeating
                        // it on every row would be noise.
                        showDate = false,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineScreenPreview() {
    val today = LocalDate.now()
    MedRecordTheme {
        TimelineScreen(
            state = TimelineUiState(
                isLoading = false,
                allEntries = listOf(
                    TimelineEntry(
                        id = "VISIT:v1",
                        kind = TimelineKind.VISIT,
                        targetId = "v1",
                        patientId = "p1",
                        patientName = "Asha Rao",
                        title = "City Care Clinic",
                        subtitle = "Dr Mehta",
                        onEpochDay = today.toEpochDay(),
                        recordedAtMillis = 2L,
                    ),
                    TimelineEntry(
                        id = "REPORT:r1",
                        kind = TimelineKind.REPORT,
                        targetId = "r1",
                        patientId = "p1",
                        patientName = "Asha Rao",
                        title = "blood-panel.pdf",
                        subtitle = "City Care Clinic · 726 KB",
                        onEpochDay = today.toEpochDay(),
                        recordedAtMillis = 1L,
                    ),
                    TimelineEntry(
                        id = "MEDICINE:m1",
                        kind = TimelineKind.MEDICINE,
                        targetId = "m1",
                        patientId = "p1",
                        patientName = "Asha Rao",
                        title = "Metformin",
                        subtitle = "500 mg · Every day",
                        onEpochDay = today.minusDays(14).toEpochDay(),
                        recordedAtMillis = 0L,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
