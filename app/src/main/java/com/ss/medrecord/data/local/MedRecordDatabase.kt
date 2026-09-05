package com.ss.medrecord.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ss.medrecord.data.local.converter.Converters
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.entity.ConsentEntity
import com.ss.medrecord.data.local.entity.PatientEntity
import com.ss.medrecord.data.local.entity.UserEntity

/**
 * The single on-device store, opened through SQLCipher so the file on disk is
 * ciphertext (spec section 9.2). Later phases add patient, visit, report,
 * medicine, reminder and audit-log tables to this same database.
 *
 * Schemas are exported to app/schemas so every version bump is diffable in
 * review and migrations can be written against a known baseline.
 *
 * Version history:
 *  1 - users, consents (Phase 1)
 *  2 - patients (Phase 2)
 */
@Database(
    entities = [
        UserEntity::class,
        ConsentEntity::class,
        PatientEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MedRecordDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun consentDao(): ConsentDao

    abstract fun patientDao(): PatientDao

    companion object {
        const val NAME = "medrecord.db"
    }
}
