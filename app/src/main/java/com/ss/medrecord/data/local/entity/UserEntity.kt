package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.AppUser
import com.ss.medrecord.domain.model.SyncStatus

/** Local mirror of the account holder (spec section 4.1). */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "consent_accepted_at")
    val consentAcceptedAt: Long? = null,
    @ColumnInfo(name = "consent_version")
    val consentVersion: Int? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun UserEntity.toDomain(): AppUser = AppUser(
    userId = userId,
    name = name,
    email = email,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    consentAcceptedAt = consentAcceptedAt,
    consentVersion = consentVersion,
)

fun AppUser.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): UserEntity = UserEntity(
    userId = userId,
    name = name,
    email = email,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    consentAcceptedAt = consentAcceptedAt,
    consentVersion = consentVersion,
    syncStatus = syncStatus,
)
