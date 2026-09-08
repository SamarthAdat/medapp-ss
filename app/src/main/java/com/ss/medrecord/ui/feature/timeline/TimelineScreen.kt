package com.ss.medrecord.ui.feature.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.ss.medrecord.ui.components.MedEmptyState
import com.ss.medrecord.ui.components.MedFilterChip
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.accent
import com.ss.medrecord.ui.components.icon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
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
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    onEvent: (TimelineEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = "History",
                subtitle = state.title,
                onBack = { onEvent(TimelineEvent.BackClicked) },
                actions = {
                    if (state.hasFiltersApplied) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.jade,
                            modifier = Modifier
                                .clickable { onEvent(TimelineEvent.FiltersCleared) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MedFilterChip(
                        text = "All patients",
                        selected = state.showAllPatients,
                        onClick = { onEvent(TimelineEvent.ToggleAllPatients) },
                        icon = MedIcons.Person,
                    )
                    TimelineKind.entries.forEach { kind ->
                        MedFilterChip(
                            text = kind.label,
                            selected = kind in state.kindFilter,
                            onClick = { onEvent(TimelineEvent.KindToggled(kind)) },
                            icon = kind.icon,
                            accent = kind.accent(),
                        )
                    }
                }
            }

            if (state.isEmpty) {
                item {
                    MedEmptyState(
                        title = if (state.hasFiltersApplied) {
                            "Nothing matches those filters"
                        } else {
                            "Nothing recorded yet"
                        },
                        message = "Visits, reports and medicines appear here " +
                            "together, newest first.",
                        icon = MedIcons.History,
                    )
                }
            }

            state.grouped.forEach { (date, entries) ->
                item(key = "header-$date") {
                    TimelineDateHeader(date = date)
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
