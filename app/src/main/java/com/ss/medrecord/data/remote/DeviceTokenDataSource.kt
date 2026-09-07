package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.core.device.DeviceIdProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{userId}/devices/{deviceIdHash}
 *
 * The FCM registration token, stored in its own document rather than as a field
 * on the user. Two reasons: the user document is written whole on every profile
 * edit, so a token field there would be clobbered by an unrelated save; and one
 * account can be signed in on several devices, which is a collection, not a
 * field.
 *
 * The document id is the same non-identifying install hash the audit trail
 * uses - never the Android ID - so re-registering a token on the same device
 * replaces the row instead of accumulating one per app launch.
 *
 * Nothing clinical goes in here. A push token identifies a device to Google's
 * servers; pairing it with health data in the same document would make the
 * pairing itself the disclosure.
 */
@Singleton
class DeviceTokenDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val deviceIdProvider: DeviceIdProvider,
) {

    suspend fun register(userId: String, token: String) {
        firestore.collection(UserRemoteDataSource.USERS)
            .document(userId)
            .collection(DEVICES)
            .document(deviceIdProvider.deviceIdHash)
            .set(
                mapOf(
                    FIELD_USER_ID to userId,
                    FIELD_TOKEN to token,
                    FIELD_PLATFORM to PLATFORM_ANDROID,
                    FIELD_UPDATED_AT to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    /**
     * Sign-out drops the token so the device stops being a delivery target for
     * an account that is no longer on it.
     */
    suspend fun unregister(userId: String) {
        firestore.collection(UserRemoteDataSource.USERS)
            .document(userId)
            .collection(DEVICES)
            .document(deviceIdProvider.deviceIdHash)
            .delete()
            .await()
    }

    companion object {
        const val DEVICES = "devices"

        private const val PLATFORM_ANDROID = "android"

        private const val FIELD_USER_ID = "userId"
        private const val FIELD_TOKEN = "token"
        private const val FIELD_PLATFORM = "platform"
        private const val FIELD_UPDATED_AT = "updatedAt"
    }
}
