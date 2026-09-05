package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore access for patient profiles.
 *
 *   users/{userId}/patients/{patientId}
 *
 * The document carries userId and patientId as fields as well as in the path,
 * so security rules can double-enforce ownership (spec section 9.1) and later
 * collection-group queries stay scopeable.
 *
 * Soft-deleted patients are written, not removed: the deletion has to reach
 * other devices, and the row is only purged after the grace period (9.6).
 */
@Singleton
class PatientRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun collection(userId: String) =
        firestore.collection(UserRemoteDataSource.USERS).document(userId).collection(PATIENTS)

    suspend fun upsert(patient: Patient) {
        collection(patient.userId)
            .document(patient.patientId)
            .set(patient.toFirestoreMap())
            .await()
    }

    suspend fun getAll(userId: String): List<Patient> =
        collection(userId).get().await().documents.mapNotNull { doc ->
            val name = doc.getString(FIELD_NAME) ?: return@mapNotNull null
            Patient(
                patientId = doc.id,
                userId = userId,
                name = name,
                relationship = doc.getString(FIELD_RELATIONSHIP)
                    ?.let { raw -> runCatching { Relationship.valueOf(raw) }.getOrNull() }
                    ?: Relationship.OTHER,
                dateOfBirthEpochDay = doc.getLong(FIELD_DOB),
                gender = doc.getString(FIELD_GENDER)
                    ?.let { raw -> runCatching { Gender.valueOf(raw) }.getOrNull() },
                bloodGroup = doc.getString(FIELD_BLOOD_GROUP)
                    ?.let { raw -> runCatching { BloodGroup.valueOf(raw) }.getOrNull() },
                knownAllergies = doc.getString(FIELD_ALLERGIES),
                photoUrl = doc.getString(FIELD_PHOTO_URL),
                createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                isArchived = doc.getBoolean(FIELD_IS_ARCHIVED) ?: false,
                deletedAt = doc.getLong(FIELD_DELETED_AT),
            )
        }

    /** Permanent removal once the grace period has passed (spec section 9.6). */
    suspend fun hardDelete(userId: String, patientId: String) {
        collection(userId).document(patientId).delete().await()
    }

    private fun Patient.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_PATIENT_ID to patientId,
        FIELD_USER_ID to userId,
        FIELD_NAME to name,
        FIELD_RELATIONSHIP to relationship.name,
        FIELD_DOB to dateOfBirthEpochDay,
        FIELD_GENDER to gender?.name,
        FIELD_BLOOD_GROUP to bloodGroup?.name,
        FIELD_ALLERGIES to knownAllergies,
        FIELD_PHOTO_URL to photoUrl,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_IS_ARCHIVED to isArchived,
        FIELD_DELETED_AT to deletedAt,
    )

    companion object {
        const val PATIENTS = "patients"

        private const val FIELD_PATIENT_ID = "patientId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_NAME = "name"
        private const val FIELD_RELATIONSHIP = "relationship"
        private const val FIELD_DOB = "dateOfBirthEpochDay"
        private const val FIELD_GENDER = "gender"
        private const val FIELD_BLOOD_GROUP = "bloodGroup"
        private const val FIELD_ALLERGIES = "knownAllergies"
        private const val FIELD_PHOTO_URL = "photoUrl"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_IS_ARCHIVED = "isArchived"
        private const val FIELD_DELETED_AT = "deletedAt"
    }
}
