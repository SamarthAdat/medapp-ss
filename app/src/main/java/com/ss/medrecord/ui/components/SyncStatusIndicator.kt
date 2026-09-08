package com.ss.medrecord.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.sync.SyncStatusUi
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography

/**
 * The global sync indicator from spec section 6.6.
 *
 * It reports state rather than demanding action: being offline with unsynced
 * records is the app working as designed, not an error, so the wording says
 * what will happen rather than warning about what has not.
 *
 * The colour follows the palette's meanings exactly - jade for settled, amber
 * for work still queued, coral only when the user has a decision to make.
 * Offline is grey rather than amber: nothing is wrong and nothing is pending
 * on the user.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatusUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    val (dotColor, label) = when {
        status.hasConflicts -> colors.coral to
            "${status.conflictCount} record${plural(status.conflictCount)} need review"

        status.isSyncing -> colors.amber to "Syncing..."

        !status.isOnline && status.hasPendingWork ->
            colors.textTertiary to
                "Offline - ${status.pendingCount} change${plural(status.pendingCount)} saved on this device"

        !status.isOnline -> colors.textTertiary to "Offline"

        status.hasPendingWork ->
            colors.amber to "${status.pendingCount} change${plural(status.pendingCount)} to sync"

        else -> colors.jade to "All changes synced"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (status.isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = dotColor,
            )
        } else {
            PulseDot(color = dotColor, pulsing = !status.hasPendingWork && status.isOnline)
        }
        Text(
            text = label,
            style = MedTypography.monoCaption,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * A dot with a slow halo behind it. The halo runs only when everything is
 * settled - an animation that never stops reads as "working on it", which is
 * the opposite of what a synced state should say.
 */
@Composable
fun PulseDot(
    color: Color,
    pulsing: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(10.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "syncPulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "syncPulseProgress",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(1f + progress * 0.35f)
                    .alpha(0.35f + progress * 0.55f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

private fun plural(count: Int): String = if (count == 1) "" else "s"

@Preview(showBackground = true)
@Composable
private fun SyncStatusIndicatorPreview() {
    MedRecordTheme {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
            SyncStatusIndicator(status = SyncStatusUi(isOnline = true))
            SyncStatusIndicator(status = SyncStatusUi(isOnline = true, pendingCount = 3))
            SyncStatusIndicator(status = SyncStatusUi(isOnline = false, pendingCount = 1))
            SyncStatusIndicator(status = SyncStatusUi(isOnline = true, isSyncing = true))
            SyncStatusIndicator(status = SyncStatusUi(isOnline = true, conflictCount = 2))
        }
    }
}
