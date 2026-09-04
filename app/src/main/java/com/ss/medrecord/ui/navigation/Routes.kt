package com.ss.medrecord.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation-Compose destinations. Arguments are declared as
 * constructor properties, so a screen can never be reached without them and the
 * compiler catches route changes at every call site.
 *
 * Destinations carry the `Destination` suffix; the stateful composable that
 * hosts a screen is named `XRoute` and the stateless one `XScreen`.
 *
 * Later-phase destinations are added here as they are built - keeping the whole
 * navigation surface in one file makes it reviewable.
 */

// --- Graphs ----------------------------------------------------------------

@Serializable
data object AuthGraph

@Serializable
data object MainGraph

// --- Entry -----------------------------------------------------------------

@Serializable
data object SplashDestination

// --- Auth (Phase 1) --------------------------------------------------------

@Serializable
data object LoginDestination

@Serializable
data object SignUpDestination

@Serializable
data object ForgotPasswordDestination

@Serializable
data object ConsentDestination

// --- Main (Phase 2+) -------------------------------------------------------

@Serializable
data object HomeDestination

@Serializable
data object PatientListDestination

@Serializable
data class PatientEditDestination(val patientId: String? = null)

@Serializable
data object SettingsDestination
