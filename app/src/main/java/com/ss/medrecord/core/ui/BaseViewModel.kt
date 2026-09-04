package com.ss.medrecord.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared MVVM plumbing: one observable [uiState], one [onEvent] entry point, and
 * a buffered [effect] channel for things that must happen exactly once.
 *
 * Screens stay stateless Composables that take a state and emit events, which
 * keeps them previewable and lets the ViewModel own all logic.
 */
abstract class BaseViewModel<S : UiState, E : UiEvent, F : UiEffect>(
    initialState: S,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effect = Channel<F>(Channel.BUFFERED)
    val effect: Flow<F> = _effect.receiveAsFlow()

    protected val currentState: S get() = _uiState.value

    /** Applies a pure reduction to the current state. */
    protected fun setState(reducer: S.() -> S) {
        _uiState.update { it.reducer() }
    }

    protected fun sendEffect(effect: F) {
        viewModelScope.launch { _effect.send(effect) }
    }

    /** Single funnel for everything the UI can ask of this ViewModel. */
    abstract fun onEvent(event: E)
}
