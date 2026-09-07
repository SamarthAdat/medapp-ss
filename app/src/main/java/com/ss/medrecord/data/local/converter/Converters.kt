package com.ss.medrecord.data.local.converter

import androidx.room.TypeConverter
import com.ss.medrecord.domain.model.BloodGroup
import com.ss.medrecord.domain.model.ConsentType
import com.ss.medrecord.domain.model.Gender
import com.ss.medrecord.domain.model.MedicineFrequency
import com.ss.medrecord.domain.model.Relationship
import com.ss.medrecord.domain.model.ReminderStatus
import com.ss.medrecord.domain.model.ReminderType
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.SyncStatus
import com.ss.medrecord.domain.model.UploadStatus

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

    @TypeConverter
    fun reportFileTypeToString(value: ReportFileType): String = value.name

    /**
     * A file type the reader does not recognise degrades to PDF rather than
     * throwing: the row is still worth listing, and every unknown value would
     * have come from a build that could store types this one cannot render.
     */
    @TypeConverter
    fun stringToReportFileType(value: String): ReportFileType =
        runCatching { ReportFileType.valueOf(value) }.getOrDefault(ReportFileType.PDF)

    @TypeConverter
    fun uploadStatusToString(value: UploadStatus): String = value.name

    @TypeConverter
    fun stringToUploadStatus(value: String): UploadStatus =
        runCatching { UploadStatus.valueOf(value) }.getOrDefault(UploadStatus.PENDING)

    @TypeConverter
    fun medicineFrequencyToString(value: MedicineFrequency): String = value.name

    /**
     * An unrecognised frequency degrades to as-needed rather than to a daily
     * schedule. Getting this wrong in the other direction would have the app
     * inventing dose reminders for a pattern it does not understand, which is
     * worse than reminding about nothing.
     */
    @TypeConverter
    fun stringToMedicineFrequency(value: String): MedicineFrequency =
        runCatching { MedicineFrequency.valueOf(value) }.getOrDefault(MedicineFrequency.AS_NEEDED)

    @TypeConverter
    fun reminderTypeToString(value: ReminderType): String = value.name

    @TypeConverter
    fun stringToReminderType(value: String): ReminderType =
        runCatching { ReminderType.valueOf(value) }.getOrDefault(ReminderType.MEDICINE)

    @TypeConverter
    fun reminderStatusToString(value: ReminderStatus): String = value.name

    /**
     * Unknown statuses read as DISMISSED, the one value that cannot cause a
     * notification: a row this build cannot interpret must not be posted.
     */
    @TypeConverter
    fun stringToReminderStatus(value: String): ReminderStatus =
        runCatching { ReminderStatus.valueOf(value) }.getOrDefault(ReminderStatus.DISMISSED)
}
