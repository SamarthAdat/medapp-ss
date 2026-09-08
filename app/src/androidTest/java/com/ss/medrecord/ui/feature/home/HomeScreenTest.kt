package com.ss.medrecord.ui.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.RecordCounts
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.UpcomingAppointment
import com.ss.medrecord.ui.theme.MedRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * The dashboard rendered for real.
 *
 * [HomeScreen] is stateless by design, so it can be driven from a fabricated
 * snapshot without a signed-in account, a database or Firebase. What this
 * catches is what a preview cannot: that the sections actually appear or stay
 * hidden under the conditions they are supposed to, and that a tap emits the
 * event the ViewModel expects.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allergiesAreShownForTheActivePatient() {
        // The one field on this screen that changes what someone else should do
        // in an emergency. If it ever stops rendering, this fails loudly.
        setContent(state(dashboard = dashboard()))

        composeRule.onNodeWithText("Allergies").assertIsDisplayed()
        composeRule.onNodeWithText("Penicillin, sulfa drugs").assertIsDisplayed()
    }

    @Test
    fun aPatientWithNoAllergiesGetsNoAllergiesSection() {
        setContent(
            state(dashboard = dashboard(patient = patient(allergies = null))),
        )

        composeRule.onNodeWithText("Allergies").assertDoesNotExist()
    }

    @Test
    fun recordCountsAreShown() {
        setContent(state(dashboard = dashboard()))

        composeRule.onNodeWithText("Visits").assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("Reports").assertIsDisplayed()
        composeRule.onNodeWithText("7").assertIsDisplayed()
    }

    @Test
    fun anUpcomingAppointmentIsShownWithHowFarAway() {
        setContent(state(dashboard = dashboard()))

        composeRule.onNodeWithText("NEXT APPOINTMENT").assertIsDisplayed()
        // Not the figure itself: "2" is also the active-medicine count lower
        // down, and a bare digit is the wrong thing to match a screen on.
        composeRule.onNodeWithText("days away").assertIsDisplayed()
    }

    @Test
    fun theComingUpCardIsHiddenWhenThereIsNothingDue() {
        // Deliberate: an empty "nothing due" card every evening teaches the user
        // to skip the one part of the screen that must never be skipped.
        setContent(
            state(
                dashboard = dashboard(
                    appointments = emptyList(),
                    doses = emptyList(),
                ),
            ),
        )

        composeRule.onNodeWithText("NEXT APPOINTMENT").assertDoesNotExist()
    }

    @Test
    fun tappingAnAppointmentAsksToOpenThatVisit() {
        val events = mutableListOf<HomeEvent>()
        setContent(state(dashboard = dashboard()), onEvent = events::add)

        // The hero card is the tap target, and the facility name appears on it
        // as well as on the visit row below - so this addresses the one inside
        // the card by the label only the hero has.
        composeRule.onNodeWithText("NEXT APPOINTMENT").performClick()

        assertEquals(listOf(HomeEvent.AppointmentClicked("v1")), events)
    }

    @Test
    fun recentActivityListsEntriesAcrossRecordTypes() {
        setContent(state(dashboard = dashboard()))

        composeRule.onNodeWithText("Recent activity").assertIsDisplayed()
        // Twice over: once on the hero appointment card, once as the visit row.
        composeRule.onAllNodesWithText("City Care Clinic").assertCountEquals(2)
        composeRule.onNodeWithText("blood-panel.pdf").assertIsDisplayed()
    }

    @Test
    fun tappingAnActivityRowAsksToOpenThatRecord() {
        val events = mutableListOf<HomeEvent>()
        setContent(state(dashboard = dashboard()), onEvent = events::add)

        composeRule.onNodeWithText("blood-panel.pdf").performClick()

        val clicked = events.filterIsInstance<HomeEvent.ActivityClicked>().single()
        assertEquals(TimelineKind.REPORT, clicked.entry.kind)
        assertEquals("r1", clicked.entry.targetId)
    }

    @Test
    fun anAccountWithNoPatientsIsPromptedToAddOne() {
        setContent(state(dashboard = DashboardSnapshot(patients = emptyList())))

        composeRule.onNodeWithText("Add your first patient").assertIsDisplayed()
        // None of the record sections make sense before there is a person.
        composeRule.onNodeWithText("Recent activity").assertDoesNotExist()
    }

    @Test
    fun seeAllOpensTheTimeline() {
        val events = mutableListOf<HomeEvent>()
        setContent(state(dashboard = dashboard()), onEvent = events::add)

        composeRule.onNodeWithText("See all").performClick()

        assertTrue(events.contains(HomeEvent.OpenTimeline))
    }

    private fun setContent(
        state: HomeUiState,
        onEvent: (HomeEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            MedRecordTheme {
                HomeScreen(state = state, onEvent = onEvent)
            }
        }
    }

    private fun state(dashboard: DashboardSnapshot) =
        HomeUiState(isLoading = false, dashboard = dashboard)

    private fun dashboard(
        patient: Patient = patient(),
        appointments: List<UpcomingAppointment> = listOf(appointment()),
        doses: List<Reminder> = emptyList(),
    ) = DashboardSnapshot(
        activePatient = patient,
        patients = listOf(patient),
        counts = RecordCounts(visits = 12, reports = 7, activeMedicines = 2),
        upcomingAppointments = appointments,
        dosesToday = doses,
        recentActivity = listOf(
            TimelineEntry(
                id = "VISIT:v1",
                kind = TimelineKind.VISIT,
                targetId = "v1",
                patientId = "p1",
                patientName = "Asha Rao",
                title = "City Care Clinic",
                subtitle = "Dr Mehta",
                onEpochDay = TODAY.minusDays(3).toEpochDay(),
                recordedAtMillis = 2L,
            ),
            TimelineEntry(
                id = "REPORT:r1",
                kind = TimelineKind.REPORT,
                targetId = "r1",
                patientId = "p1",
                patientName = "Asha Rao",
                title = "blood-panel.pdf",
                subtitle = "726 KB",
                onEpochDay = TODAY.minusDays(3).toEpochDay(),
                recordedAtMillis = 1L,
            ),
        ),
    )

    private fun patient(allergies: String? = "Penicillin, sulfa drugs") = Patient(
        patientId = "p1",
        userId = "u1",
        name = "Asha Rao",
        relationship = Relationship.SELF,
        dateOfBirthEpochDay = TODAY.minusYears(41).toEpochDay(),
        knownAllergies = allergies,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun appointment() = UpcomingAppointment(
        visitId = "v1",
        patientId = "p1",
        patientName = "Asha Rao",
        facilityName = "City Care Clinic",
        doctorName = "Dr Mehta",
        // Relative to today, so the "In 2 days" label is stable whenever this runs.
        onEpochDay = TODAY.plusDays(2).toEpochDay(),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.now()
    }
}
