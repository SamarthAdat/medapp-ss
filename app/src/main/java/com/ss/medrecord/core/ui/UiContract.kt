package com.ss.medrecord.core.ui

/** Immutable snapshot a screen renders. Implementations must be data classes. */
interface UiState

/** A user intent travelling from the Composable into its ViewModel. */
interface UiEvent

/** A one-shot side effect: navigation, snackbar, permission request. */
interface UiEffect
