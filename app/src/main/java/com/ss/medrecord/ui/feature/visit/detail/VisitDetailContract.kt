package com.ss.medrecord.ui.feature.visit.detail

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.VisitWithFacility

data class VisitDetailUiState(
    val visit: VisitWithFacility? = null,
    val patientName: String? = null,
    val isLoading: Boolean = true,
) : UiState

sealed interface VisitDetailEvent : UiEvent {
    data object EditClicked : VisitDetailEvent
    data object BackClicked : VisitDetailEvent
}

sealed interface VisitDetailEffect : UiEffect {
    data object NavigateBack : VisitDetailEffect
    data class NavigateToEdit(val visitId: String) : VisitDetailEffect
}
