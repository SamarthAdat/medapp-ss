package com.ss.medrecord.ui.feature.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.domain.model.AppearanceMode
import com.ss.medrecord.domain.model.AuditLog
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.ui.components.AvatarInitials
import com.ss.medrecord.ui.components.MedCard
import com.ss.medrecord.ui.components.MedIconGlyph
import com.ss.medrecord.ui.components.MedOutlineButton
import com.ss.medrecord.ui.components.MedScreen
import com.ss.medrecord.ui.components.MedTonalButton
import com.ss.medrecord.ui.components.MedTopBar
import com.ss.medrecord.ui.components.SectionLabel
import com.ss.medrecord.ui.components.StatusPill
import com.ss.medrecord.ui.feature.consent.ConsentTexts
import com.ss.medrecord.ui.theme.MedIcon
import com.ss.medrecord.ui.theme.MedIcons
import com.ss.medrecord.ui.theme.MedRecordTheme
import com.ss.medrecord.ui.theme.MedTheme
import com.ss.medrecord.ui.theme.MedTypography
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

/**
 * Settings and the compliance surface (spec sections 5.12 and 9).
 *
 * Ordered by who the section is for. Anything demanding a decision comes
 * first, then the app's own behaviour, then the rights a data subject can
 * exercise, then the account itself - with the two irreversible actions last,
 * where they cannot be hit on the way to something else.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = MedTheme.colors

    state.pendingDialog?.let { dialog ->
        ConfirmDialog(dialog = dialog, onEvent = onEvent)
    }

    MedScreen(
        modifier = modifier,
        topBar = {
            MedTopBar(
                title = "Settings",
                onBack = { onEvent(SettingsEvent.BackClicked) },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
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

                item { SectionLabel("Appearance", modifier = Modifier.padding(top = 12.dp)) }

                item { AppearanceCard(state = state, onEvent = onEvent) }

                item { SectionLabel("Privacy on this device", modifier = Modifier.padding(top = 12.dp)) }

                item { AppLockCard(state = state, onEvent = onEvent) }

                item { SectionLabel("Your data", modifier = Modifier.padding(top = 12.dp)) }

                item {
                    SettingsRow(
                        icon = MedIcons.Download,
                        accent = colors.azure,
                        title = "Export my records",
                        subtitle = "A JSON copy of everything except report files",
                        isBusy = state.isExporting,
                        onClick = { onEvent(SettingsEvent.ExportClicked) },
                    )
                }

                item {
                    SettingsRow(
                        icon = MedIcons.VerifiedUser,
                        accent = colors.jade,
                        title = "Consent history",
                        subtitle = "${state.consentHistory.size} record" +
                            plural(state.consentHistory.size) + " · append-only",
                        expanded = state.expandedSection == SettingsSection.CONSENT_HISTORY,
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
                    SettingsRow(
                        icon = MedIcons.ReceiptLong,
                        accent = colors.violet,
                        title = "Access log",
                        subtitle = "Who touched your records, and when",
                        expanded = state.expandedSection == SettingsSection.ACCESS_LOG,
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

                item { SectionLabel("Account", modifier = Modifier.padding(top = 12.dp)) }

                item {
                    MedOutlineButton(
                        text = "Sign out",
                        onClick = { onEvent(SettingsEvent.SignOutClicked) },
                        enabled = !state.isSigningOut,
                        icon = MedIcons.Logout,
                    )
                }

                item {
                    Text(
                        text = "Request account deletion",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.coral,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(SettingsEvent.DeleteAccountClicked) }
                            .padding(vertical = 14.dp),
                    )
                }

                item { PrivacyFooter() }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AccountCard(state: SettingsUiState) {
    val colors = MedTheme.colors
    MedCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarInitials(
                name = state.name.ifBlank { "?" },
                seed = state.email,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.name.ifBlank { "Signed in" },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            state.consentVersion?.let { version ->
                StatusPill(text = "Consent v$version", accent = colors.jade)
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
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.coral,
        containerColor = colors.coral.copy(alpha = if (colors.isDark) 0.12f else 0.08f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedIconGlyph(
                icon = MedIcons.CallSplit,
                size = 20.dp,
                tint = colors.coral,
                contentDescription = null,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (count == 1) {
                    "1 record needs your decision"
                } else {
                    "$count records need your decision"
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.coral,
            )
        }
        Text(
            text = "These were changed here and on another device. They have " +
                "stopped syncing until you choose which copy to keep.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        MedTonalButton(
            text = "Review both copies",
            onClick = { onEvent(SettingsEvent.ResolveConflictsClicked) },
            container = colors.coral,
            onContainer = colors.onCoral,
        )
    }
}

@Composable
private fun SyncCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = MedTheme.colors
    MedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sync",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            if (state.syncStatus.hasPendingWork) {
                StatusPill(
                    text = "${state.syncStatus.pendingCount} pending",
                    accent = colors.amber,
                )
            }
        }
        Text(
            text = when {
                !state.syncStatus.isOnline -> "Offline. Changes are saved here."
                state.syncStatus.isSyncing -> "Syncing now."
                state.syncStatus.hasPendingWork ->
                    "${state.syncStatus.pendingCount} change" +
                        plural(state.syncStatus.pendingCount) + " waiting to upload."

                else -> "Everything is up to date."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(14.dp))
        MedTonalButton(
            text = "Sync now",
            onClick = { onEvent(SettingsEvent.SyncNowClicked) },
            icon = MedIcons.Sync,
        )
    }
}

@Composable
private fun RemindersCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier.fillMaxWidth(),
        accent = colors.amber,
        containerColor = colors.amber.copy(alpha = if (colors.isDark) 0.10f else 0.08f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedIconGlyph(
                icon = MedIcons.Alarm,
                size = 20.dp,
                tint = colors.amber,
                contentDescription = null,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (!state.notificationsEnabled) {
                    "Reminders are off"
                } else {
                    "Reminders may arrive late"
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
        }
        Text(
            text = if (!state.notificationsEnabled) {
                "Doses are recorded but this device will not notify you."
            } else {
                "Android may delay a dose reminder while the device is idle."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Open notification settings",
            style = MaterialTheme.typography.titleSmall,
            color = colors.amber,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { onEvent(SettingsEvent.NotificationSettingsClicked) },
        )
    }
}

/**
 * Light, dark, or whatever the phone is doing.
 *
 * Three segments rather than a single switch, because "off" is not the
 * opposite of "follow the system" - a two-state control cannot express the
 * default at all, and the default is the one most people should stay on.
 */
