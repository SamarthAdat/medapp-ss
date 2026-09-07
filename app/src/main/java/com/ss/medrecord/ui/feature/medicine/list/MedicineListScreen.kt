package com.ss.medrecord.ui.feature.medicine.list

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.ui.components.EmptyState
import com.ss.medrecord.core.ui.components.FullScreenLoading
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.MedicineWithContext
import com.ss.medrecord.domain.model.Reminder
import com.ss.medrecord.ui.components.MedicineTile
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MedicineListRoute(
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: MedicineListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.onEvent(MedicineListEvent.PermissionsRechecked) }

    // Both permissions are granted somewhere else - a system dialog or a
    // settings screen - and neither reports back. Re-reading them on resume is
    // what makes the warning disappear once the user has acted on it.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(MedicineListEvent.PermissionsRechecked)
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                MedicineListEffect.NavigateBack -> onNavigateBack()
                MedicineListEffect.NavigateToAdd -> onNavigateToAdd()
                is MedicineListEffect.NavigateToEdit -> onNavigateToEdit(effect.medicineId)
                is MedicineListEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)

                MedicineListEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // No runtime permission to ask for below API 33, so a
                        // blocked state there means the user switched
                        // notifications off in settings - which is where they
                        // have to switch them back on.
                        context.openAppNotificationSettings()
                    }
                }

                MedicineListEffect.OpenExactAlarmSettings ->
                    context.openExactAlarmSettings()
            }
        }
    }

    MedicineListScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineListScreen(
    state: MedicineListUiState,
    onEvent: (MedicineListEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.pendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { onEvent(MedicineListEvent.DeleteDismissed) },
            title = { Text(text = "Delete ${target.medicine.name}?") },
            text = {
                Text(
                    text = "Its reminders stop immediately. The record is recoverable " +
                        "for 30 days.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(MedicineListEvent.DeleteConfirmed) }) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(MedicineListEvent.DeleteDismissed) }) {
                    Text(text = "Cancel")
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Medicines") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(MedicineListEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEvent(MedicineListEvent.AddClicked) },
                text = { Text(text = "Add medicine") },
                icon = {},
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> FullScreenLoading(modifier = Modifier.padding(innerPadding))

            state.hasNoPatient -> EmptyState(
                title = "No patient selected",
                description = "Choose a patient profile to see what they are taking.",
                modifier = Modifier.padding(innerPadding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    // Clears the FAB so the last row is never trapped under it.
                    bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.shouldWarnAboutDelivery) {
                    item {
                        DeliveryWarning(state = state, onEvent = onEvent)
                    }
                }

                if (state.upcomingToday.isNotEmpty()) {
                    item { TodaySection(reminders = state.upcomingToday) }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = state.showAllPatients,
                            onClick = { onEvent(MedicineListEvent.ToggleAllPatients) },
                            label = { Text(text = "All patients") },
                        )
                        FilterChip(
                            selected = state.showInactive,
                            onClick = { onEvent(MedicineListEvent.ToggleShowInactive) },
                            label = { Text(text = "Include paused") },
                        )
                    }
                }

                if (state.isEmpty) {
                    item {
                        // Not EmptyState here: it fills the viewport, which
                        // inside a LazyColumn item means an unbounded height.
                        Column(modifier = Modifier.padding(vertical = 32.dp)) {
                            Text(
                                text = "Nothing recorded yet",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Add a medicine to keep track of it and be " +
                                    "reminded when a dose is due.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }

                items(state.medicines, key = { it.medicine.medicineId }) { entry ->
                    Column {
                        MedicineTile(
                            medicine = entry.medicine,
                            subtitle = subtitleFor(entry, state.showAllPatients),
                            onClick = { onEvent(MedicineListEvent.MedicineClicked(entry)) },
                            onToggleActive = { active ->
                                onEvent(MedicineListEvent.ActiveToggled(entry, active))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = { onEvent(MedicineListEvent.DeleteRequested(entry)) },
                            ) {
                                Text(text = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Says plainly that the schedule is stored but will not ring, and offers the
 * one action that fixes it. Silently failing to remind someone about medication
 * is the worst outcome this screen can produce, so it is stated rather than
 * left to be discovered.
 */
@Composable
private fun DeliveryWarning(
    state: MedicineListUiState,
    onEvent: (MedicineListEvent) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (state.notificationsBlocked) {
                Text(
                    text = "Reminders are off",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Doses are still recorded here, but this device will not " +
                        "notify you about them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = { onEvent(MedicineListEvent.FixNotificationsClicked) }) {
                    Text(text = "Turn on reminders")
                }
            } else {
                Text(
                    text = "Reminders may arrive late",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Without permission for exact alarms, Android may delay a " +
                        "dose reminder while the device is idle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                TextButton(onClick = { onEvent(MedicineListEvent.FixExactAlarmsClicked) }) {
                    Text(text = "Allow exact alarms")
                }
            }
        }
    }
}

@Composable
private fun TodaySection(reminders: List<Reminder>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Still due today",
                style = MaterialTheme.typography.titleSmall,
            )
            reminders.forEach { reminder ->
                Text(
                    text = "${formatClock(reminder.triggerAtMillis)}  ${reminder.title}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun subtitleFor(entry: MedicineWithContext, showAllPatients: Boolean): String? {
    val parts = buildList {
        if (showAllPatients) entry.patientName?.let(::add)
        entry.facilityName?.let { facility ->
            val date = entry.visitDate?.format(DATE_FORMAT)
            add(if (date == null) facility else "$facility · $date")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

private fun formatClock(millis: Long): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(time.hour, time.minute)
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Opens where the user can turn notifications back on.
 *
 * ACTION_APP_NOTIFICATION_SETTINGS only exists from API 26; below that there is
 * no per-app notification screen, so the app's details page is the closest
 * thing. Without the branch the intent silently fails on API 24-25 and the
 * button does nothing - the worst kind of bug, because the user concludes the
 * app is broken rather than that their device is old.
 */
private fun Context.openAppNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$packageName".toUri())
    }
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData("package:$packageName".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

@Preview(showBackground = true)
@Composable
private fun MedicineListScreenPreview() {
    MedRecordTheme {
        MedicineListScreen(
            state = MedicineListUiState(
                isLoading = false,
                showAllPatients = true,
                allMedicines = listOf(
                    MedicineWithContext(
                        medicine = Medicine(
                            medicineId = "m1",
                            userId = "u1",
                            patientId = "p1",
                            name = "Metformin",
                            dosage = "500 mg",
                            frequency = MedicineFrequency.DAILY,
                            reminderTimes = listOf(8 * 60, 20 * 60),
                            startDateEpochDay = LocalDate.now().minusDays(20).toEpochDay(),
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                        patientName = "Asha Rao",
                        facilityName = "City Care Clinic",
                        visitDateEpochDay = LocalDate.now().minusDays(20).toEpochDay(),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
