package com.ss.medrecord.ui.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedOutlineButton
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedTheme

/**
 * What stands in front of the app when the lock is on.
 *
 * It shows nothing: no name, no patient, no counts. A lock screen that
 * previews the thing it is protecting is decoration, and this one is covering
 * a medical history that may not be the phone owner's own.
 */
@Composable
fun AppLockScreen(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    val colors = MedTheme.colors
    // Swallows every tap. Without this the lock is only a picture: the
    // navigation graph is still composed underneath it and would keep taking
    // touches through the "cover".
    val blocker = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas)
            .clickable(interactionSource = blocker, indication = null) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.jade.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                MedIconGlyph(
                    icon = MedIcons.Fingerprint,
                    size = 36.dp,
                    tint = colors.jade,
                    contentDescription = null,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "MedRecord is locked",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Unlock with your fingerprint or screen lock to see your records.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.coral,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(32.dp))
            MedOutlineButton(
                text = "Unlock",
                onClick = onUnlock,
                icon = MedIcons.Fingerprint,
            )
        }
    }
}