@Composable
private fun AppearanceCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = MedTheme.colors
    MedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        Text(
            text = "Dark is easier at night; light is easier to read a scan on.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppearanceOption(
                label = "System",
                icon = MedIcons.Contrast,
                selected = state.appearanceMode == AppearanceMode.FOLLOW_SYSTEM,
                onClick = {
                    onEvent(SettingsEvent.AppearanceModeChanged(AppearanceMode.FOLLOW_SYSTEM))
                },
                modifier = Modifier.weight(1f),
            )
            AppearanceOption(
                label = "Light",
                icon = MedIcons.LightMode,
                selected = state.appearanceMode == AppearanceMode.LIGHT,
                onClick = { onEvent(SettingsEvent.AppearanceModeChanged(AppearanceMode.LIGHT)) },
                modifier = Modifier.weight(1f),
            )
            AppearanceOption(
                label = "Dark",
                icon = MedIcons.DarkMode,
                selected = state.appearanceMode == AppearanceMode.DARK,
                onClick = { onEvent(SettingsEvent.AppearanceModeChanged(AppearanceMode.DARK)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AppearanceOption(
    label: String,
    icon: MedIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MedTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) colors.jade.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) colors.jade.copy(alpha = 0.40f) else colors.hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MedIconGlyph(
            icon = icon,
            size = 20.dp,
            tint = if (selected) colors.jade else colors.textSecondary,
            contentDescription = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

/**
 * The optional fingerprint lock.
 *
 * The description says exactly what it does and no more. It is a screen lock,
 * not a second key - the database is already sealed with a hardware-backed
 * Keystore key whether this is on or off - and a user who believes otherwise
 * would make worse decisions about where they leave the phone.
 */
@Composable
private fun AppLockCard(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    val colors = MedTheme.colors
    MedCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MedIconGlyph(
                icon = MedIcons.Fingerprint,
                size = 22.dp,
                tint = if (state.canUseBiometricLock) colors.jade else colors.textTertiary,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Unlock with biometrics",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                )
                Text(
                    text = state.biometricUnavailableReason
                        ?: "Ask for your fingerprint or screen lock each time the " +
                        "app is opened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.biometricLockEnabled && state.canUseBiometricLock,
                onCheckedChange = { onEvent(SettingsEvent.BiometricLockToggled(it)) },
                enabled = state.canUseBiometricLock,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onJade,
                    checkedTrackColor = colors.jade,
                    uncheckedTrackColor = colors.cardHighest,
                    uncheckedBorderColor = colors.hairlineStrong,
                ),
            )
        }
        if (state.biometricLockEnabled && state.canUseBiometricLock) {
            Text(
                text = "This hides the app behind your fingerprint. Your records are " +
                    "encrypted on this device either way.",
                style = MedTypography.monoCaption,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** One tappable row: tinted icon, title, supporting line, and a state marker. */
@Composable
private fun SettingsRow(
    icon: MedIcon,
    accent: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBusy: Boolean = false,
    expanded: Boolean? = null,
) {
    val colors = MedTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isBusy, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedIconGlyph(icon = icon, size = 20.dp, tint = accent, contentDescription = null)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        when {
            isBusy -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = accent,
            )

            expanded != null -> MedIconGlyph(
                icon = if (expanded) MedIcons.ExpandLess else MedIcons.ExpandMore,
                size = 20.dp,
                tint = colors.textTertiary,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )

            else -> MedIconGlyph(
                icon = MedIcons.ChevronRight,
                size = 20.dp,
                tint = colors.textTertiary,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ConsentRow(consent: ConsentRecord) {
    val colors = MedTheme.colors
    Column(modifier = Modifier.padding(start = 34.dp, bottom = 10.dp)) {
        Text(
            text = consent.consentType.name.replace('_', ' ').lowercase()
                .replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
        )
        Text(
            text = "Version ${consent.version} · ${formatInstant(consent.acceptedAt)}",
            style = MedTypography.monoCaption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun AccessLogRow(entry: AuditLog) {
    val colors = MedTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 34.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "${entry.action.name.lowercase().replaceFirstChar { it.uppercase() }} " +
                entry.entityType.name.lowercase(),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        Text(
            text = formatClock(entry.timestamp),
            style = MedTypography.monoCaption,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun PrivacyFooter() {
    val colors = MedTheme.colors
    MedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        containerColor = colors.cardHighest,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            MedIconGlyph(
                icon = MedIcons.Shield,
                size = 20.dp,
                tint = colors.jade,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Records are encrypted on this device with a hardware-backed " +
                        "key. Deleted records are recoverable for " +
                        "${AppConstants.SOFT_DELETE_GRACE_PERIOD_DAYS} days, then erased " +
                        "automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    text = "Privacy questions and grievances: " +
                        ConsentTexts.GRIEVANCE_CONTACT,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(dialog: SettingsDialog, onEvent: (SettingsEvent) -> Unit) {
    val colors = MedTheme.colors
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
        containerColor = colors.card,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        shape = RoundedCornerShape(24.dp),
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = { onEvent(confirmEvent) }) {
                Text(text = confirmLabel, color = colors.coral)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DialogDismissed) }) {
                Text(text = "Cancel", color = colors.textSecondary)
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

private fun plural(count: Int): String = if (count == 1) "" else "s"

private val TIMESTAMP_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatInstant(millis: Long): String =
    TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun formatClock(millis: Long): String =
    CLOCK_FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

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
