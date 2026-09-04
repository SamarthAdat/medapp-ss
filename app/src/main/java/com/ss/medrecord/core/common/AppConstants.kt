package com.ss.medrecord.core.common

/** Values referenced across layers. Business rules from the project spec. */
object AppConstants {
    /** Hard per-file cap for medical reports (spec section 6.3). */
    const val MAX_REPORT_FILE_SIZE_BYTES: Long = 2L * 1024 * 1024

    /** Version of the consent text the user must accept (spec section 9.5). */
    const val CURRENT_CONSENT_VERSION: Int = 1

    /** Soft-deleted records are purged this many days later (spec section 9.6). */
    const val SOFT_DELETE_GRACE_PERIOD_DAYS: Int = 30

    /** Periodic background sync cadence (spec section 6.2). */
    const val SYNC_INTERVAL_MINUTES: Long = 15

    /** Dashboard shows next-visit reminders falling inside this window. */
    const val UPCOMING_VISITS_WINDOW_DAYS: Int = 7

    const val DATASTORE_SETTINGS_NAME = "medrecord_settings"
}
