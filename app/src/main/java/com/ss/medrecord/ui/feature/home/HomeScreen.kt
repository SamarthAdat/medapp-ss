package com.ss.medrecord.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.DashboardSnapshot
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.RecordCounts
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.TimelineEntry
import com.ss.medrecord.domain.model.TimelineKind
import com.ss.medrecord.domain.model.UpcomingAppointment
import com.ss.medrecord.ui.components.AdherenceRing
import com.ss.medrecord.ui.components.HeroCard
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedEmptyState
import com.ss.medrecord.ui.components.MedIconButton
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.PatientChip
import com.ss.medrecord.ui.components.SectionHeader
import com.ss.medrecord.ui.components.SectionLabel
import com.ss.medrecord.ui.components.SparklineBars
import com.ss.medrecord.ui.components.SyncStatusIndicator
import com.ss.medrecord.ui.components.TimelineRow
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stateful entry point: owns the ViewModel and translates one-shot effects into
 * navigation calls. Kept thin so the stateless [HomeScreen] stays previewable.
 */
@Composable
fun HomeRoute(
    onNavigateToPatients: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAddPatient: () -> Unit,
    onNavigateToVisits: () -> Unit,
    onNavigateToAddVisit: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToMedicines: () -> Unit,
    onNavigateToTimeline: () -> Unit,
    onNavigateToFacilities: () -> Unit,
    onOpenVisit: (String) -> Unit,
    onOpenReport: (String) -> Unit,
    onOpenMedicine: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                HomeEffect.NavigateToPatients -> onNavigateToPatients()
                HomeEffect.NavigateToSettings -> onNavigateToSettings()
                HomeEffect.NavigateToAddPatient -> onNavigateToAddPatient()
                HomeEffect.NavigateToVisits -> onNavigateToVisits()
                HomeEffect.NavigateToAddVisit -> onNavigateToAddVisit()
                HomeEffect.NavigateToReports -> onNavigateToReports()
                HomeEffect.NavigateToMedicines -> onNavigateToMedicines()
                HomeEffect.NavigateToTimeline -> onNavigateToTimeline()
                HomeEffect.NavigateToFacilities -> onNavigateToFacilities()
                is HomeEffect.NavigateToVisit -> onOpenVisit(effect.visitId)
                is HomeEffect.NavigateToReport -> onOpenReport(effect.reportId)
                is HomeEffect.NavigateToMedicine -> onOpenMedicine(effect.medicineId)
                is HomeEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * The dashboard (spec section 5.3).
 *
 * Ordered by how time-sensitive each section is rather than by how the data is
 * stored: anything to act on today comes first, then who is on screen, then
 * what is on file, then history. A dashboard that opens with a record count is
 * one nobody reads twice.
 *
 * The figures are drawn rather than listed - a ring for the day's doses, bars
 * for the year's visits - because the aggregates were already being computed
 * and a shape answers "is this a normal year" faster than a number does.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MedTheme.colors
    val dashboard = state.dashboard

    Box(modifier = modifier.fillMaxSize().background(colors.canvas)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                HomeBottomBar(
                    onEvent = onEvent,
                    enabled = !state.needsFirstPatient,
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { GreetingHeader(state = state, onEvent = onEvent) }

                if (state.needsFirstPatient) {
                    item {
                        MedEmptyState(
                            title = "Add your first patient",
                            message = "Every record is kept against a person. Start with " +
                                "yourself, then add family or dependents.",
                            icon = MedIcons.PersonAdd,
                            actionText = "Add patient",
                            onAction = { onEvent(HomeEvent.AddFirstPatient) },
                        )
                    }
                } else {
                    if (dashboard.patients.size > 1) {
                        item { PatientChipRow(dashboard = dashboard, onEvent = onEvent) }
                    }

                    dashboard.upcomingAppointments.firstOrNull()?.let { appointment ->
                        item { NextAppointmentCard(appointment = appointment, onEvent = onEvent) }
                    }

                    if (dashboard.dosesScheduledToday > 0) {
                        item { DosesCard(dashboard = dashboard, onEvent = onEvent) }
                    }

                    item { VisitsChartCard(dashboard = dashboard, onEvent = onEvent) }

                    state.allergies?.let { allergies ->
                        item { AllergyCard(allergies = allergies) }
                    }

                    item { CountsRow(counts = dashboard.counts, onEvent = onEvent) }

                    if (dashboard.recentActivity.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recent activity",
                                trailing = "See all",
                                onTrailingClick = { onEvent(HomeEvent.OpenTimeline) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        items(dashboard.recentActivity, key = { it.id }) { entry ->
                            TimelineRow(
                                entry = entry,
                                onClick = { onEvent(HomeEvent.ActivityClicked(entry)) },
                                // Recent activity spans the whole account, so a row
                                // that did not say whose it is would be ambiguous the
                                // moment there is more than one profile.
                                showPatientName = true,
                            )
                        }
                    }
                }

                item {
                    // Tapping the indicator is the manual "Sync now" from spec 5.13.
                    SyncStatusIndicator(
                        status = state.syncStatus,
                        onClick = { onEvent(HomeEvent.SyncNowClicked) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Date, greeting, and the way out to Settings.
 *
 * The greeting is derived from the clock rather than stored, which is the sort
 * of small thing that makes an app feel like it is running now rather than
 * showing a cached screen.
 */
@Composable
private fun GreetingHeader(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    val colors = MedTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = LocalDate.now().format(HEADER_DATE_FORMAT),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = greetingFor(LocalTime.now()),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cardRaised)
                // In light the raised surface is white on an off-white page, so
                // without the hairline the tile has no edge at all.
                .border(1.dp, colors.hairline, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MedIconButton(
                icon = MedIcons.Settings,
                onClick = { onEvent(HomeEvent.OpenSettings) },
                contentDescription = "Settings",
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * The profile switcher, inline.
 *
 * Shown only when there is more than one profile: a single chip that cannot
 * change anything is a control that teaches the user it does nothing.
 */
@Composable
private fun PatientChipRow(dashboard: DashboardSnapshot, onEvent: (HomeEvent) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        dashboard.patients.forEach { patient ->
            PatientChip(
                name = patient.name,
                seed = patient.patientId,
                selected = patient.patientId == dashboard.activePatient?.patientId,
                onClick = { onEvent(HomeEvent.PatientSelected(patient.patientId)) },
            )
        }
    }
}

/**
 * The next appointment, as the one thing on this screen with a deadline.
 *
 * It gets the gradient and the large figure because it is the only item here
 * that stops being useful if it is read tomorrow instead of today.
 */
@Composable
private fun NextAppointmentCard(
    appointment: UpcomingAppointment,
    onEvent: (HomeEvent) -> Unit,
) {
    val colors = MedTheme.colors
    val days = appointment.daysAway()

    HeroCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEvent(HomeEvent.AppointmentClicked(appointment.visitId)) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("Next appointment", color = colors.onHeroMuted)
                Text(
                    text = appointment.facilityName ?: "Appointment",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onHero,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = listOfNotNull(appointment.doctorName, appointment.patientName)
                        .joinToString(" · ")
                        .ifBlank { appointment.date.format(DATE_FORMAT) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onHeroMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                // "Today" and "Tomorrow" replace the figure entirely: a large
                // "0" beside "days away" is a puzzle, not a headline.
                when (days) {
                    0L -> Text(
                        text = "Today",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.heroFigure,
                    )

                    1L -> Text(
                        text = "Tomorrow",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.heroFigure,
                    )

                    else -> {
                        Text(
                            text = days.toString(),
                            style = MedTypography.heroFigure,
                            color = colors.heroFigure,
                        )
                        Text(
                            text = "days away",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onHeroMuted,
                        )
                    }
                }
            }
        }

        Text(
            text = appointment.date.format(DATE_FORMAT),
            style = MedTypography.monoCaption,
            color = colors.onHeroMuted,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/**
 * Today's medicine schedule as a ring.
 *
 * The label says how many are *left*, not how many were taken. The app arms
 * reminders and records that they fired; it has never known whether a tablet
 * was actually swallowed, and a ring labelled "taken" would quietly claim
 * otherwise.
 */
@Composable
private fun DosesCard(dashboard: DashboardSnapshot, onEvent: (HomeEvent) -> Unit) {
    val colors = MedTheme.colors
    val scheduled = dashboard.dosesScheduledToday
    val elapsed = dashboard.dosesElapsedToday
    val remaining = scheduled - elapsed
    val nextDose = dashboard.dosesDueToday.firstOrNull()

    MedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEvent(HomeEvent.OpenMedicines) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AdherenceRing(
                progress = if (scheduled == 0) 0f else elapsed.toFloat() / scheduled,
                label = "$elapsed/$scheduled",
                accent = colors.violet,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Doses today",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = when {
                        remaining == 0 -> "All of today's reminders have gone off"
                        nextDose != null ->
                            "$remaining left, next at ${formatClock(nextDose.triggerAtMillis)}"

                        else -> "$remaining left today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** Visits per month across the year, as a shape rather than twelve numbers. */
@Composable
private fun VisitsChartCard(dashboard: DashboardSnapshot, onEvent: (HomeEvent) -> Unit) {
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onEvent(HomeEvent.OpenVisits) },
    ) {
        SectionHeader(
            title = "Visits this year",
            trailing = "${dashboard.visitsThisYear} total",
        )
        SparklineBars(
            values = dashboard.visitsByMonth,
            accent = colors.jade,
            // Only the months that have happened are drawn in the accent.
            filledThrough = LocalDate.now().monthValue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("JAN", "MAR", "MAY", "JUL", "SEP", "NOV").forEach { month ->
                Text(
                    text = month,
                    style = MedTypography.monoMicro,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * Allergies, in coral, with nothing else in the card.
 *
 * It is the one field on this screen that changes what somebody else should do
 * in an emergency, so it is not allowed to share a card with a record count.
 */
@Composable
private fun AllergyCard(allergies: String) {
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.coral,
        containerColor = colors.coral.copy(alpha = if (colors.isDark) 0.12f else 0.08f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MedIconGlyph(
                icon = MedIcons.Warning,
                size = 20.dp,
                tint = colors.coral,
                contentDescription = null,
            )
            Column {
                Text(
                    text = "Allergies",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.coral,
                )
                Text(
                    text = allergies,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CountsRow(counts: RecordCounts, onEvent: (HomeEvent) -> Unit) {
    val colors = MedTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CountTile(
            label = "Visits",
            value = counts.visits,
            icon = MedIcons.Stethoscope,
            accent = colors.jade,
            onClick = { onEvent(HomeEvent.OpenVisits) },
            modifier = Modifier.weight(1f),
        )
        CountTile(
            label = "Reports",
            value = counts.reports,
            icon = MedIcons.Description,
            accent = colors.azure,
            onClick = { onEvent(HomeEvent.OpenReports) },
            modifier = Modifier.weight(1f),
        )
        CountTile(
            label = "Medicines",
            value = counts.activeMedicines,
            icon = MedIcons.Medication,
            accent = colors.violet,
            onClick = { onEvent(HomeEvent.OpenMedicines) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CountTile(
    label: String,
    value: Int,
    icon: com.ss.medrecord.ui.theme.MedIcon,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    MedCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp),
    ) {
        MedIconGlyph(icon = icon, size = 18.dp, tint = accent, contentDescription = null)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
        )
    }
}

/**
 * The bottom bar, with the add-visit action raised into the middle of it.
 *
 * Five slots, four of them the destinations people return to daily. Anything
 * rarer than that is reachable from a screen rather than from here - a bar
 * that holds everything holds nothing.
 */
@Composable
private fun HomeBottomBar(onEvent: (HomeEvent) -> Unit, enabled: Boolean) {
    val colors = MedTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.canvas)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomBarItem(
                icon = MedIcons.GridView,
                label = "Today",
                selected = true,
                onClick = { },
            )
            BottomBarItem(
                icon = MedIcons.History,
                label = "History",
                selected = false,
                onClick = { onEvent(HomeEvent.OpenTimeline) },
            )

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (enabled) colors.jade else colors.jade.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                MedIconButton(
                    icon = MedIcons.Add,
                    onClick = { onEvent(HomeEvent.AddVisit) },
                    contentDescription = "Add visit",
                    tint = colors.onJade,
                    enabled = enabled,
                )
            }

            BottomBarItem(
                icon = MedIcons.Medication,
                label = "Meds",
                selected = false,
                onClick = { onEvent(HomeEvent.OpenMedicines) },
            )
            BottomBarItem(
                icon = MedIcons.FolderOpen,
                label = "Files",
                selected = false,
                onClick = { onEvent(HomeEvent.OpenReports) },
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: com.ss.medrecord.ui.theme.MedIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MedTheme.colors
    val tint = if (selected) colors.jade else colors.textTertiary
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        MedIconGlyph(icon = icon, size = 24.dp, tint = tint, contentDescription = null)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 0..4 -> "Good evening"
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

private fun formatClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    val today = LocalDate.now()
    MedRecordTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                dashboard = DashboardSnapshot(
                    activePatient = Patient(
                        patientId = "p1",
                        userId = "u1",
                        name = "Asha Rao",
                        relationship = Relationship.SELF,
                        dateOfBirthEpochDay = today.minusYears(41).toEpochDay(),
                        knownAllergies = "Penicillin, sulfa drugs",
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                    patients = listOf(
                        Patient(
                            patientId = "p1",
                            userId = "u1",
                            name = "Asha Rao",
                            relationship = Relationship.SELF,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                        Patient(
                            patientId = "p2",
                            userId = "u1",
                            name = "Vikram Rao",
                            relationship = Relationship.CHILD,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                    ),
                    counts = RecordCounts(visits = 12, reports = 7, activeMedicines = 2),
                    visitsByMonth = listOf(1, 2, 0, 3, 1, 2, 0, 1, 2, 0, 0, 0),
                    upcomingAppointments = listOf(
                        UpcomingAppointment(
                            visitId = "v1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            facilityName = "City Care Clinic",
                            doctorName = "Dr Mehta",
                            onEpochDay = today.plusDays(2).toEpochDay(),
                        ),
                    ),
                    recentActivity = listOf(
                        TimelineEntry(
                            id = "VISIT:v1",
                            kind = TimelineKind.VISIT,
                            targetId = "v1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            title = "City Care Clinic",
                            subtitle = "Dr Mehta",
                            onEpochDay = today.minusDays(3).toEpochDay(),
                            recordedAtMillis = 0L,
                        ),
                        TimelineEntry(
                            id = "REPORT:r1",
                            kind = TimelineKind.REPORT,
                            targetId = "r1",
                            patientId = "p1",
                            patientName = "Asha Rao",
                            title = "blood-panel.pdf",
                            subtitle = "City Care Clinic · 726 KB",
                            onEpochDay = today.minusDays(3).toEpochDay(),
                            recordedAtMillis = 0L,
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
