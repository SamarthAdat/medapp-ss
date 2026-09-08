package com.ss.medrecord.ui.feature.consent

import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.ConsentType

/**
 * Each [ConsentType] is acknowledged separately (spec section 4.8) rather than
 * behind one blanket checkbox, so the ledger records what was actually agreed
 * to and a later partial withdrawal has something to withdraw from.
 */
data class ConsentUiState(
    val version: Int = 0,
    val accepted: Map<ConsentType, Boolean> = ConsentType.entries.associateWith { false },
    val isSubmitting: Boolean = false,
    val isReconsent: Boolean = false,
) : UiState {
    val allAccepted: Boolean get() = accepted.values.all { it }

    /** How many clauses are ticked, for the "2/3" marker in the header. */
    val acceptedCount: Int get() = accepted.values.count { it }
    val canSubmit: Boolean get() = allAccepted && !isSubmitting

    fun isAccepted(type: ConsentType): Boolean = accepted[type] == true
}

sealed interface ConsentEvent : UiEvent {
    data class ToggleConsent(val type: ConsentType, val accepted: Boolean) : ConsentEvent
    data object Submit : ConsentEvent
    data object Decline : ConsentEvent
}

sealed interface ConsentEffect : UiEffect {
    data object ConsentGranted : ConsentEffect

    /**
     * Declining signs the user out. Consent is the legal basis for processing,
     * so an account that refuses it cannot be left sitting in a signed-in state.
     */
    data object Declined : ConsentEffect
    data class ShowMessage(val message: String) : ConsentEffect
}
