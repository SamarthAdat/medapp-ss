package com.ss.medrecord.ui.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

/**
 * Shown while the session is still resolving - reading the cached Firebase
 * credential, opening the encrypted database and checking the consent version.
 *
 * It has no navigation of its own. The navigation host moves off this screen as
 * soon as the session settles into a real state, so there is exactly one place
 * that decides where a user lands.
 *
 * The mark is the same one the sign-in screen opens with, in the same place, so
 * the hand-off from the system splash to the app is one continuous image
 * rather than three different first impressions.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val colors = MedTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.jade),
                contentAlignment = Alignment.Center,
            ) {
                MedIconGlyph(
                    icon = MedIcons.HealthAndSafety,
                    size = 34.dp,
                    tint = colors.onJade,
                    contentDescription = null,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "MedRecord Keeper",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
            )

            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
                color = colors.jade,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    MedRecordTheme {
        SplashScreen()
    }
}
