package com.ss.medrecord.ui.feature.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.theme.MedRecordTheme

/**
 * Shown while the session is still resolving - reading the cached Firebase
 * credential, opening the encrypted database and checking the consent version.
 *
 * It has no navigation of its own. The navigation host moves off this screen as
 * soon as the session settles into a real state, so there is exactly one place
 * that decides where a user lands.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "MedRecord Keeper",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    MedRecordTheme {
        SplashScreen()
    }
}
