package com.ss.medrecord.ui.feature.conflict

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.model.ConflictResolution
import com.ss.medrecord.domain.model.SyncConflict
import com.ss.medrecord.domain.repository.ConflictRepository
import com.ss.medrecord.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConflictViewModel @Inject constructor(
    private val conflictRepository: ConflictRepository,
    private val sessionManager: SessionManager,
) : BaseViewModel<ConflictUiState, ConflictEvent, ConflictEffect>(ConflictUiState()) {

    init {
        load()
    }

    override fun onEvent(event: ConflictEvent) {
        when (event) {
            is ConflictEvent.Resolved -> resolve(event.conflict, event.resolution)
            ConflictEvent.Refresh -> load()
            ConflictEvent.BackClicked -> sendEffect(ConflictEffect.NavigateBack)
        }
    }

    private fun userId(): String? =
        (sessionManager.session.value as? AuthSession.Authenticated)?.userId

    private fun load() {
        val userId = userId()
        if (userId == null) {
            setState { copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true, errorMessage = null) }
            when (val result = conflictRepository.conflicts(userId)) {
                is DataResult.Success ->
                    setState { copy(conflicts = result.data, isLoading = false) }

                is DataResult.Error -> setState {
                    copy(isLoading = false, errorMessage = result.error.toUserMessage())
                }
            }
        }
    }

    private fun resolve(conflict: SyncConflict, resolution: ConflictResolution) {
        if (currentState.resolvingId != null) return
        val userId = userId() ?: return

        viewModelScope.launch {
            setState { copy(resolvingId = conflict.entityId) }

            when (val result = conflictRepository.resolve(userId, conflict, resolution)) {
                is DataResult.Success -> {
                    // Dropped from the list locally rather than re-read: the row
                    // is no longer in CONFLICT, and re-querying would hit the
                    // network again for every resolution in a batch.
                    setState {
                        copy(
                            conflicts = conflicts.filterNot {
                                it.entityId == conflict.entityId
                            },
                            resolvingId = null,
                        )
                    }
                    sendEffect(
                        ConflictEffect.ShowMessage(
                            when (resolution) {
                                ConflictResolution.KEEP_LOCAL -> "Kept this device's copy"
                                ConflictResolution.KEEP_REMOTE -> "Kept the synced copy"
                            },
                        ),
                    )
                }

                is DataResult.Error -> {
                    setState { copy(resolvingId = null) }
                    sendEffect(ConflictEffect.ShowMessage(result.error.toUserMessage()))
                }
            }
        }
    }
}
