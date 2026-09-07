package com.ss.medrecord.ui.feature.medicine.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.domain.validation.MedicineValidator
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun MedicineEditRoute(
    onNavigateBack: () -> Unit,
    viewModel: MedicineEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                MedicineEditEffect.NavigateBack -> onNavigateBack()
                is MedicineEditEffect.ShowMessage ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    MedicineEditScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicineEditScreen(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.openDatePicker?.let { field ->
        MedicineDatePicker(
            field = field,
            initialEpochDay = when (field) {
                MedicineDateField.START -> state.startDateEpochDay
                MedicineDateField.END -> state.endDateEpochDay
            },
            onSelected = { onEvent(MedicineEditEvent.DateSelected(field, it)) },
            onDismiss = { onEvent(MedicineEditEvent.DatePickerDismissed) },
        )
    }

    if (state.isTimePickerOpen) {
        DoseTimePicker(
            onSelected = { onEvent(MedicineEditEvent.TimeAdded(it)) },
            onDismiss = { onEvent(MedicineEditEvent.TimePickerDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(text = if (state.isEditing) "Edit medicine" else "Add medicine")
                },
                navigationIcon = {
                    TextButton(onClick = { onEvent(MedicineEditEvent.BackClicked) }) {
                        Text(text = "Cancel")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PatientSelector(state = state, onEvent = onEvent)

            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(MedicineEditEvent.NameChanged(it)) },
                label = { Text(text = "Medicine name") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(text = it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.dosage,
                onValueChange = { onEvent(MedicineEditEvent.DosageChanged(it)) },
                label = { Text(text = "Dosage") },
                placeholder = { Text(text = "500 mg, 1 tablet, 5 ml") },
                singleLine = true,
                isError = state.dosageError != null,
                supportingText = state.dosageError?.let { { Text(text = it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            FrequencySelector(state = state, onEvent = onEvent)

            if (state.showTimes) {
                DoseTimes(state = state, onEvent = onEvent)
            }

            DateField(
                label = "Start date",
                epochDay = state.startDateEpochDay,
                error = state.startDateError,
                onOpen = {
                    onEvent(MedicineEditEvent.DatePickerRequested(MedicineDateField.START))
                },
            )

            DateField(
                label = "End date (leave empty if ongoing)",
                epochDay = state.endDateEpochDay,
                error = state.endDateError,
                onClear = { onEvent(MedicineEditEvent.DateSelected(MedicineDateField.END, null)) },
                onOpen = {
                    onEvent(MedicineEditEvent.DatePickerRequested(MedicineDateField.END))
                },
            )

            OutlinedTextField(
                value = state.instructions,
                onValueChange = { onEvent(MedicineEditEvent.InstructionsChanged(it)) },
                label = { Text(text = "Instructions") },
                placeholder = { Text(text = "After food, with water") },
                minLines = 2,
                isError = state.instructionsError != null,
                supportingText = state.instructionsError?.let { { Text(text = it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            VisitSelector(state = state, onEvent = onEvent)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Currently taking", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Turn this off to pause reminders without losing the record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.isActive,
                    onCheckedChange = { onEvent(MedicineEditEvent.ActiveChanged(it)) },
                )
            }

            Text(
                text = state.scheduleSummary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onEvent(MedicineEditEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(text = if (state.isEditing) "Save changes" else "Add medicine")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientSelector(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = state.selectedPatient?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(text = "Patient") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.patients.forEach { patient ->
                DropdownMenuItem(
                    text = { Text(text = patient.name) },
                    onClick = {
                        onEvent(MedicineEditEvent.PatientSelected(patient.patientId))
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitSelector(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = state.selectedVisit?.let { visitLabel(it) } ?: "Not from a logged visit",
                onValueChange = {},
                readOnly = true,
                label = { Text(text = "Prescribed at") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // The first option, not an afterthought: most of what a family
                // takes was never prescribed at an appointment this app knows
                // about, and a form that insists otherwise invites a wrong link.
                DropdownMenuItem(
                    text = { Text(text = "Not from a logged visit") },
                    onClick = {
                        onEvent(MedicineEditEvent.VisitSelected(null))
                        expanded = false
                    },
                )
                state.visits.forEach { visit ->
                    DropdownMenuItem(
                        text = { Text(text = visitLabel(visit)) },
                        onClick = {
                            onEvent(MedicineEditEvent.VisitSelected(visit.visit.visitId))
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FrequencySelector(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
) {
    Column {
        Text(text = "How often", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MedicineFrequency.entries.forEach { frequency ->
                FilterChip(
                    selected = state.frequency == frequency,
                    onClick = { onEvent(MedicineEditEvent.FrequencyChanged(frequency)) },
                    label = { Text(text = frequency.label) },
                )
            }
        }
    }
}

@Composable
private fun DoseTimes(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Reminder times", style = MaterialTheme.typography.labelMedium)
            TextButton(
                onClick = { onEvent(MedicineEditEvent.TimePickerRequested) },
                enabled = state.times.size < MedicineValidator.MAX_TIMES_PER_DAY,
            ) {
                Text(text = "Add time")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.times.forEach { minutes ->
                AssistChip(
                    onClick = { onEvent(MedicineEditEvent.TimeRemoved(minutes)) },
                    label = {
                        Text(text = Medicine.formatTime(Medicine.minutesToTime(minutes)))
                    },
                    trailingIcon = { Text(text = "×") },
                )
            }
        }

        Text(
            text = state.timesError ?: "Tap a time to remove it.",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.timesError != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun DateField(
    label: String,
    epochDay: Long?,
    error: String?,
    onOpen: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = epochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FORMAT) } ?: "",
        onValueChange = {},
        label = { Text(text = label) },
        readOnly = true,
        // Disabled rather than merely read-only, so the field can never take
        // focus and open a keyboard on a value only the picker can set.
        enabled = false,
        isError = error != null,
        supportingText = error?.let { { Text(text = it) } },
        trailingIcon = {
            Row {
                if (onClear != null && epochDay != null) {
                    TextButton(onClick = onClear) { Text(text = "Clear") }
                }
                TextButton(onClick = onOpen) {
                    Text(text = if (epochDay == null) "Set" else "Change")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicineDatePicker(
    field: MedicineDateField,
    initialEpochDay: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.let { it * MILLIS_PER_DAY },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSelected(
                        pickerState.selectedDateMillis?.let {
                            // The picker works in UTC midnight, so the epoch day
                            // is read back in UTC too. Converting through the
                            // device zone here would shift the date by one.
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                                .toEpochDay()
                        },
                    )
                },
            ) {
                Text(text = "OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    text = when (field) {
                        MedicineDateField.START -> "Start date"
                        MedicineDateField.END -> "End date"
                    },
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DoseTimePicker(
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Dose time",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TimePicker(state = pickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(text = "Cancel") }
                    TextButton(
                        onClick = { onSelected(pickerState.hour * 60 + pickerState.minute) },
                    ) {
                        Text(text = "Add")
                    }
                }
            }
        }
    }
}

private fun visitLabel(visit: VisitWithFacility): String =
    "${visit.facilityName} · ${visit.visit.visitDate.format(DATE_FORMAT)}"

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private const val MILLIS_PER_DAY = 86_400_000L

@Preview(showBackground = true)
@Composable
private fun MedicineEditScreenPreview() {
    MedRecordTheme {
        MedicineEditScreen(
            state = MedicineEditUiState(
                name = "Metformin",
                dosage = "500 mg",
                frequency = MedicineFrequency.DAILY,
                reminderTimes = listOf(8 * 60, 20 * 60),
                startDateEpochDay = LocalDate.now().toEpochDay(),
                instructions = "After food",
            ),
            onEvent = {},
        )
    }
}
