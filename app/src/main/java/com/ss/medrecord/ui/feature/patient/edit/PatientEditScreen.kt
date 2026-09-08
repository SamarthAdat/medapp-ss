package com.ss.medrecord.ui.feature.patient.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.validation.PatientValidator
import com.ss.medrecord.ui.components.MedDropdownField
import com.ss.medrecord.ui.components.MedPrimaryButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedSelectField
import com.ss.medrecord.ui.components.MedTextField
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.medDatePickerColors
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun PatientEditRoute(
    onNavigateBack: () -> Unit,
    viewModel: PatientEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PatientEditEffect.NavigateBack -> onNavigateBack()
                is PatientEditEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    PatientEditScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientEditScreen(
    state: PatientEditUiState,
    onEvent: (PatientEditEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    if (state.showDatePicker) {
        DateOfBirthPicker(
            initialEpochDay = state.dateOfBirthEpochDay,
            onSelected = { onEvent(PatientEditEvent.DateOfBirthChanged(it)) },
            onDismiss = { onEvent(PatientEditEvent.DatePickerDismissed) },
        )
    }

    MedScreen(
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        topBar = {
            MedTopBar(
                title = if (state.isEditing) "Edit patient" else "Add patient",
                onBack = { onEvent(PatientEditEvent.BackClicked) },
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
            MedTextField(
                value = state.name,
                onValueChange = { onEvent(PatientEditEvent.NameChanged(it)) },
                label = "Full name",
                placeholder = "Who these records belong to",
                error = state.nameError,
                enabled = !state.isSubmitting,
            )

            MedDropdownField(
                label = "Relationship",
                options = Relationship.entries,
                selected = state.relationship,
                optionLabel = { it.label },
                onSelected = { relationship ->
                    // Required, so the dropdown offers no empty option and a
                    // null can only arrive from a bug - ignored rather than
                    // written over a valid choice.
                    relationship?.let { onEvent(PatientEditEvent.RelationshipChanged(it)) }
                },
                enabled = !state.isSubmitting,
            )

            // Tapped rather than typed: free-typed dates are a reliable source
            // of ambiguous day/month entry.
            MedSelectField(
                value = state.dateOfBirthEpochDay?.let(::formatEpochDay),
                label = "Date of birth (optional)",
                placeholder = "Not recorded",
                icon = MedIcons.Event,
                error = state.dateOfBirthError,
                enabled = !state.isSubmitting,
                onClick = { onEvent(PatientEditEvent.DatePickerRequested) },
            )

            MedDropdownField(
                label = "Gender (optional)",
                options = Gender.entries,
                selected = state.gender,
                optionLabel = { it.label },
                onSelected = { onEvent(PatientEditEvent.GenderChanged(it)) },
                enabled = !state.isSubmitting,
                placeholder = "Not recorded",
                emptyOption = "Not recorded",
            )

            MedDropdownField(
                label = "Blood group (optional)",
                options = BloodGroup.entries,
                selected = state.bloodGroup,
                optionLabel = { it.label },
                onSelected = { onEvent(PatientEditEvent.BloodGroupChanged(it)) },
                enabled = !state.isSubmitting,
                placeholder = "Not recorded",
                emptyOption = "Not recorded",
            )

            MedTextField(
                value = state.knownAllergies,
                onValueChange = { onEvent(PatientEditEvent.AllergiesChanged(it)) },
                label = "Known allergies (optional)",
                // Phrased as an instruction, not an example. A plausible allergy sitting
                // grey in an empty field is one somebody can read as recorded,
                // and this is the field where that mistake costs the most.
                placeholder = "Type any known allergies",
                error = state.allergiesError,
                enabled = !state.isSubmitting,
                singleLine = false,
                minLines = 3,
                imeAction = ImeAction.Done,
            )

            Text(
                text = "Profile photos are not stored yet. Reports and scans attach " +
                    "to a visit rather than to a profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MedTheme.colors.textTertiary,
            )

            MedPrimaryButton(
                text = if (state.isEditing) "Save changes" else "Add patient",
                onClick = { onEvent(PatientEditEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.isSubmitting,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateOfBirthPicker(
    initialEpochDay: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayUtcMillis = remember {
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val today = remember { LocalDate.now() }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochDay?.let { it * MILLIS_PER_DAY },
        // The default range runs to 2100, which is meaningless for a birth date
        // and buries the plausible years under decades of scrolling.
        yearRange = (today.year - PatientValidator.MAX_AGE_YEARS.toInt())..today.year,
        selectableDates = object : SelectableDates {
            // A birth date cannot be in the future; blocking it in the picker
            // beats explaining it in an error afterwards.
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis

            override fun isSelectableYear(year: Int) = year <= today.year
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
                            // The picker works in UTC millis; converting through
                            // UTC keeps the calendar day the user tapped.
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

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatEpochDay(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT)

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PatientEditScreenPreview() {
    MedRecordTheme {
        PatientEditScreen(
            state = PatientEditUiState(
                name = "Asha Rao",
                relationship = Relationship.SELF,
                dateOfBirthEpochDay = 10_000L,
                bloodGroup = BloodGroup.O_POSITIVE,
            ),
            onEvent = {},
        )
    }
}
