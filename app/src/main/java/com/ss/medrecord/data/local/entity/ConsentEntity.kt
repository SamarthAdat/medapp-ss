package com.ss.medrecord.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ss.medrecord.domain.model.ConsentRecord
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Append-only consent ledger (spec sections 4.8 and 9.5). Rows are inserted and
 * never modified, so there is no updatedAt column - acceptedAt is the only time
 * that matters.
 *
 * The row survives the user being deleted locally: consent history has to
 * outlive the account it describes for the audit trail to mean anything, so
 * there is deliberately no CASCADE delete here.
 */
@Entity(
    tableName = "consents",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["user_id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [Index("user_id")],
)
data class ConsentEntity(
    @PrimaryKey
    @ColumnInfo(name = "consent_id")
    val consentId: String,
    @ColumnInfo(name = "user_id")
    val userId: String,
    @ColumnInfo(name = "consent_type")
    val consentType: ConsentType,
    val version: Int,
    @ColumnInfo(name = "accepted_at")
    val acceptedAt: Long,
    @ColumnInfo(name = "ip_hash")
    val ipHash: String? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,
)

fun ConsentEntity.toDomain(): ConsentRecord = ConsentRecord(
    consentId = consentId,
    userId = userId,
    consentType = consentType,
    version = version,
    acceptedAt = acceptedAt,
    ipHash = ipHash,
)

fun ConsentRecord.toEntity(syncStatus: SyncStatus = SyncStatus.PENDING): ConsentEntity =
    ConsentEntity(
        consentId = consentId,
        userId = userId,
        consentType = consentType,
        version = version,
        acceptedAt = acceptedAt,
        ipHash = ipHash,
        syncStatus = syncStatus,
    )
