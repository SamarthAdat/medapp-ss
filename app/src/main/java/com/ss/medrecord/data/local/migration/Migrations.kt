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

    /** Phase 4 adds facilities and visits. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `facilities` (
                    `facility_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `address` TEXT,
                    `latitude` REAL,
                    `longitude` REAL,
                    `phone` TEXT,
                    `notes` TEXT,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `sync_status` TEXT NOT NULL,
                    PRIMARY KEY(`facility_id`),
                    FOREIGN KEY(`user_id`) REFERENCES `users`(`user_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_facilities_user_id` ON `facilities` (`user_id`)",
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_facilities_user_id_deleted_at`
                ON `facilities` (`user_id`, `deleted_at`)
                """.trimIndent(),
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `visits` (
                    `visit_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `patient_id` TEXT NOT NULL,
                    `facility_id` TEXT NOT NULL,
                    `doctor_name` TEXT,
                    `visit_date_epoch_day` INTEGER NOT NULL,
                    `notes` TEXT,
                    `next_visit_date_epoch_day` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `sync_status` TEXT NOT NULL,
                    PRIMARY KEY(`visit_id`),
                    FOREIGN KEY(`patient_id`) REFERENCES `patients`(`patient_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`facility_id`) REFERENCES `facilities`(`facility_id`)
                        ON UPDATE NO ACTION ON DELETE NO ACTION
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_user_id` ON `visits` (`user_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_visits_facility_id` ON `visits` (`facility_id`)",
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_visits_patient_id_deleted_at_visit_date_epoch_day`
                ON `visits` (`patient_id`, `deleted_at`, `visit_date_epoch_day`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_visits_user_id_next_visit_date_epoch_day`
                ON `visits` (`user_id`, `next_visit_date_epoch_day`)
                """.trimIndent(),
            )
        }
    }

    /** Phase 5 adds report file metadata. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reports` (
                    `report_id` TEXT NOT NULL,
                    `user_id` TEXT NOT NULL,
                    `patient_id` TEXT NOT NULL,
                    `visit_id` TEXT NOT NULL,
                    `file_name` TEXT NOT NULL,
                    `file_type` TEXT NOT NULL,
                    `file_size_bytes` INTEGER NOT NULL,
                    `local_file_path` TEXT,
                    `remote_storage_url` TEXT,
                    `upload_status` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    `deleted_at` INTEGER,
                    `sync_status` TEXT NOT NULL,
                    PRIMARY KEY(`report_id`),
                    FOREIGN KEY(`patient_id`) REFERENCES `patients`(`patient_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`visit_id`) REFERENCES `visits`(`visit_id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reports_user_id` ON `reports` (`user_id`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reports_visit_id` ON `reports` (`visit_id`)",
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_reports_patient_id_deleted_at_created_at`
                ON `reports` (`patient_id`, `deleted_at`, `created_at`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_reports_upload_status`
                ON `reports` (`upload_status`)
                """.trimIndent(),
            )
        }
    }

    val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}
