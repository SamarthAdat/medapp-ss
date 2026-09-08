package com.ss.medrecord.ui.feature.medicine.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.VisitWithFacility
import com.ss.medrecord.domain.validation.MedicineValidator
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedDropdownField
import com.ss.medrecord.ui.components.MedFilterChip
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedSelectField
import com.ss.medrecord.ui.components.MedTextField
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.SectionLabel
import com.ss.medrecord.ui.components.medDatePickerColors
import com.ss.medrecord.ui.components.medTimePickerColors
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography
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

    MedScreen(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        glow = MedTheme.colors.violet,
        topBar = {
            MedTopBar(
                title = if (state.isEditing) "Edit medicine" else "Add medicine",
                onBack = { onEvent(MedicineEditEvent.BackClicked) },
            )
        },
    ) { innerPadding ->
        val colors = MedTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MedDropdownField(
                label = "Patient",
                options = state.patients,
                selected = state.selectedPatient,
                optionLabel = { it.name },
                onSelected = { patient ->
                    // Required: a medicine with no patient has nowhere to be
                    // filed, so a null is ignored rather than written.
                    patient?.let { onEvent(MedicineEditEvent.PatientSelected(it.patientId)) }
                },
            )

            MedTextField(
                value = state.name,
                onValueChange = { onEvent(MedicineEditEvent.NameChanged(it)) },
                label = "Medicine name",
                placeholder = "e.g. Metformin",
                error = state.nameError,
            )

            MedTextField(
                value = state.dosage,
                onValueChange = { onEvent(MedicineEditEvent.DosageChanged(it)) },
                label = "Dosage",
                placeholder = "e.g. 500 mg, 1 tablet, 5 ml",
                error = state.dosageError,
            )

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

            MedTextField(
                value = state.instructions,
                onValueChange = { onEvent(MedicineEditEvent.InstructionsChanged(it)) },
                label = "Instructions",
                placeholder = "e.g. after food, with water",
                error = state.instructionsError,
                singleLine = false,
                minLines = 2,
            )

            MedDropdownField(
                label = "Prescribed at",
                options = state.visits,
                selected = state.selectedVisit,
                optionLabel = { visitLabel(it) },
                onSelected = { visit ->
                    onEvent(MedicineEditEvent.VisitSelected(visit?.visit?.visitId))
                },
                placeholder = NOT_FROM_A_VISIT,
                // The first option, not an afterthought: most of what a family
                // takes was never prescribed at an appointment this app knows
                // about, and a form that insists otherwise invites a wrong link.
                emptyOption = NOT_FROM_A_VISIT,
            )

            MedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Currently taking",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Turn this off to pause reminders without losing " +
                                "the record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = state.isActive,
                        onCheckedChange = { onEvent(MedicineEditEvent.ActiveChanged(it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onViolet,
                            checkedTrackColor = colors.violet,
                            uncheckedTrackColor = colors.cardHighest,
                            uncheckedBorderColor = colors.hairlineStrong,
                        ),
                    )
                }
            }

            Text(
                text = state.scheduleSummary,
                style = MedTypography.monoCaption,
                color = colors.textTertiary,
            )

            MedPrimaryButton(
                text = if (state.isEditing) "Save changes" else "Add medicine",
                onClick = { onEvent(MedicineEditEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                container = colors.violet,
                onContainer = colors.onViolet,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun FrequencySelector(
    state: MedicineEditUiState,
    onEvent: (MedicineEditEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("How often")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MedicineFrequency.entries.forEach { frequency ->
                MedFilterChip(
                    text = frequency.label,
                    selected = state.frequency == frequency,
                    onClick = { onEvent(MedicineEditEvent.FrequencyChanged(frequency)) },
                    accent = MedTheme.colors.violet,
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
    val colors = MedTheme.colors
    val canAddMore = state.times.size < MedicineValidator.MAX_TIMES_PER_DAY
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Reminder times")
            Text(
                text = "Add time",
                style = MaterialTheme.typography.titleSmall,
                color = if (canAddMore) colors.violet else colors.textTertiary,
                modifier = Modifier.clickable(enabled = canAddMore) {
                    onEvent(MedicineEditEvent.TimePickerRequested)
                },
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.times.forEach { minutes ->
                // The whole chip removes the time, which is why the cross is
                // decoration rather than a second, smaller tap target.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(colors.violet.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            colors.violet.copy(alpha = 0.30f),
                            RoundedCornerShape(percent = 50),
                        )
                        .clickable { onEvent(MedicineEditEvent.TimeRemoved(minutes)) }
                        .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = Medicine.formatTime(Medicine.minutesToTime(minutes)),
                        style = MedTypography.monoNumber,
                        color = colors.textPrimary,
                    )
                    MedIconGlyph(
                        icon = MedIcons.Close,
                        size = 16.dp,
                        tint = colors.violet,
                        contentDescription = "Remove this time",
                    )
                }
            }
        }

        Text(
            text = state.timesError ?: "Tap a time to remove it.",
            style = MaterialTheme.typography.bodySmall,
            color = if (state.timesError != null) colors.coral else colors.textTertiary,
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
    val colors = MedTheme.colors
    Column {
        MedSelectField(
            value = epochDay?.let { LocalDate.ofEpochDay(it).format(DATE_FORMAT) },
            label = label,
            placeholder = "Not set",
            icon = MedIcons.Event,
            error = error,
            onClick = onOpen,
        )
        // Clearing is offered below rather than as a second control inside the
        // field: two tap targets in one row on a date the picker owns is how
        // people clear a value they meant to change.
        if (onClear != null && epochDay != null) {
            Text(
                text = "Clear",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 16.dp, top = 6.dp)
                    .clickable(onClick = onClear),
            )
        }
    }
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
        colors = medDatePickerColors(),
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
                TimePicker(state = pickerState, colors = medTimePickerColors())
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

/** One string, so the placeholder and the empty option cannot drift apart. */
private const val NOT_FROM_A_VISIT = "Not from a logged visit"

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
