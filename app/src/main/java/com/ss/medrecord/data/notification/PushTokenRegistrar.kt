package com.ss.medrecord.data.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.ss.medrecord.data.remote.DeviceTokenDataSource
import com.ss.medrecord.di.ApplicationScope
import com.ss.medrecord.domain.model.AuthSession
import com.ss.medrecord.domain.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PushTokenRegistrar"

/**
 * Registers this device's push token against whoever is signed in.
 *
 * [MedRecordMessagingService.onNewToken] only fires when Firebase mints or
 * rotates a token, which on a given install may be once ever - and almost
 * certainly before anyone has signed in. So the token also has to be attached
 * at the other end: when a session becomes usable. Without this half, the first
 * account on a device would never be reachable.
 *
 * Failure is silent and non-fatal by design. Nothing the user does depends on
 * this succeeding, Play Services may be absent entirely, and a sign-in that
 * fails because a push token could not be stored would be an absurd trade.
 *
 * Removal on sign-out lives in AuthRepositoryImpl instead, talking to the data
 * source directly: this class observes SessionManager, which is built on that
 * repository, so injecting it there would close a dependency cycle.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val sessionManager: SessionManager,
    private val deviceTokenDataSource: DeviceTokenDataSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    fun initialize() {
        sessionManager.session
            .filterIsInstance<AuthSession.Authenticated>()
            .map { it.userId }
            .distinctUntilChanged()
            .onEach { userId ->
                runCatching {
                    val token = FirebaseMessaging.getInstance().token.await()
                    deviceTokenDataSource.register(userId, token)
                }.onFailure { Log.d(TAG, "Push token not registered", it) }
            }
            .launchIn(scope)
    }
}
