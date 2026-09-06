package com.ss.medrecord.domain.repository

import android.net.Uri
import com.ss.medrecord.core.common.DataResult
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportWithContext
import kotlinx.coroutines.flow.Flow

/**
 * What happened to a file the user picked.
 *
 * A refusal is an ordinary outcome, not an error: the app did its job by
 * checking the file and saying why it cannot keep it, so callers get a result
 * they have to describe rather than an exception they might swallow.
 */
sealed interface ReportImportResult {
    /** Stored, encrypted, queued for upload. */
    data class Stored(val reportId: String, val wasCompressed: Boolean) : ReportImportResult

    /** Over the cap even after compression; [message] explains what to do. */
    data class Rejected(val reportId: String, val message: String) : ReportImportResult
}

interface ReportRepository {

    /** Every report on the account, with the visit context the grid renders. */
    fun observeReportsWithContext(userId: String): Flow<List<ReportWithContext>>

    fun observeReportsForVisit(visitId: String): Flow<List<Report>>

    fun observeReport(reportId: String): Flow<Report?>

    fun observeReportCountForVisit(visitId: String): Flow<Int>

    fun observeReportCountForPatient(patientId: String): Flow<Int>

    suspend fun getReport(reportId: String): Report?

    /** Reads, size-checks, compresses if needed, encrypts and queues [uri]. */
    suspend fun importReport(
        uri: Uri,
        patientId: String,
        visitId: String,
    ): DataResult<ReportImportResult>

    /**
     * Guarantees a decryptable local copy exists, downloading it if this device
     * has only the metadata row. Returns the path of the encrypted file.
     */
    suspend fun ensureLocalCopy(reportId: String): DataResult<String>

    /** Puts a failed upload back in the queue and wakes the worker. */
    suspend fun retryUpload(reportId: String): DataResult<Unit>

    suspend fun deleteReport(reportId: String): DataResult<Unit>

    suspend fun refreshReports(userId: String): DataResult<Unit>
}
