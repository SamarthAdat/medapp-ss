package com.ss.medrecord.ui.feature.home

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.connectivity.ConnectivityObserver
import com.ss.medrecord.core.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    connectivityObserver: ConnectivityObserver,
) : BaseViewModel<HomeUiState, HomeEvent, HomeEffect>(
    initialState = HomeUiState(networkStatus = connectivityObserver.currentStatus()),
) {

    init {
        connectivityObserver.status
            .onEach { status -> setState { copy(networkStatus = status) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OpenPatients -> sendEffect(HomeEffect.NavigateToPatients)
            HomeEvent.OpenSettings -> sendEffect(HomeEffect.NavigateToSettings)
        }
    }
}
