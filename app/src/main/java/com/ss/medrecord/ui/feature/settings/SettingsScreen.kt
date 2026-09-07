package com.ss.medrecord.ui.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.domain.model.AuditLog
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.ui.feature.consent.ConsentTexts
import com.ss.medrecord.ui.theme.MedRecordTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToConflicts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(SettingsEvent.PermissionsRechecked)
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SettingsEffect.NavigateBack -> onNavigateBack()
                SettingsEffect.NavigateToConflicts -> onNavigateToConflicts()
                SettingsEffect.OpenNotificationSettings ->
                    context.openAppNotificationSettings()

                is SettingsEffect.ShowMessage -> snackbarHostState.showSnackbar(effect.message)

                is SettingsEffect.ShareExport -> {
                    if (!context.shareExport(effect.uri)) {
                        snackbarHostState.showSnackbar(
                            "No app on this device can receive the file.",
                        )
                    }
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    state.pendingDialog?.let { dialog ->
        ConfirmDialog(dialog = dialog, onEvent = onEvent)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    TextButton(onClick = { onEvent(SettingsEvent.BackClicked) }) {
                        Text(text = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AccountCard(state = state) }

            if (state.hasConflicts) {
                item { ConflictsCard(count = state.conflictCount, onEvent = onEvent) }
            }

            item { SyncCard(state = state, onEvent = onEvent) }

            if (state.remindersDegraded) {
                item { RemindersCard(state = state, onEvent = onEvent) }
            }

            item { SectionHeader(text = "Your data") }

            item {
                ActionRow(
                    title = "Export my records",
                    subtitle = "A JSON copy of everything except report files.",
                    isBusy = state.isExporting,
                    onClick = { onEvent(SettingsEvent.ExportClicked) },
                )
            }

            item {
                ExpandableRow(
                    title = "Consent history",
                    subtitle = "${state.consentHistory.size} record(s)",
                    isExpanded = state.expandedSection == SettingsSection.CONSENT_HISTORY,
                    onClick = {
                        onEvent(SettingsEvent.SectionToggled(SettingsSection.CONSENT_HISTORY))
                    },
                )
            }

            if (state.expandedSection == SettingsSection.CONSENT_HISTORY) {
                items(state.consentHistory, key = { it.consentId }) { consent ->
                    ConsentRow(consent = consent)
                }
            }

            item {
                ExpandableRow(
                    title = "Access log",
                    subtitle = "Who touched your records, and when.",
                    isExpanded = state.expandedSection == SettingsSection.ACCESS_LOG,
                    onClick = {
                        onEvent(SettingsEvent.SectionToggled(SettingsSection.ACCESS_LOG))
                    },
                )
            }

            if (state.expandedSection == SettingsSection.ACCESS_LOG) {
                items(state.accessLog, key = { it.logId }) { entry ->
                    AccessLogRow(entry = entry)
                }
            }

            item { SectionHeader(text = "Account") }

            item {
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.SignOutClicked) },
                    enabled = !state.isSigningOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Sign out")
                }
            }

            item {
                TextButton(
                    onClick = { onEvent(SettingsEvent.DeleteAccountClicked) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Request account deletion",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { PrivacyFooter() }
        }
    }
}

@Composable
private fun AccountCard(state: SettingsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.name.ifBlank { "Signed in" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = state.email, style = MaterialTheme.typography.bodyMedium)
            state.consentVersion?.let { version ->
                Text(
                    text = "Consent version $version accepted",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Conflicts get their own card above everything else, and only when there are
 * any. A parked record is not syncing at all and never will until someone
 * chooses - the one state in this app that silently stops working and cannot
 * fix itself.
 */
@Composable
private fun ConflictsCard(count: Int, onEvent: (SettingsEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (count == 1) "1 record needs your decision" else
                    "$count records need your decision",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "These were changed here and on another device. They have " +
                    "stopped syncing until you choose which copy to keep.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { onEvent(SettingsEvent.ResolveConflictsClicked) }) {
                Text(text = "Review")
            }
        }
    }
}

@Composable
private fun SyncCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Sync", style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    !state.syncStatus.isOnline -> "Offline. Changes are saved here."
                    state.syncStatus.isSyncing -> "Syncing now."
                    state.syncStatus.hasPendingWork ->
                        "${state.syncStatus.pendingCount} change(s) waiting to upload."

                    else -> "Everything is up to date."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onEvent(SettingsEvent.SyncNowClicked) }) {
                Text(text = "Sync now")
            }
        }
    }
}

