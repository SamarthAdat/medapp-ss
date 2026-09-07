package com.ss.medrecord.ui.feature.timeline

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.session.ActivePatientManager
import com.ss.medrecord.domain.usecase.ObservePatientTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    observePatientTimeline: ObservePatientTimeline,
    activePatientManager: ActivePatientManager,
) : BaseViewModel<TimelineUiState, TimelineEvent, TimelineEffect>(TimelineUiState()) {

    init {
        activePatientManager.activePatient
            .onEach { patient -> setState { copy(activePatient = patient) } }
            .launchIn(viewModelScope)

        // The scope is a real query parameter, not a filter over one list: an
        // account with several profiles would otherwise hold every patient's
        // history in memory to render one person's.
        combine(
            activePatientManager.activePatient,
            uiState.map { it.showAllPatients }.distinctUntilChanged(),
        ) { patient, showAll -> if (showAll) null else patient?.patientId }
            .distinctUntilChanged()
            .flatMapLatest { patientId -> observePatientTimeline(patientId) }
            .onEach { entries -> setState { copy(allEntries = entries, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: TimelineEvent) {
        when (event) {
            is TimelineEvent.EntryClicked -> open(event.entry)

            is TimelineEvent.KindToggled -> setState {
                copy(
                    kindFilter = if (event.kind in kindFilter) {
                        kindFilter - event.kind
                    } else {
                        kindFilter + event.kind
                    },
                )
            }

            TimelineEvent.ToggleAllPatients ->
                setState { copy(showAllPatients = !showAllPatients, isLoading = true) }

            TimelineEvent.FiltersCleared -> setState { copy(kindFilter = emptySet()) }
            TimelineEvent.BackClicked -> sendEffect(TimelineEffect.NavigateBack)
        }
    }

    private fun open(entry: TimelineEntry) {
        sendEffect(
            when (entry.kind) {
                TimelineKind.VISIT -> TimelineEffect.NavigateToVisit(entry.targetId)
                TimelineKind.REPORT -> TimelineEffect.NavigateToReport(entry.targetId)
                TimelineKind.MEDICINE -> TimelineEffect.NavigateToMedicine(entry.targetId)
            },
        )
    }
}
