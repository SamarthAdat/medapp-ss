package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.Facility
import com.ss.medrecord.domain.model.FacilityType
import com.ss.medrecord.domain.model.SyncStatus

@Entity(
    tableName = "facilities",
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
        Index(value = ["user_id", "deleted_at"]),
    ],
)
data class FacilityEntity(
    @PrimaryKey
    @ColumnInfo(name = "facility_id")
    val facilityId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    val name: String,
    val type: FacilityType,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun FacilityEntity.toDomain(): Facility = Facility(
    facilityId = facilityId,
    userId = userId,
    name = name,
    type = type,
    address = address,
    latitude = latitude,
    longitude = longitude,
    phone = phone,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun Facility.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): FacilityEntity =
    FacilityEntity(
        facilityId = facilityId,
        userId = userId,
        name = name,
        type = type,
        address = address,
        latitude = latitude,
        longitude = longitude,
        phone = phone,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncStatus = syncStatus,
    )
