package com.ss.medrecord.ui.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ss.medrecord.BuildConfig
import com.ss.medrecord.core.ui.components.EmptyState

/** Placeholder. Phase 9 builds settings, data export and erasure flows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(text = "Settings") }) },
    ) { innerPadding ->
        EmptyState(
            title = "Settings",
            description = "Version ${BuildConfig.VERSION_NAME}. Preferences and compliance " +
                "tools arrive in Phase 9.",
            modifier = Modifier.padding(innerPadding),
        )
    }
}
