package com.ss.medrecord.data.local.converter

import androidx.room.TypeConverter
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Enums are persisted by name rather than ordinal so that reordering or
 * inserting a constant later cannot silently reinterpret existing rows.
 */
class Converters {

    @TypeConverter
    fun syncStatusToString(value: SyncStatus): String = value.name

    @TypeConverter
    fun stringToSyncStatus(value: String): SyncStatus =
        runCatching { SyncStatus.valueOf(value) }.getOrDefault(SyncStatus.PENDING)

    @TypeConverter
    fun consentTypeToString(value: ConsentType): String = value.name

    @TypeConverter
    fun stringToConsentType(value: String): ConsentType = ConsentType.valueOf(value)
}
