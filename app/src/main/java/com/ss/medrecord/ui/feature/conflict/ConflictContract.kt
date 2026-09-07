package com.ss.medrecord.ui.feature.conflict

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.ConflictResolution
import com.ss.medrecord.domain.model.SyncConflict

/**
 * Resolving records that were changed in two places (spec section 6.4).
 *
 * The screen exists because the sync engine refuses to guess. Every automatic
 * rule for picking a winner - newest wins, server wins, device wins - silently
 * destroys a medical record someone deliberately wrote, and nothing in the
 * timestamps says which of two edits was the correct one.
 */
data class ConflictUiState(
    val conflicts: List<SyncConflict> = emptyList(),
    val isLoading: Boolean = true,
    val resolvingId: String? = null,
    val errorMessage: String? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && conflicts.isEmpty()
}

sealed interface ConflictEvent : UiEvent {
    data class Resolved(
        val conflict: SyncConflict,
        val resolution: ConflictResolution,
    ) : ConflictEvent

    data object Refresh : ConflictEvent
    data object BackClicked : ConflictEvent
}

sealed interface ConflictEffect : UiEffect {
    data object NavigateBack : ConflictEffect
    data class ShowMessage(val message: String) : ConflictEffect
}
