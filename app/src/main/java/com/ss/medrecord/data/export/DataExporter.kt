package com.ss.medrecord.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.data.local.dao.AuditLogDao
import com.ss.medrecord.data.local.dao.ConsentDao
import com.ss.medrecord.data.local.dao.FacilityDao
import com.ss.medrecord.data.local.dao.MedicineDao
import com.ss.medrecord.data.local.dao.PatientDao
import com.ss.medrecord.data.local.dao.ReportDao
import com.ss.medrecord.data.local.dao.UserDao
import com.ss.medrecord.data.local.dao.VisitDao
import com.ss.medrecord.data.local.entity.decodeReminderTimes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports everything the account holds, for the right of access and data
 * portability (DPDP Act 2023 section 11; HIPAA right of access).
 *
 * JSON rather than PDF, and deliberately so. Portability means the data has to
 * be usable somewhere else, and a PDF of a table is a picture of data, not
 * data. The structure mirrors the database so a reader can tell what came from
 * where.
 *
 * Two things are excluded, both on purpose:
 *
 *  - report file bytes. They are the largest thing here by orders of magnitude,
 *    and an export that silently becomes a 40 MB file is one that fails to
 *    share. The metadata names every file so the user knows what exists.
 *  - the SQLCipher passphrase and the Keystore-sealed keys. An export is a
 *    plaintext file the user is about to hand to something else; putting the
 *    keys in it would make every other protection in this app pointless.
 *
 * The file is written to a dedicated cache directory that the FileProvider
 * exposes, and the previous export is cleared first - a stale copy of someone's
 * whole medical history sitting in a shared-readable directory is exactly the
 * kind of thing that leaks.
 */
