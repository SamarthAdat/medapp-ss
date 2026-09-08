package com.ss.medrecord.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ss.medrecord.domain.model.AppearanceMode
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.RecordCounts
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.domain.model.ReminderStatus
import com.ss.medrecord.domain.model.ReminderType
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.UpcomingAppointment
import com.ss.medrecord.domain.sync.SyncStatusUi
import com.ss.medrecord.ui.feature.consent.ConsentScreen
import com.ss.medrecord.ui.feature.consent.ConsentUiState
import com.ss.medrecord.ui.feature.home.HomeScreen
import com.ss.medrecord.ui.feature.home.HomeUiState
import com.ss.medrecord.ui.feature.settings.SettingsScreen
import com.ss.medrecord.ui.feature.settings.SettingsUiState
import com.ss.medrecord.ui.feature.timeline.TimelineScreen
import com.ss.medrecord.ui.feature.timeline.TimelineUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.theme.MedIcon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Renders the redesigned screens and writes them to the device's external
 * files directory so they can be looked at.
 *
 * Not an assertion of anything - it is a way to see a screen without a signed-in
 * account, a database or Firebase. Kept because the alternative is signing into
 * a real account with real records every time a colour or a spacing changes.
 */
@RunWith(AndroidJUnit4::class)
class DesignCaptureTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Every icon in the app, drawn at once.
     *
     * The icon font is addressed by ligature name, so a name the subset does
     * not carry renders as the word itself. This is what makes that visible -
     * a wall of glyphs with one word in it - instead of it shipping.
     */
    @Test
    fun captureIconSheet() = capture("icons", dark = true) { IconSheet() }

    @Test
    fun captureDashboardDark() = capture("dashboard-dark", dark = true) { HomeScreen(state = homeState(), onEvent = {}) }

    @Test
    fun captureDashboardLight() = capture("dashboard-light", dark = false) { HomeScreen(state = homeState(), onEvent = {}) }

    @Test
    fun captureSettingsDark() = capture("settings-dark", dark = true) { SettingsScreen(state = settingsState(), onEvent = {}) }

    @Test
    fun captureSettingsLight() = capture("settings-light", dark = false) { SettingsScreen(state = settingsState(), onEvent = {}) }

    @Test
    fun captureConsentDark() = capture("consent-dark", dark = true) {
        ConsentScreen(
            state = ConsentUiState(
                version = 1,
                accepted = ConsentType.entries.associateWith { it == ConsentType.entries.first() },
            ),
            onEvent = {},
        )
    }

    @Test
    fun captureTimelineDark() = capture("timeline-dark", dark = true) {
        TimelineScreen(
            state = TimelineUiState(isLoading = false, allEntries = entries()),
            onEvent = {},
        )
    }

    private fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        composeRule.setContent {
            MedRecordTheme(darkTheme = dark) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { content() }
            }
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir(null)!!
        File(dir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Composable
    private fun IconSheet() {
        val icons = MedIcons::class.java.declaredFields
            .filter { it.type == MedIcon::class.java }
            .mapNotNull { field ->
                field.isAccessible = true
                (field.get(MedIcons) as? MedIcon)?.let { field.name to it }
            }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
            modifier = Modifier
                .fillMaxSize()
                .background(MedTheme.colors.canvas),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            items(icons.size) { index ->
                val (name, icon) = icons[index]
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    MedIconGlyph(
                        icon = icon,
                        size = 26.dp,
                        tint = MedTheme.colors.jade,
                        contentDescription = null,
                    )
                    androidx.compose.material3.Text(
                        text = name,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = MedTheme.colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    private fun homeState() = HomeUiState(
        isLoading = false,
        syncStatus = SyncStatusUi(isOnline = true),
        dashboard = DashboardSnapshot(
            activePatient = asha,
            patients = listOf(asha, vikram, lalita),
            counts = RecordCounts(visits = 12, reports = 7, activeMedicines = 2),
            visitsByMonth = listOf(1, 2, 0, 3, 1, 2, 0, 1, 2, 0, 0, 0),
            upcomingAppointments = listOf(
                UpcomingAppointment(
                    visitId = "v1",
                    patientId = "p1",
                    patientName = "Asha Rao",
                    facilityName = "City Care Clinic",
                    doctorName = "Dr Mehta",
                    onEpochDay = TODAY.plusDays(14).toEpochDay(),
                ),
            ),
            dosesToday = listOf(
                dose(1L, ReminderStatus.FIRED),
                dose(2L, ReminderStatus.FIRED),
                dose(3L, ReminderStatus.SCHEDULED),
            ),
            recentActivity = entries(),
        ),
    )

    private fun settingsState() = SettingsUiState(
        name = "Samarth Adat",
        email = "owner@example.com",
        consentVersion = 1,
        syncStatus = SyncStatusUi(isOnline = true, pendingCount = 3),
        appearanceMode = AppearanceMode.FOLLOW_SYSTEM,
    )

    private fun dose(id: Long, status: ReminderStatus) = Reminder(
        reminderId = id,
        userId = "u1",
        patientId = "p1",
        type = ReminderType.MEDICINE,
        sourceId = "m1",
        triggerAtMillis = System.currentTimeMillis() + id * 3_600_000L,
        title = "Metformin",
        message = "500 mg",
        status = status,
    )

    private fun entries() = listOf(
        TimelineEntry(
            id = "VISIT:v1",
            kind = TimelineKind.VISIT,
            targetId = "v1",
            patientId = "p1",
            patientName = "Asha Rao",
            title = "City Care Clinic",
            subtitle = "Dr Mehta · routine check-up",
            onEpochDay = TODAY.toEpochDay(),
            recordedAtMillis = 3L,
        ),
        TimelineEntry(
            id = "REPORT:r1",
            kind = TimelineKind.REPORT,
            targetId = "r1",
            patientId = "p1",
            patientName = "Asha Rao",
            title = "blood-panel.pdf",
            subtitle = "City Care Clinic · 726 KB",
            onEpochDay = TODAY.toEpochDay(),
            recordedAtMillis = 2L,
        ),
        TimelineEntry(
            id = "MEDICINE:m1",
            kind = TimelineKind.MEDICINE,
            targetId = "m1",
            patientId = "p1",
            patientName = "Asha Rao",
            title = "Metformin started",
            subtitle = "500 mg · every day",
            onEpochDay = TODAY.minusDays(14).toEpochDay(),
            recordedAtMillis = 1L,
        ),
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.now()

        val asha = Patient(
            patientId = "p1",
            userId = "u1",
            name = "Asha Rao",
            relationship = Relationship.SELF,
            dateOfBirthEpochDay = TODAY.minusYears(41).toEpochDay(),
            knownAllergies = "Penicillin, sulfa drugs",
            createdAt = 0L,
            updatedAt = 0L,
        )
        val vikram = Patient(
            patientId = "p2",
            userId = "u1",
            name = "Vikram Rao",
            relationship = Relationship.CHILD,
            createdAt = 0L,
            updatedAt = 0L,
        )
        val lalita = Patient(
            patientId = "p3",
            userId = "u1",
            name = "Lalita Rao",
            relationship = Relationship.PARENT,
            createdAt = 0L,
            updatedAt = 0L,
        )
    }
}
