package com.ss.medrecord.ui.feature.timeline

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import java.time.LocalDate

/**
 * The merged history (spec section 5.9).
 *
 * Kind filters are applied here over an already-observed list rather than by
 * re-querying, exactly as the reports and medicines screens do: one read feeds
 * every combination, results stay live as sync brings changes in, and toggling
 * a chip costs nothing.
 */
data class TimelineUiState(
    val activePatient: Patient? = null,
    val allEntries: List<TimelineEntry> = emptyList(),
    val showAllPatients: Boolean = false,
    /** Empty means everything; a kind is added or removed by tapping its chip. */
    val kindFilter: Set<TimelineKind> = emptySet(),
    val isLoading: Boolean = true,
) : UiState {

    val entries: List<TimelineEntry>
        get() = allEntries.filter { kindFilter.isEmpty() || it.kind in kindFilter }

    /** Day-by-day, newest first, for the date headers. */
    val grouped: List<Pair<LocalDate, List<TimelineEntry>>>
        get() = entries
            .groupBy { it.date }
            .toList()
            .sortedByDescending { (date, _) -> date }

    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()

    val hasFiltersApplied: Boolean get() = kindFilter.isNotEmpty()

    val title: String
        get() = when {
            showAllPatients -> "All records"
            else -> activePatient?.name ?: "History"
        }
}

sealed interface TimelineEvent : UiEvent {
    data class EntryClicked(val entry: TimelineEntry) : TimelineEvent
    data class KindToggled(val kind: TimelineKind) : TimelineEvent
    data object ToggleAllPatients : TimelineEvent
    data object FiltersCleared : TimelineEvent
    data object BackClicked : TimelineEvent
}

sealed interface TimelineEffect : UiEffect {
    data object NavigateBack : TimelineEffect
    data class NavigateToVisit(val visitId: String) : TimelineEffect
    data class NavigateToReport(val reportId: String) : TimelineEffect
    data class NavigateToMedicine(val medicineId: String) : TimelineEffect
}