@Singleton
class DataExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userDao: UserDao,
    private val consentDao: ConsentDao,
    private val patientDao: PatientDao,
    private val facilityDao: FacilityDao,
    private val visitDao: VisitDao,
    private val reportDao: ReportDao,
    private val medicineDao: MedicineDao,
    private val auditLogDao: AuditLogDao,
    private val dispatchers: DispatcherProvider,
) {

    private val exportDir: File
        get() = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }

    /** Writes the export and returns a Uri another app can read once. */
    suspend fun export(userId: String): ExportResult = withContext(dispatchers.io) {
        clear()

        val json = buildJson(userId)
        val file = File(exportDir, fileName())
        file.writeText(json.toString(INDENT))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        ExportResult(uri = uri, fileName = file.name, sizeBytes = file.length())
    }

    /** Removes any previous export. Called before writing and on sign-out. */
    fun clear() {
        runCatching { exportDir.listFiles()?.forEach(File::delete) }
    }

    private suspend fun buildJson(userId: String): JSONObject {
        val user = userDao.getUser(userId)

        return JSONObject().apply {
            put("exportedAt", isoNow())
            put("schemaVersion", SCHEMA_VERSION)
            put(
                "notice",
                "Report files are not included; their details are listed under " +
                    "\"reports\". Encryption keys are never exported.",
            )

            put(
                "account",
                JSONObject().apply {
                    put("userId", userId)
                    put("name", user?.name)
                    put("email", user?.email)
                    put("consentVersion", user?.consentVersion)
                },
            )

            put(
                "consents",
                consentDao.observeConsents(userId).first().toJsonArray { consent ->
                    JSONObject().apply {
                        put("consentType", consent.consentType.name)
                        put("version", consent.version)
                        put("acceptedAt", isoOf(consent.acceptedAt))
                    }
                },
            )

            put(
                "patients",
                patientDao.observeActivePatients(userId).first().toJsonArray { patient ->
                    JSONObject().apply {
                        put("patientId", patient.patientId)
                        put("name", patient.name)
                        put("relationship", patient.relationship.name)
                        put("dateOfBirth", patient.dateOfBirthEpochDay?.let(::isoDate))
                        put("gender", patient.gender?.name)
                        put("bloodGroup", patient.bloodGroup?.label)
                        put("knownAllergies", patient.knownAllergies)
                    }
                },
            )

            put(
                "facilities",
                facilityDao.observeFacilities(userId).first().toJsonArray { facility ->
                    JSONObject().apply {
                        put("facilityId", facility.facilityId)
                        put("name", facility.name)
                        put("type", facility.type.name)
                        put("address", facility.address)
                        put("phone", facility.phone)
                        put("latitude", facility.latitude)
                        put("longitude", facility.longitude)
                    }
                },
            )

            put(
                "visits",
                visitDao.observeVisitsWithContext(userId).first().toJsonArray { row ->
                    JSONObject().apply {
                        put("visitId", row.visit.visitId)
                        put("patientId", row.visit.patientId)
                        put("facility", row.facilityName)
                        put("date", isoDate(row.visit.visitDateEpochDay))
                        put("doctorName", row.visit.doctorName)
                        put("notes", row.visit.notes)
                        put(
                            "nextVisitDate",
                            row.visit.nextVisitDateEpochDay?.let(::isoDate),
                        )
                    }
                },
            )

            put(
                "reports",
                reportDao.observeReportsWithContext(userId).first().toJsonArray { row ->
                    JSONObject().apply {
                        put("reportId", row.report.reportId)
                        put("patientId", row.report.patientId)
                        put("visitId", row.report.visitId)
                        put("fileName", row.report.fileName)
                        put("fileType", row.report.fileType.name)
                        put("fileSizeBytes", row.report.fileSizeBytes)
                        put("addedAt", isoOf(row.report.createdAt))
                        // Named rather than embedded, so the user knows what
                        // exists even though the bytes are not in this file.
                        put("fileIncludedInExport", false)
                    }
                },
            )

            put(
                "medicines",
                medicineDao.observeMedicinesWithContext(userId).first().toJsonArray { row ->
                    JSONObject().apply {
                        put("medicineId", row.medicine.medicineId)
                        put("patientId", row.medicine.patientId)
                        put("name", row.medicine.name)
                        put("dosage", row.medicine.dosage)
                        put("frequency", row.medicine.frequency.name)
                        put(
                            "reminderTimes",
                            JSONArray().apply {
                                decodeReminderTimes(row.medicine.reminderTimes)
                                    .forEach { put(formatMinutes(it)) }
                            },
                        )
                        put("startDate", isoDate(row.medicine.startDateEpochDay))
                        put("endDate", row.medicine.endDateEpochDay?.let(::isoDate))
                        put("instructions", row.medicine.instructions)
                        put("isActive", row.medicine.isActive)
                    }
                },
            )

            // The access log is part of what the user is entitled to see: it is
            // the record of who touched their data and when.
            put(
                "accessLog",
                auditLogDao.observeRecent(userId, AUDIT_EXPORT_LIMIT).first()
                    .toJsonArray { entry ->
                        JSONObject().apply {
                            put("action", entry.action.name)
                            put("entityType", entry.entityType.name)
                            put("entityId", entry.entityId)
                            put("at", isoOf(entry.timestamp))
                        }
                    },
            )
        }
    }

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(transform(it)) } }

    private fun formatMinutes(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    private fun isoNow() = isoOf(System.currentTimeMillis())

    private fun isoOf(millis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))

    private fun isoDate(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun fileName(): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        return "medrecord-export-$stamp.json"
    }

    private companion object {
        const val EXPORT_DIR = "export"
        const val INDENT = 2

        /** Bumped whenever the shape below changes, so a reader can tell. */
        const val SCHEMA_VERSION = 1

        /**
         * The trail can run to thousands of entries. This is the same window
         * the settings screen shows, which keeps the export honest about being
         * a copy of what the app can show rather than of the whole database.
         */
        const val AUDIT_EXPORT_LIMIT = 500
    }
}

/** Where the export landed, and how big it turned out. */
data class ExportResult(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
)
