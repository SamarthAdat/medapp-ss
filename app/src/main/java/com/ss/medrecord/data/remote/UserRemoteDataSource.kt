package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ss.medrecord.core.common.AppError
import com.ss.medrecord.domain.model.AppUser
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.domain.model.ConsentType
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore access for the account holder document and its consent subcollection.
 *
 * Layout, chosen so security rules can scope on the path alone (spec 9.1):
 *
 *   users/{userId}
 *   users/{userId}/consents/{consentId}
 *
 * Every document also carries its own userId field, giving rules a second,
 * independent check that does not depend on the document path being well formed.
 */
@Singleton
class UserRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    suspend fun getUser(userId: String): AppUser? {
        val snapshot = firestore.collection(USERS).document(userId).get().await()
        if (!snapshot.exists()) return null
        return AppUser(
            userId = userId,
            name = snapshot.getString(FIELD_NAME).orEmpty(),
            email = snapshot.getString(FIELD_EMAIL).orEmpty(),
            phone = snapshot.getString(FIELD_PHONE),
            createdAt = snapshot.getLong(FIELD_CREATED_AT) ?: 0L,
            updatedAt = snapshot.getLong(FIELD_UPDATED_AT) ?: 0L,
            consentAcceptedAt = snapshot.getLong(FIELD_CONSENT_ACCEPTED_AT),
            consentVersion = snapshot.getLong(FIELD_CONSENT_VERSION)?.toInt(),
        )
    }

    suspend fun upsertUser(user: AppUser) {
        firestore.collection(USERS)
            .document(user.userId)
            .set(user.toFirestoreMap())
            .await()
    }

    suspend fun insertConsents(consents: List<ConsentRecord>) {
        if (consents.isEmpty()) return
        val batch = firestore.batch()
        consents.forEach { consent ->
            val ref = firestore.collection(USERS)
                .document(consent.userId)
                .collection(CONSENTS)
                .document(consent.consentId)
            batch.set(ref, consent.toFirestoreMap())
        }
        batch.commit().await()
    }

    suspend fun getConsents(userId: String): List<ConsentRecord> =
        firestore.collection(USERS)
            .document(userId)
            .collection(CONSENTS)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val type = doc.getString(FIELD_CONSENT_TYPE)
                    ?.let { raw -> runCatching { ConsentType.valueOf(raw) }.getOrNull() }
                    ?: return@mapNotNull null
                ConsentRecord(
                    consentId = doc.id,
                    userId = userId,
                    consentType = type,
                    version = doc.getLong(FIELD_VERSION)?.toInt() ?: 0,
                    acceptedAt = doc.getLong(FIELD_ACCEPTED_AT) ?: 0L,
                    ipHash = doc.getString(FIELD_IP_HASH),
                )
            }

    private fun AppUser.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_EMAIL to email,
        FIELD_PHONE to phone,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_CONSENT_ACCEPTED_AT to consentAcceptedAt,
        FIELD_CONSENT_VERSION to consentVersion,
    )

    private fun ConsentRecord.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_CONSENT_ID to consentId,
        FIELD_USER_ID to userId,
        FIELD_CONSENT_TYPE to consentType.name,
        FIELD_VERSION to version,
        FIELD_ACCEPTED_AT to acceptedAt,
        FIELD_IP_HASH to ipHash,
    )

    companion object {
        const val USERS = "users"
        const val CONSENTS = "consents"

        private const val FIELD_USER_ID = "userId"
        private const val FIELD_NAME = "name"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_CONSENT_ACCEPTED_AT = "consentAcceptedAt"
        private const val FIELD_CONSENT_VERSION = "consentVersion"
        private const val FIELD_CONSENT_ID = "consentId"
        private const val FIELD_CONSENT_TYPE = "consentType"
        private const val FIELD_VERSION = "version"
        private const val FIELD_ACCEPTED_AT = "acceptedAt"
        private const val FIELD_IP_HASH = "ipHash"

        /** Maps a Firestore throwable onto the app-wide error vocabulary. */
        fun mapFirestoreError(throwable: Throwable): AppError = when {
            throwable is FirebaseFirestoreException ->
                when (throwable.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
                    FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    -> AppError.Network(throwable)

                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        AppError.Auth(AppError.AuthReason.NOT_AUTHENTICATED, throwable)

                    else -> AppError.Unknown(throwable)
                }

            throwable is IOException -> AppError.Network(throwable)
            else -> AppError.Unknown(throwable)
        }
    }
}
