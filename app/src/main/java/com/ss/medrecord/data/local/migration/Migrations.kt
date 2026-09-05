package com.ss.medrecord.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * Destructive fallback is deliberately not enabled: the local database is the
 * source of truth for anything not yet synced, so wiping it on a schema change
 * would lose records the user entered offline.
 */
object Migrations {

    /** Phase 2 adds patient profiles. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `patients` (
                    `patient_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `relationship` TEXT NOT NULL,
                    `date_of_birth_epoch_day` INTEGER,
                    `gender` TEXT,
                    `blood_group` TEXT,
                    `known_allergies` TEXT,
                    `photo_url` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `is_archived` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `sync_status` TEXT NOT NULL,
                    PRIMARY KEY(`patient_id`),
                    FOREIGN KEY(`user_id`) REFERENCES `users`(`user_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_patients_user_id` ON `patients` (`user_id`)",
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_patients_user_id_deleted_at_is_archived`
                ON `patients` (`user_id`, `deleted_at`, `is_archived`)
                """.trimIndent(),
            )
        }
    }

    /** Phase 3 adds the audit trail. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `audit_logs` (
                    `log_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `patient_id` TEXT,
                    `action` TEXT NOT NULL,
                    `entity_type` TEXT NOT NULL,
                    `entity_id` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `device_id_hash` TEXT NOT NULL,
                    `sync_status` TEXT NOT NULL,
                    PRIMARY KEY(`log_id`)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_user_id` ON `audit_logs` (`user_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_patient_id` ON `audit_logs` (`patient_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_sync_status` ON `audit_logs` (`sync_status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_timestamp` ON `audit_logs` (`timestamp`)")
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
