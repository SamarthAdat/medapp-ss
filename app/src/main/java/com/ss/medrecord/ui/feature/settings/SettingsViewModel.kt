package com.ss.medrecord.ui.feature.settings

import androidx.lifecycle.viewModelScope
import com.ss.medrecord.core.biometric.BiometricGate
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.core.ui.BaseViewModel
import com.ss.medrecord.core.ui.toUserMessage
import com.ss.medrecord.data.export.DataExporter
import com.ss.medrecord.data.reminder.AlarmScheduler
import com.ss.medrecord.data.reminder.ReminderNotifier
import com.ss.medrecord.domain.audit.AuditLogger
import com.ss.medrecord.domain.model.AuditAction
import com.ss.medrecord.domain.model.AuditEntityType
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.repository.AuthRepository
import com.ss.medrecord.domain.repository.ConsentRepository
import com.ss.medrecord.domain.session.AppearanceManager
import com.ss.medrecord.domain.session.SessionManager
import com.ss.medrecord.domain.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataExporter: DataExporter,
    private val auditLogger: AuditLogger,
    private val notifier: ReminderNotifier,
    private val alarmScheduler: AlarmScheduler,
    private val syncManager: SyncManager,
    private val sessionManager: SessionManager,
    private val appearanceManager: AppearanceManager,
    private val biometricGate: BiometricGate,
    consentRepository: ConsentRepository,
) : BaseViewModel<SettingsUiState, SettingsEvent, SettingsEffect>(SettingsUiState()) {

    init {
        authRepository.observeCurrentUser()
            .onEach { user ->
                setState {
                    copy(
                        name = user?.name.orEmpty(),
                        email = user?.email.orEmpty(),
                        consentVersion = user?.consentVersion,
                    )
                }
            }
            .launchIn(viewModelScope)

        syncManager.status
            .onEach { status -> setState { copy(syncStatus = status) } }
            .launchIn(viewModelScope)

        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated ->
                        consentRepository.observeConsentHistory(session.userId)

                    else -> flowOf(emptyList())
                }
            }
            .onEach { history -> setState { copy(consentHistory = history) } }
            .launchIn(viewModelScope)

        sessionManager.session
            .flatMapLatest { session ->
                when (session) {
                    is AuthSession.Authenticated -> auditLogger.observeRecent(session.userId)
                    else -> flowOf(emptyList())
                }
            }
            .onEach { entries -> setState { copy(accessLog = entries) } }
            .launchIn(viewModelScope)

        appearanceManager.appearanceMode
            .onEach { mode -> setState { copy(appearanceMode = mode) } }
            .launchIn(viewModelScope)

        appearanceManager.biometricLockEnabled
            .onEach { enabled -> setState { copy(biometricLockEnabled = enabled) } }
            .launchIn(viewModelScope)

        refreshPermissionState()
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.BackClicked -> sendEffect(SettingsEffect.NavigateBack)

            SettingsEvent.SignOutClicked ->
                setState { copy(pendingDialog = SettingsDialog.SignOut) }

            SettingsEvent.SignOutConfirmed -> signOut()

            SettingsEvent.DeleteAccountClicked ->
                setState { copy(pendingDialog = SettingsDialog.DeleteAccount) }

            SettingsEvent.DeleteAccountConfirmed -> requestAccountDeletion()

            SettingsEvent.DialogDismissed -> setState { copy(pendingDialog = null) }

            is SettingsEvent.SectionToggled -> setState {
                // One open at a time: both lists are long, and two expanded at
                // once turns the screen into a scroll with no landmarks.
                copy(
                    expandedSection = if (expandedSection == event.section) {
                        null
                    } else {
                        event.section
                    },
                )
            }

            SettingsEvent.ExportClicked -> export()

            SettingsEvent.ResolveConflictsClicked ->
                sendEffect(SettingsEffect.NavigateToConflicts)

            SettingsEvent.NotificationSettingsClicked ->
                sendEffect(SettingsEffect.OpenNotificationSettings)

            SettingsEvent.SyncNowClicked -> {
                if (currentState.syncStatus.isOnline) {
                    syncManager.syncNow(expedited = true)
                    sendEffect(SettingsEffect.ShowMessage("Syncing..."))
                } else {
                    sendEffect(SettingsEffect.ShowMessage("You are offline."))
                }
            }

            SettingsEvent.PermissionsRechecked -> refreshPermissionState()

            is SettingsEvent.AppearanceModeChanged ->
                appearanceManager.setAppearanceMode(event.mode)

            is SettingsEvent.BiometricLockToggled -> {
                // Refuses to store an intent the device cannot honour, rather
                // than accepting it and silently never locking.
                if (event.enabled && !currentState.canUseBiometricLock) {
                    sendEffect(
                        SettingsEffect.ShowMessage(
                            currentState.biometricUnavailableReason
                                ?: "This phone cannot use a fingerprint lock.",
                        ),
                    )
                } else {
                    appearanceManager.setBiometricLockEnabled(event.enabled)
                }
            }
        }
    }

    private fun refreshPermissionState() {
        setState {
            copy(
                notificationsEnabled = notifier.canPost(),
                exactAlarmsAllowed = alarmScheduler.canScheduleExact(),
                // Re-read alongside the permissions: an enrolment can be added
                // or wiped in the same trip to system settings.
                biometricAvailability = biometricGate.availability(),
            )
        }
    }

    /**
     * Exporting is logged as an EXPORT under spec 4.9. A copy of every record
     * leaving the app is exactly the kind of event an access log exists to
     * record, and it would be a strange trail that noted every view but not the
     * moment the whole history was handed to another app.
     */
    private fun export() {
        if (currentState.isExporting) return
        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        if (userId == null) {
            sendEffect(SettingsEffect.ShowMessage("Sign in to export your records."))
            return
        }

        viewModelScope.launch {
            setState { copy(isExporting = true) }

            runCatching { dataExporter.export(userId) }
                .onSuccess { result ->
                    auditLogger.log(
                        action = AuditAction.EXPORT,
                        entityType = AuditEntityType.USER,
                        entityId = userId,
                    )
                    setState { copy(isExporting = false) }
                    sendEffect(SettingsEffect.ShareExport(result.uri, result.fileName))
                }
                .onFailure {
                    setState { copy(isExporting = false) }
                    sendEffect(SettingsEffect.ShowMessage("Could not build the export."))
                }
        }
    }

    private fun signOut() {
        if (currentState.isSigningOut) return
        setState { copy(pendingDialog = null, isSigningOut = true) }
        viewModelScope.launch {
            // No navigation effect: signing out changes the session, and the
            // navigation host relocates the user to the auth graph from there.
            authRepository.signOut()
        }
    }

    /**
     * Records the erasure request and signs out.
     *
     * Deliberately not a delete. A client cannot erase the Firestore copy, the
     * Cloud Storage objects or the audit trail - doing so needs credentials no
     * device should hold - so pretending otherwise would be the worst outcome
     * available: telling someone their data is gone when it is not. What this
     * does is honest and is what the screen says.
     */
    private fun requestAccountDeletion() {
        val userId = (sessionManager.session.value as? AuthSession.Authenticated)?.userId
        setState { copy(pendingDialog = null) }
        if (userId == null) return

        viewModelScope.launch {
            auditLogger.log(
                action = AuditAction.DELETE,
                entityType = AuditEntityType.USER,
                entityId = userId,
            )
            // Pushed before signing out, so the request survives this device.
            syncManager.syncNow(expedited = true)
            sendEffect(
                SettingsEffect.ShowMessage(
                    "Deletion requested. Records on this device are removed at sign-out.",
                ),
            )
            authRepository.signOut()
        }
    }
}
