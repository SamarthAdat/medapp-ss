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

// --- Visits and facilities (Phase 4) --------------------------------------

@Serializable
data object VisitListDestination

/** A null visitId means "add"; a present one means "edit". */
@Serializable
data class VisitEditDestination(val visitId: String? = null)

@Serializable
data class VisitDetailDestination(val visitId: String)

@Serializable
data object FacilityListDestination

// --- Reports (Phase 5) -----------------------------------------------------

@Serializable
data object ReportListDestination

@Serializable
data class ReportViewerDestination(val reportId: String)

// --- Medicines and reminders (Phase 6) ------------------------------------

@Serializable
data object MedicineListDestination

/**
 * A null medicineId means "add"; a present one means "edit".
 *
 * visitId is only ever set when adding from a visit record, so the new medicine
 * arrives already linked to the appointment that prescribed it. It is ignored
 * when editing, where the medicine already knows its own visit.
 */
@Serializable
data class MedicineEditDestination(
    val medicineId: String? = null,
    val visitId: String? = null,
)

// --- Dashboard and aggregated views (Phase 7) -----------------------------

/**
 * The merged history. Takes no arguments: which patient it shows follows the
 * active-patient context and the screen's own "all patients" toggle, so a
 * deep-linked timeline can never disagree with the chip in the app bar.
 */
@Serializable
data object TimelineDestination

// --- Maps and nearby facilities (Phase 8) ---------------------------------

@Serializable
data class FacilityDetailDestination(val facilityId: String)

@Serializable
data object NearbyFacilitiesDestination
