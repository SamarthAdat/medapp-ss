package com.ss.medrecord.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.domain.sync.SyncStatusUi
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.OfflineGrey
import com.ss.medrecord.ui.theme.SyncPendingAmber
import com.ss.medrecord.ui.theme.SyncedGreen

/**
 * The global sync indicator from spec section 6.6.
 *
 * It reports state rather than demanding action: being offline with unsynced
 * records is the app working as designed, not an error, so the wording says
 * what will happen rather than warning about what has not.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatusUi,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val (dotColor, label) = when {
        status.hasConflicts -> MaterialTheme.colorScheme.error to
            "${status.conflictCount} record${plural(status.conflictCount)} need review"

        status.isSyncing -> SyncPendingAmber to "Syncing..."

        !status.isOnline && status.hasPendingWork ->
            OfflineGrey to "Offline - ${status.pendingCount} change${plural(status.pendingCount)} saved on this device"

        !status.isOnline -> OfflineGrey to "Offline"

        status.hasPendingWork ->
            SyncPendingAmber to "${status.pendingCount} change${plural(status.pendingCount)} to sync"

        else -> SyncedGreen to "All changes synced"
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
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
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
