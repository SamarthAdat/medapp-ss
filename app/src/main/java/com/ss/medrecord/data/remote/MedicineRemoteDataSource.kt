package com.ss.medrecord.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.ss.medrecord.domain.model.Medicine
import com.ss.medrecord.domain.model.MedicineFrequency
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * users/{userId}/medicines/{medicineId}
 *
 * Flat under the user, like visits and reports, so "everything this person is
 * taking" is one read rather than one per patient.
 *
 * Reminder times travel as a real array of ints rather than the packed string
 * the local table uses. The packing exists to keep one small list out of a
 * second SQLite table; Firestore has arrays, and a document another client can
 * read should not need to know this app's storage shortcut.
 */
@Singleton
class MedicineRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    private fun collection(userId: String) =
        firestore.collection(UserRemoteDataSource.USERS).document(userId).collection(MEDICINES)

    suspend fun upsert(medicine: Medicine) {
        collection(medicine.userId)
            .document(medicine.medicineId)
            .set(medicine.toFirestoreMap())
            .await()
    }

    suspend fun getAll(userId: String): List<Medicine> =
        collection(userId).get().await().documents.mapNotNull { doc ->
            val patientId = doc.getString(FIELD_PATIENT_ID) ?: return@mapNotNull null
            Medicine(
                medicineId = doc.id,
                userId = userId,
                patientId = patientId,
                visitId = doc.getString(FIELD_VISIT_ID),
                name = doc.getString(FIELD_NAME) ?: return@mapNotNull null,
                dosage = doc.getString(FIELD_DOSAGE),
                frequency = doc.getString(FIELD_FREQUENCY)
                    ?.let { raw -> runCatching { MedicineFrequency.valueOf(raw) }.getOrNull() }
                    ?: MedicineFrequency.AS_NEEDED,
                reminderTimes = readTimes(doc.get(FIELD_REMINDER_TIMES)),
                startDateEpochDay = doc.getLong(FIELD_START_DATE) ?: return@mapNotNull null,
                endDateEpochDay = doc.getLong(FIELD_END_DATE),
                instructions = doc.getString(FIELD_INSTRUCTIONS),
                isActive = doc.getBoolean(FIELD_IS_ACTIVE) ?: true,
                createdAt = doc.getLong(FIELD_CREATED_AT) ?: 0L,
                updatedAt = doc.getLong(FIELD_UPDATED_AT) ?: 0L,
                deletedAt = doc.getLong(FIELD_DELETED_AT),
            )
        }

    /**
     * Firestore hands back numbers as Long regardless of what was written, and
     * the array may be absent or hold something unexpected if another client
     * ever writes here. Anything not a plausible minute-of-day is dropped.
     */
    private fun readTimes(raw: Any?): List<Int> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { (it as? Number)?.toInt() }
            .filter { it in 0 until MINUTES_IN_DAY }
            .distinct()
            .sorted()
    }

    private fun Medicine.toFirestoreMap(): Map<String, Any?> = mapOf(
        FIELD_MEDICINE_ID to medicineId,
        FIELD_USER_ID to userId,
        FIELD_PATIENT_ID to patientId,
        FIELD_VISIT_ID to visitId,
        FIELD_NAME to name,
        FIELD_DOSAGE to dosage,
        FIELD_FREQUENCY to frequency.name,
        FIELD_REMINDER_TIMES to reminderTimes,
        FIELD_START_DATE to startDateEpochDay,
        FIELD_END_DATE to endDateEpochDay,
        FIELD_INSTRUCTIONS to instructions,
        FIELD_IS_ACTIVE to isActive,
        FIELD_CREATED_AT to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to deletedAt,
    )

    companion object {
        const val MEDICINES = "medicines"

        private const val MINUTES_IN_DAY = 24 * 60

        private const val FIELD_MEDICINE_ID = "medicineId"
        private const val FIELD_USER_ID = "userId"
        private const val FIELD_PATIENT_ID = "patientId"
        private const val FIELD_VISIT_ID = "visitId"
        private const val FIELD_NAME = "name"
        private const val FIELD_DOSAGE = "dosage"
        private const val FIELD_FREQUENCY = "frequency"
        private const val FIELD_REMINDER_TIMES = "reminderTimes"
        private const val FIELD_START_DATE = "startDateEpochDay"
        private const val FIELD_END_DATE = "endDateEpochDay"
        private const val FIELD_INSTRUCTIONS = "instructions"
        private const val FIELD_IS_ACTIVE = "isActive"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_DELETED_AT = "deletedAt"
    }
}
