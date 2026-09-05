package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.Visit
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{userId}/visits/{visitId}
 *
 * Flat under the user rather than nested inside the patient. The document
 * carries patientId as a field, so the rules can still enforce per-patient
 * scoping (spec 9.1), and a cross-patient query - "every upcoming visit" for
 * the Phase 7 dashboard - stays a single read instead of one per patient.
 */
@Singleton
class VisitRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun collection(userId: String) =
        firestore.collection(UserRemoteDataSource.USERS).document(userId).collection(VISITS)

    suspend fun upsert(visit: Visit) {
        collection(visit.userId)
            .document(visit.visitId)
            .set(visit.toFirestoreMap())
            .await()
    }

    suspend fun getAll(userId: String): List<Visit> =
        collection(userId).get().await().documents.mapNotNull { doc ->
            val patientId = doc.getString(FIELD_PATIENT_ID) ?: return@mapNotNull null
            val facilityId = doc.getString(FIELD_FACILITY_ID) ?: return@mapNotNull null
            Visit(
                visitId = doc.id,
                userId = userId,
                patientId = patientId,
                facilityId = facilityId,
                doctorName = doc.getString(FIELD_DOCTOR_NAME),
                visitDateEpochDay = doc.getLong(FIELD_VISIT_DATE) ?: return@mapNotNull null,
                notes = doc.getString(FIELD_NOTES),
                nextVisitDateEpochDay = doc.getLong(FIELD_NEXT_VISIT_DATE),
                createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                deletedAt = doc.getLong(FIELD_DELETED_AT),
            )
        }

    private fun Visit.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_VISIT_ID to visitId,
        FIELD_USER_ID to userId,
        FIELD_PATIENT_ID to patientId,
        FIELD_FACILITY_ID to facilityId,
        FIELD_DOCTOR_NAME to doctorName,
        FIELD_VISIT_DATE to visitDateEpochDay,
        FIELD_NOTES to notes,
        FIELD_NEXT_VISIT_DATE to nextVisitDateEpochDay,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to deletedAt,
    )

    companion object {
        const val VISITS = "visits"

        private const val FIELD_VISIT_ID = "visitId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_PATIENT_ID = "patientId"
        private const val FIELD_FACILITY_ID = "facilityId"
        private const val FIELD_DOCTOR_NAME = "doctorName"
        private const val FIELD_VISIT_DATE = "visitDateEpochDay"
        private const val FIELD_NOTES = "notes"
        private const val FIELD_NEXT_VISIT_DATE = "nextVisitDateEpochDay"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_DELETED_AT = "deletedAt"
    }
}
