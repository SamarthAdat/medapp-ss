package com.ss.medrecord.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.ss.medrecord.data.local.MedRecordDatabase
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.ReminderDao
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.migration.Migrations
import com.ss.medrecord.core.security.DatabaseKeyProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

/**
 * Builds the encrypted Room database.
 *
 * SQLCipher replaces Room's default open helper, so every page written to disk -
 * including the write-ahead log and temp files - is AES-256 encrypted with the
 * Keystore-sealed passphrase from [DatabaseKeyProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyProvider: DatabaseKeyProvider,
    ): MedRecordDatabase {
        // Loads the bundled libsqlcipher.so. Must happen before the factory runs.
        System.loadLibrary("sqlcipher")

        val passphrase = try {
            keyProvider.getOrCreatePassphrase()
        } catch (e: Exception) {
            // The Keystore key is gone (device restore, keystore reset), so the
            // existing file can never be decrypted again. Start clean and let
            // the Phase 3 pull sync restore the data from Firestore.
            Log.w(TAG, "Database key unavailable; re-provisioning local store", e)
            keyProvider.resetKeyMaterial()
            context.deleteDatabase(MedRecordDatabase.NAME)
            keyProvider.getOrCreatePassphrase()
        }

        return Room.databaseBuilder(
            context,
            MedRecordDatabase::class.java,
            MedRecordDatabase.NAME,
        )
            // SupportOpenHelperFactory zeroes the array it is given, so it gets
            // its own copy and the caller's stays usable.
            .openHelperFactory(SupportOpenHelperFactory(passphrase.copyOf()))
            .addMigrations(*Migrations.ALL)
            .build()
            .also { passphrase.fill(0) }
    }

    @Provides
    fun provideUserDao(database: MedRecordDatabase): UserDao = database.userDao()

    @Provides
    fun provideConsentDao(database: MedRecordDatabase): ConsentDao = database.consentDao()

    @Provides
    fun providePatientDao(database: MedRecordDatabase): PatientDao = database.patientDao()

    @Provides
    fun provideAuditLogDao(database: MedRecordDatabase): AuditLogDao = database.auditLogDao()

    @Provides
    fun provideFacilityDao(database: MedRecordDatabase): FacilityDao = database.facilityDao()

    @Provides
    fun provideVisitDao(database: MedRecordDatabase): VisitDao = database.visitDao()

    @Provides
    fun provideReportDao(database: MedRecordDatabase): ReportDao = database.reportDao()

    @Provides
    fun provideMedicineDao(database: MedRecordDatabase): MedicineDao = database.medicineDao()

    @Provides
    fun provideReminderDao(database: MedRecordDatabase): ReminderDao = database.reminderDao()
}
