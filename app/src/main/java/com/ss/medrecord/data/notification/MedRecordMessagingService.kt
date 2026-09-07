package com.ss.medrecord.data.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ss.medrecord.data.remote.DeviceTokenDataSource
import com.ss.medrecord.data.remote.FirebaseAuthDataSource
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.reminder.ReminderScheduler
import com.ss.medrecord.domain.sync.SyncManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MedRecordMessaging"

/**
 * Firebase Cloud Messaging (spec section 7.4).
 *
 * Scope note, so this is not mistaken for more than it is: **no backend
 * currently sends messages to this app**. Medicine and appointment reminders are
 * local alarms and stay local - they have to work with no network at all, which
 * a push cannot promise. What this service exists for is the other direction:
 * registering this device so that server-initiated work has somewhere to land
 * when Phase 9 adds it (an account deletion that must reach every device, a
 * nudge to sync after a server-side change).
 *
 * Data messages are handled and notification messages are not. A `notification`
 * payload is rendered by the system before the app sees it, which would put
 * server-supplied text on a medical app's notification shade with no channel of
 * ours and no review - so anything meaningful must arrive as data and be acted
 * on here.
 */
@AndroidEntryPoint
class MedRecordMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var deviceTokenDataSource: DeviceTokenDataSource

    @Inject
    lateinit var authDataSource: FirebaseAuthDataSource

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    /**
     * Called on first launch and whenever Firebase rotates the token - after a
     * restore, a reinstall, or its own expiry. A token that is not re-registered
     * on rotation is a device that silently stops receiving anything.
     */
    override fun onNewToken(token: String) {
        val userId = authDataSource.currentUserId ?: return
        scope.launch {
            runCatching { deviceTokenDataSource.register(userId, token) }
                .onFailure { Log.w(TAG, "Could not register push token", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // A message for a signed-out device is not ours to act on: it was
        // addressed to a session that has ended.
        if (authDataSource.currentUserId == null) return

        when (message.data[KEY_ACTION]) {
            ACTION_SYNC -> syncManager.syncNow(expedited = true)
            ACTION_REBUILD_REMINDERS -> reminderScheduler.requestRebuild()
            else -> Log.d(TAG, "Ignoring message with no recognised action")
        }
    }

    private companion object {
        const val KEY_ACTION = "action"
        const val ACTION_SYNC = "sync"
        const val ACTION_REBUILD_REMINDERS = "rebuild_reminders"
    }
}
