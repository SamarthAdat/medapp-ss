package com.ss.medrecord.ui.feature.settings

import android.net.Uri
import com.ss.medrecord.core.ui.UiEffect
import com.ss.medrecord.core.ui.UiEvent
import com.ss.medrecord.core.ui.UiState
import com.ss.medrecord.domain.model.AuditLog
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.domain.sync.SyncStatusUi

/**
 * Settings and the compliance surface (spec sections 5.12 and 9).
 *
 * Everything a data subject is entitled to exercise lives here: see the consent
 * they gave, see who touched their records, take a copy of everything, and ask
 * for it to be erased. Scattering those across the app would make them
 * technically present and practically unfindable, which is the usual way these
 * rights are honoured on paper only.
 */
data class SettingsUiState(
    val name: String = "",
    val email: String = "",
    val consentVersion: Int? = null,
    val syncStatus: SyncStatusUi = SyncStatusUi(),
    val consentHistory: List<ConsentRecord> = emptyList(),
    val accessLog: List<AuditLog> = emptyList(),
    val isSigningOut: Boolean = false,
    val isExporting: Boolean = false,
    val expandedSection: SettingsSection? = null,
    val pendingDialog: SettingsDialog? = null,
    val notificationsEnabled: Boolean = true,
    val exactAlarmsAllowed: Boolean = true,
) : UiState {
    val hasConflicts: Boolean get() = syncStatus.hasConflicts

    val conflictCount: Int get() = syncStatus.conflictCount

    /** The reminder engine is only fully working when both are granted. */
    val remindersDegraded: Boolean get() = !notificationsEnabled || !exactAlarmsAllowed
}

/** The expandable sections; only one is open at a time. */
enum class SettingsSection { CONSENT_HISTORY, ACCESS_LOG }

sealed interface SettingsDialog {
    data object SignOut : SettingsDialog
    data object DeleteAccount : SettingsDialog
}

sealed interface SettingsEvent : UiEvent {
    data object SignOutClicked : SettingsEvent
    data object SignOutConfirmed : SettingsEvent
    data object DeleteAccountClicked : SettingsEvent
    data object DeleteAccountConfirmed : SettingsEvent
    data object DialogDismissed : SettingsEvent
    data class SectionToggled(val section: SettingsSection) : SettingsEvent
    data object ExportClicked : SettingsEvent
    data object ResolveConflictsClicked : SettingsEvent
    data object NotificationSettingsClicked : SettingsEvent
    data object SyncNowClicked : SettingsEvent
    /** Re-read on resume; both are changed outside the app. */
    data object PermissionsRechecked : SettingsEvent
    data object BackClicked : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data object NavigateBack : SettingsEffect
    data object NavigateToConflicts : SettingsEffect
    data object OpenNotificationSettings : SettingsEffect
    data class ShareExport(val uri: Uri, val fileName: String) : SettingsEffect
    data class ShowMessage(val message: String) : SettingsEffect
}
