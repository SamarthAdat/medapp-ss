package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.Visit

/**
 * Deleting a patient cascades to their visits: section 9.6 requires patient
 * deletion to reach the records underneath it. The facility reference does not
 * cascade - facilities are soft-deleted and a visit must keep pointing at where
 * it happened even after the clinic is removed from the picker.
 */
@Entity(
    tableName = "visits",
    foreignKeys = [
        ForeignKey(
            entity = PatientEntity::class,
            parentColumns = ["patient_id"],
            childColumns = ["patient_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FacilityEntity::class,
            parentColumns = ["facility_id"],
            childColumns = ["facility_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("user_id"),
        Index("facility_id"),
        // The visit list filters by patient and deletion, then sorts by date.
        Index(value = ["patient_id", "deleted_at", "visit_date_epoch_day"]),
        // Phase 7 dashboard: upcoming next-visit dates across patients.
        Index(value = ["user_id", "next_visit_date_epoch_day"]),
    ],
)
data class VisitEntity(
    @PrimaryKey
    @ColumnInfo(name = "visit_id")
    val visitId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "patient_id")
    val patientId: String,
    @ColumnInfo(name = "facility_id")
    val facilityId: String,
    @ColumnInfo(name = "doctor_name")
    val doctorName: String? = null,
    @ColumnInfo(name = "visit_date_epoch_day")
    val visitDateEpochDay: Long,
    val notes: String? = null,
    @ColumnInfo(name = "next_visit_date_epoch_day")
    val nextVisitDateEpochDay: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun VisitEntity.toDomain(): Visit = Visit(
    visitId = visitId,
    userId = userId,
    patientId = patientId,
    facilityId = facilityId,
    doctorName = doctorName,
    visitDateEpochDay = visitDateEpochDay,
    notes = notes,
    nextVisitDateEpochDay = nextVisitDateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Visit.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): VisitEntity = VisitEntity(
    visitId = visitId,
    userId = userId,
    patientId = patientId,
    facilityId = facilityId,
    doctorName = doctorName,
    visitDateEpochDay = visitDateEpochDay,
    notes = notes,
    nextVisitDateEpochDay = nextVisitDateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    syncStatus = syncStatus,
)
