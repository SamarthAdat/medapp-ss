package com.ss.medrecord.ui.feature.visit.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.validation.VisitValidator
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun VisitEditRoute(
    onNavigateBack: () -> Unit,
    viewModel: VisitEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                VisitEditEffect.NavigateBack -> onNavigateBack()
                is VisitEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    VisitEditScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEditScreen(
    state: VisitEditUiState,
    onEvent: (VisitEditEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.openDatePicker?.let { field ->
        VisitDatePicker(
            field = field,
            initialEpochDay = when (field) {
                VisitDateField.VISIT -> state.visitDateEpochDay
                VisitDateField.NEXT_VISIT -> state.nextVisitDateEpochDay
            },
            onSelected = { onEvent(VisitEditEvent.DateSelected(field, it)) },
            onDismiss = { onEvent(VisitEditEvent.DatePickerDismissed) },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = if (state.isEditing) "Edit visit" else "Add visit") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(VisitEditEvent.BackClicked) }) {
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
            if (state.patients.size > 1) {
                PatientDropdown(state = state, onEvent = onEvent)
            } else {
                state.selectedPatient?.let { patient ->
                    Text(
                        text = "Recording a visit for ${patient.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FacilityField(state = state, onEvent = onEvent)

            if (state.willCreateFacility) {
                // Only relevant when a new clinic is about to be created; asking
                // for a type on an existing one would be a pointless choice.
                FacilityTypeDropdown(
                    selected = state.facilityType,
                    onSelected = { onEvent(VisitEditEvent.FacilityTypeChanged(it)) },
                    enabled = !state.isSubmitting,
                )
            }

            DateField(
                label = "Visit date",
                epochDay = state.visitDateEpochDay,
                error = state.visitDateError,
                onOpen = { onEvent(VisitEditEvent.DatePickerRequested(VisitDateField.VISIT)) },
            )

            OutlinedTextField(
                value = state.doctorName,
                onValueChange = { onEvent(VisitEditEvent.DoctorNameChanged(it)) },
                label = { Text(text = "Doctor name (optional)") },
                singleLine = true,
                isError = state.doctorNameError != null,
                supportingText = state.doctorNameError?.let { { Text(text = it) } },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.notes,
                onValueChange = { onEvent(VisitEditEvent.NotesChanged(it)) },
                label = { Text(text = "Diagnosis or notes (optional)") },
                minLines = 3,
                isError = state.notesError != null,
                supportingText = state.notesError?.let { { Text(text = it) } },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = "Next visit (optional)",
                epochDay = state.nextVisitDateEpochDay,
                error = state.nextVisitDateError,
                onOpen = { onEvent(VisitEditEvent.DatePickerRequested(VisitDateField.NEXT_VISIT)) },
            )

            Text(
                text = "Report attachments and prescribed medicines arrive in Phases 5 and 6.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onEvent(VisitEditEvent.Submit) },
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                if (state.isSubmitting) {
                    Box(modifier = Modifier.size(20.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } else {
                    Text(text = if (state.isEditing) "Save changes" else "Save visit")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatientDropdown(state: VisitEditUiState, onEvent: (VisitEditEvent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!state.isSubmitting) expanded = it },
    ) {
        OutlinedTextField(
            value = state.selectedPatient?.name.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = !state.isSubmitting,
            label = { Text(text = "Patient") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, !state.isSubmitting),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.patients.forEach { patient ->
                DropdownMenuItem(
                    text = { Text(text = "${patient.name} - ${patient.relationship.label}") },
                    onClick = {
                        onEvent(VisitEditEvent.PatientSelected(patient.patientId))
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Free-text with suggestions rather than a plain dropdown: a clinic the user
 * has not saved yet must be enterable without a separate "add facility" trip.
 */
@Composable
private fun FacilityField(state: VisitEditUiState, onEvent: (VisitEditEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.facilityName,
            onValueChange = { onEvent(VisitEditEvent.FacilityNameChanged(it)) },
            label = { Text(text = "Clinic or hospital") },
            singleLine = true,
            isError = state.facilityError != null,
            supportingText = {
                when {
                    state.facilityError != null -> Text(text = state.facilityError)
                    state.willCreateFacility -> Text(text = "New - will be saved to your facilities")
                    else -> Unit
                }
            },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        val suggestions = state.facilitySuggestions
        if (state.showFacilitySuggestions && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                suggestions.take(MAX_SUGGESTIONS).forEachIndexed { index, facility ->
                    if (index > 0) HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(VisitEditEvent.FacilitySelected(facility)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(text = facility.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = facility.type.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacilityTypeDropdown(
    selected: FacilityType,
    onSelected: (FacilityType) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = "Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FacilityType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(text = type.label) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DateField(
    label: String,
    epochDay: Long?,
    error: String?,
    onOpen: () -> Unit,
) {
    OutlinedTextField(
        value = epochDay?.let(::formatEpochDay) ?: "",
        onValueChange = {},
        label = { Text(text = label) },
        readOnly = true,
        enabled = false,
        isError = error != null,
        supportingText = error?.let { { Text(text = it) } },
        trailingIcon = {
            TextButton(onClick = onOpen) {
                Text(text = if (epochDay == null) "Set" else "Change")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisitDatePicker(
    field: VisitDateField,
    initialEpochDay: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val todayUtcMillis = remember { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.let { it * MILLIS_PER_DAY },
        // A visit happened in the past; a follow-up is ahead. Constraining each
        // field separately stops the two being confused for one another.
        yearRange = when (field) {
            VisitDateField.VISIT ->
                (today.year - VisitValidator.MAX_HISTORY_YEARS.toInt())..today.year

            VisitDateField.NEXT_VISIT -> today.year..(today.year + FUTURE_YEARS)
        },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = when (field) {
                VisitDateField.VISIT -> utcTimeMillis <= todayUtcMillis
                VisitDateField.NEXT_VISIT -> true
            }
        },
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSelected(
                        pickerState.selectedDateMillis?.let { millis ->
                            // Converting through UTC keeps the calendar day the
                            // user tapped, whatever zone the device is in.
                            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                                .toEpochDay()
                        },
                    )
                },
            ) {
                Text(text = "Select")
            }
        },
        dismissButton = {
            TextButton(onClick = { onSelected(null) }) { Text(text = "Clear") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L
private const val MAX_SUGGESTIONS = 5
private const val FUTURE_YEARS = 5

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatEpochDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun VisitEditScreenPreview() {
    MedRecordTheme {
        VisitEditScreen(
            state = VisitEditUiState(
                facilityName = "City Care",
                visitDateEpochDay = LocalDate.now().toEpochDay(),
                doctorName = "Dr Mehta",
                facilities = listOf(
                    Facility(
                        facilityId = "f1",
                        userId = "u1",
                        name = "City Care Clinic",
                        type = FacilityType.CLINIC,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                ),
                showFacilitySuggestions = true,
            ),
            onEvent = {},
        )
    }
}