@Composable
private fun RemindersCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (!state.notificationsEnabled) {
                    "Reminders are off"
                } else {
                    "Reminders may arrive late"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = if (!state.notificationsEnabled) {
                    "Doses are recorded but this device will not notify you."
                } else {
                    "Exact alarms are not permitted, so a dose reminder may be delayed."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = { onEvent(SettingsEvent.NotificationSettingsClicked) }) {
                Text(text = "Open notification settings")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isBusy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExpandableRow(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = if (isExpanded) "Hide" else "Show", color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ConsentRow(consent: ConsentRecord) {
    Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
        Text(
            text = consent.consentType.name.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "Version ${consent.version} · ${formatInstant(consent.acceptedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun AccessLogRow(entry: AuditLog) {
    Column(modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
        Text(
            text = "${entry.action.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                entry.entityType.name.lowercase(),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatInstant(entry.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PrivacyFooter() {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        HorizontalDivider()
        Text(
            text = "Records you delete are recoverable for " +
                "${AppConstants.SOFT_DELETE_GRACE_PERIOD_DAYS} days, then removed from " +
                "this device automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "Privacy questions and grievances: ${ConsentTexts.GRIEVANCE_CONTACT}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ConfirmDialog(dialog: SettingsDialog, onEvent: (SettingsEvent) -> Unit) {
    val (title, body, confirmLabel, confirmEvent) = when (dialog) {
        SettingsDialog.SignOut -> DialogSpec(
            title = "Sign out?",
            body = "Records on this device are removed. Anything not yet synced " +
                "stays only here, so sync first if you are unsure.",
            confirmLabel = "Sign out",
            confirmEvent = SettingsEvent.SignOutConfirmed,
        )

        SettingsDialog.DeleteAccount -> DialogSpec(
            title = "Request account deletion?",
            body = "This records your request and signs you out, removing every " +
                "record from this device. The cloud copy is erased by our team; " +
                "the request is not completed on this device.",
            confirmLabel = "Request deletion",
            confirmEvent = SettingsEvent.DeleteAccountConfirmed,
        )
    }

    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DialogDismissed) },
        title = { Text(text = title) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = { onEvent(confirmEvent) }) { Text(text = confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DialogDismissed) }) {
                Text(text = "Cancel")
            }
        },
    )
}

private data class DialogSpec(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val confirmEvent: SettingsEvent,
)

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun formatInstant(millis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/**
 * Opens where the user can turn notifications back on.
 *
 * ACTION_APP_NOTIFICATION_SETTINGS only exists from API 26; below that there is
 * no per-app notification screen, so the app's details page is the closest
 * thing. Without the branch the intent silently fails on API 24-25 and the
 * button does nothing - the worst kind of bug, because the user concludes the
 * app is broken rather than that their device is old.
 */
private fun Context.openAppNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$packageName".toUri())
    }
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Hands the export to another app.
 *
 * ACTION_SEND with a one-shot read grant, wrapped in a chooser: the user picks
 * where their whole medical history goes, and the grant dies with the target
 * activity rather than persisting to whatever was chosen.
 */
private fun Context.shareExport(uri: android.net.Uri): Boolean {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    return runCatching {
        startActivity(
            Intent.createChooser(intent, "Export records")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    MedRecordTheme {
        SettingsScreen(
            state = SettingsUiState(
                name = "Samarth",
                email = "owner@example.com",
                consentVersion = 1,
            ),
            onEvent = {},
        )
    }
}
