package com.ss.medrecord.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ss.medrecord.ui.components.IconTile
import com.ss.medrecord.ui.components.MedEmptyState
import com.ss.medrecord.ui.components.MedTonalButton
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme

/**
 * The three states every list screen can be in before it has rows.
 *
 * They live here rather than in ui/components because the ViewModel contracts
 * in core reference the same three cases, and because a screen should not be
 * able to invent a fourth: an empty list, a failed read and a pending read
 * look different for a reason, and getting them confused is how "no records"
 * ends up on screen when the truth is "could not read the database".
 */

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MedTheme.colors.jade,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = MedTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconTile(icon = MedIcons.PriorityHigh, accent = colors.coral, size = 56.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            MedTonalButton(
                text = "Retry",
                onClick = onRetry,
                icon = MedIcons.Refresh,
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MedEmptyState(title = title, message = description)
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MedRecordTheme {
        EmptyState(
            title = "No patients yet",
            description = "Add a patient profile to get started.",
        )
    }
}
