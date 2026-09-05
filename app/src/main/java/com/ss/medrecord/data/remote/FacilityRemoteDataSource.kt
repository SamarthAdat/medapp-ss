package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{userId}/facilities/{facilityId}
 *
 * Account-scoped rather than patient-scoped: one clinic serves the whole
 * family, and the visit history at a facility spans patients.
 */
@Singleton
class FacilityRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun collection(userId: String) =
        firestore.collection(UserRemoteDataSource.USERS).document(userId).collection(FACILITIES)

    suspend fun upsert(facility: Facility) {
        collection(facility.userId)
            .document(facility.facilityId)
            .set(facility.toFirestoreMap())
            .await()
    }

    suspend fun getAll(userId: String): List<Facility> =
        collection(userId).get().await().documents.mapNotNull { doc ->
            val name = doc.getString(FIELD_NAME) ?: return@mapNotNull null
            Facility(
                facilityId = doc.id,
                userId = userId,
                name = name,
                type = doc.getString(FIELD_TYPE)
                    ?.let { raw -> runCatching { FacilityType.valueOf(raw) }.getOrNull() }
                    ?: FacilityType.CLINIC,
                address = doc.getString(FIELD_ADDRESS),
                latitude = doc.getDouble(FIELD_LATITUDE),
                longitude = doc.getDouble(FIELD_LONGITUDE),
                phone = doc.getString(FIELD_PHONE),
                notes = doc.getString(FIELD_NOTES),
                createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                deletedAt = doc.getLong(FIELD_DELETED_AT),
            )
        }

    private fun Facility.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_FACILITY_ID to facilityId,
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_TYPE to type.name,
        FIELD_ADDRESS to address,
        FIELD_LATITUDE to latitude,
        FIELD_LONGITUDE to longitude,
        FIELD_PHONE to phone,
        FIELD_NOTES to notes,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to deletedAt,
    )

    companion object {
        const val FACILITIES = "facilities"

        private const val FIELD_FACILITY_ID = "facilityId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_NAME = "name"
        private const val FIELD_TYPE = "type"
        private const val FIELD_ADDRESS = "address"
        private const val FIELD_LATITUDE = "latitude"
        private const val FIELD_LONGITUDE = "longitude"
        private const val FIELD_PHONE = "phone"
        private const val FIELD_NOTES = "notes"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_DELETED_AT = "deletedAt"
    }
}
