package com.ss.medrecord.ui.feature.patient.list

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ss.medrecord.core.ui.components.EmptyState

/** Placeholder. Phase 2 builds the real multi-patient list and switcher. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = "Patients") }) },
    ) { innerPadding ->
        EmptyState(
            title = "No patients yet",
            description = "Patient profiles arrive in Phase 2.",
            modifier = Modifier.padding(innerPadding),
        )
    }
}
