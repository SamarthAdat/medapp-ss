package com.ss.medrecord.data.local.converter

import androidx.room.TypeConverter
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.SyncStatus

/**
 * Enums are persisted by name rather than ordinal so that reordering or
 * inserting a constant later cannot silently reinterpret existing rows.
 *
 * Optional enums decode leniently to null on an unrecognised value: a row
 * written by a newer build of the app should degrade to a missing field, not
 * crash the reader. Required enums keep throwing, because there is no sensible
 * value to substitute.
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

    @TypeConverter
    fun relationshipToString(value: Relationship): String = value.name

    @TypeConverter
    fun stringToRelationship(value: String): Relationship =
        runCatching { Relationship.valueOf(value) }.getOrDefault(Relationship.OTHER)

    @TypeConverter
    fun genderToString(value: Gender?): String? = value?.name

    @TypeConverter
    fun stringToGender(value: String?): Gender? =
        value?.let { runCatching { Gender.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun bloodGroupToString(value: BloodGroup?): String? = value?.name

    @TypeConverter
    fun stringToBloodGroup(value: String?): BloodGroup? =
        value?.let { runCatching { BloodGroup.valueOf(it) }.getOrNull() }
}
