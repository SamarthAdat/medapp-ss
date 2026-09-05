package com.ss.medrecord.ui.feature.patient.edit

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.validation.PatientValidator
import com.ss.medrecord.ui.theme.MedRecordTheme
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = if (state.isEditing) "Edit patient" else "Add patient") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(PatientEditEvent.BackClicked) }) {
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
            LabelledTextField(
                value = state.name,
                onValueChange = { onEvent(PatientEditEvent.NameChanged(it)) },
                label = "Full name",
                errorMessage = state.nameError,
                enabled = !state.isSubmitting,
            )

            EnumDropdown(
                label = "Relationship",
                options = Relationship.entries,
                selected = state.relationship,
                optionLabel = { it.label },
                onSelected = { onEvent(PatientEditEvent.RelationshipChanged(it)) },
                enabled = !state.isSubmitting,
            )

            // Read-only field that opens the picker: free-typed dates are a
            // reliable source of ambiguous day/month entry.
            OutlinedTextField(
                value = state.dateOfBirthEpochDay?.let(::formatEpochDay) ?: "",
                onValueChange = {},
                label = { Text(text = "Date of birth (optional)") },
                readOnly = true,
                enabled = false,
                isError = state.dateOfBirthError != null,
                supportingText = state.dateOfBirthError?.let { { Text(text = it) } },
                trailingIcon = {
                    TextButton(onClick = { onEvent(PatientEditEvent.DatePickerRequested) }) {
                        Text(text = if (state.dateOfBirthEpochDay == null) "Set" else "Change")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            NullableEnumDropdown(
                label = "Gender (optional)",
                options = Gender.entries,
                selected = state.gender,
                optionLabel = { it.label },
                onSelected = { onEvent(PatientEditEvent.GenderChanged(it)) },
                enabled = !state.isSubmitting,
            )

            NullableEnumDropdown(
                label = "Blood group (optional)",
                options = BloodGroup.entries,
                selected = state.bloodGroup,
                optionLabel = { it.label },
                onSelected = { onEvent(PatientEditEvent.BloodGroupChanged(it)) },
                enabled = !state.isSubmitting,
            )

            LabelledTextField(
                value = state.knownAllergies,
                onValueChange = { onEvent(PatientEditEvent.AllergiesChanged(it)) },
                label = "Known allergies (optional)",
                errorMessage = state.allergiesError,
                enabled = !state.isSubmitting,
                singleLine = false,
            )

            Text(
                text = "A profile photo can be added once file uploads arrive in Phase 5.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = { onEvent(PatientEditEvent.Submit) },
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
                    Text(text = if (state.isEditing) "Save changes" else "Add patient")
                }
            }
        }
    }
}

@Composable
private fun LabelledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorMessage: String?,
    enabled: Boolean,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = errorMessage != null,
        enabled = enabled,
        supportingText = errorMessage?.let { { Text(text = it) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> NullableEnumDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelected: (T?) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // An explicit way back to "not recorded", since these fields are
            // optional and a mis-tap would otherwise be permanent.
            DropdownMenuItem(
                text = { Text(text = "Not recorded") },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
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
        DatePicker(state = pickerState)
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
