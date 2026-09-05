package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Patient
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Local mirror of a patient profile (spec section 4.2).
 *
 * Deleting the owning user cascades: patient records have no meaning without
 * the account holder, and section 9.6 requires account deletion to reach every
 * profile under it. The consent ledger is the exception to that and stays.
 */
@Entity(
    tableName = "patients",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("user_id"),
        // The list screen filters on all three of these at once.
        Index(value = ["user_id", "deleted_at", "is_archived"]),
    ],
)
data class PatientEntity(
    @PrimaryKey
    @ColumnInfo(name = "patient_id")
    val patientId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    val name: String,
    val relationship: Relationship,
    @ColumnInfo(name = "date_of_birth_epoch_day")
    val dateOfBirthEpochDay: Long? = null,
    val gender: Gender? = null,
    @ColumnInfo(name = "blood_group")
    val bloodGroup: BloodGroup? = null,
    @ColumnInfo(name = "known_allergies")
    val knownAllergies: String? = null,
    @ColumnInfo(name = "photo_url")
    val photoUrl: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun PatientEntity.toDomain(): Patient = Patient(
    patientId = patientId,
    userId = userId,
    name = name,
    relationship = relationship,
    dateOfBirthEpochDay = dateOfBirthEpochDay,
    gender = gender,
    bloodGroup = bloodGroup,
    knownAllergies = knownAllergies,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
    deletedAt = deletedAt,
)

fun Patient.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): PatientEntity = PatientEntity(
    patientId = patientId,
    userId = userId,
    name = name,
    relationship = relationship,
    dateOfBirthEpochDay = dateOfBirthEpochDay,
    gender = gender,
    bloodGroup = bloodGroup,
    knownAllergies = knownAllergies,
    photoUrl = photoUrl,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
)
