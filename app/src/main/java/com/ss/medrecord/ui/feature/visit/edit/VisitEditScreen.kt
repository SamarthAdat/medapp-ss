package com.ss.medrecord.ui.feature.visit.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.validation.VisitValidator
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedDropdownField
import com.ss.medrecord.ui.components.MedListRow
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedSelectField
import com.ss.medrecord.ui.components.MedTextField
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.medDatePickerColors
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography
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

    MedScreen(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            MedTopBar(
                title = if (state.isEditing) "Edit visit" else "Add visit",
                onBack = { onEvent(VisitEditEvent.BackClicked) },
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
                MedDropdownField(
                    label = "Patient",
                    options = state.patients,
                    selected = state.selectedPatient,
                    optionLabel = { "${it.name} · ${it.relationship.label}" },
                    onSelected = { patient ->
                        // Required: a visit with no patient has nowhere to be
                        // filed, so a null here is ignored rather than written.
                        patient?.let { onEvent(VisitEditEvent.PatientSelected(it.patientId)) }
                    },
                    enabled = !state.isSubmitting,
                )
            } else {
                state.selectedPatient?.let { patient ->
                    Text(
                        text = "Recording a visit for ${patient.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MedTheme.colors.textSecondary,
                    )
                }
            }

            FacilityField(state = state, onEvent = onEvent)

            if (state.willCreateFacility) {
                // Only relevant when a new clinic is about to be created; asking
                // for a type on an existing one would be a pointless choice.
                MedDropdownField(
                    label = "Type",
                    options = FacilityType.entries,
                    selected = state.facilityType,
                    optionLabel = { it.label },
                    onSelected = { type ->
                        type?.let { onEvent(VisitEditEvent.FacilityTypeChanged(it)) }
                    },
                    enabled = !state.isSubmitting,
                )
            }

            DateField(
                label = "Visit date",
                epochDay = state.visitDateEpochDay,
                error = state.visitDateError,
                onOpen = { onEvent(VisitEditEvent.DatePickerRequested(VisitDateField.VISIT)) },
            )

            MedTextField(
                value = state.doctorName,
                onValueChange = { onEvent(VisitEditEvent.DoctorNameChanged(it)) },
                label = "Doctor name (optional)",
                placeholder = "e.g. Dr Mehta",
                error = state.doctorNameError,
                enabled = !state.isSubmitting,
            )

            MedTextField(
                value = state.notes,
                onValueChange = { onEvent(VisitEditEvent.NotesChanged(it)) },
                label = "Diagnosis or notes (optional)",
                placeholder = "What was said, what was ordered",
                error = state.notesError,
                enabled = !state.isSubmitting,
                singleLine = false,
                minLines = 3,
            )

            DateField(
                label = "Next visit (optional)",
                epochDay = state.nextVisitDateEpochDay,
                error = state.nextVisitDateError,
                onOpen = { onEvent(VisitEditEvent.DatePickerRequested(VisitDateField.NEXT_VISIT)) },
            )

            Text(
                text = "Save the visit first. Reports and prescribed medicines are " +
                    "then attached from the visit record.",
                style = MaterialTheme.typography.bodySmall,
                color = MedTheme.colors.textTertiary,
            )

            MedPrimaryButton(
                text = if (state.isEditing) "Save changes" else "Save visit",
                onClick = { onEvent(VisitEditEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Free-text with suggestions rather than a plain dropdown: a clinic the user
 * has not saved yet must be enterable without a separate "add facility" trip.
 */
@Composable
private fun FacilityField(state: VisitEditUiState, onEvent: (VisitEditEvent) -> Unit) {
    val colors = MedTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        MedTextField(
            value = state.facilityName,
            onValueChange = { onEvent(VisitEditEvent.FacilityNameChanged(it)) },
            label = "Clinic or hospital",
            placeholder = "e.g. City Care Clinic",
            error = state.facilityError,
            enabled = !state.isSubmitting,
            // The suggestions belong to the field, so they go away with it.
            // Left open, they sit over the next question down.
            onFocusChange = { focused ->
                if (!focused) onEvent(VisitEditEvent.FacilitySuggestionsDismissed)
            },
        )

        if (state.facilityError == null && state.willCreateFacility) {
            Text(
                text = "New · will be saved to your facilities",
                style = MedTypography.monoCaption,
                color = colors.jade,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }

        val suggestions = state.facilitySuggestions
        if (state.showFacilitySuggestions && suggestions.isNotEmpty()) {
            MedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                containerColor = colors.cardRaised,
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                suggestions.take(MAX_SUGGESTIONS).forEach { facility ->
                    MedListRow(
                        title = facility.name,
                        subtitle = facility.type.label,
                        icon = MedIcons.LocalHospital,
                        accent = colors.jade,
                        showChevron = false,
                        onClick = { onEvent(VisitEditEvent.FacilitySelected(facility)) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
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
    MedSelectField(
        value = epochDay?.let(::formatEpochDay),
        label = label,
        placeholder = "Not set",
        icon = MedIcons.Event,
        error = error,
        onClick = onOpen,
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
        colors = medDatePickerColors(),
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
        DatePicker(state = pickerState, colors = medDatePickerColors())
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
