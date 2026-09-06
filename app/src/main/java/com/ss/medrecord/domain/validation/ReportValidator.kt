package com.ss.medrecord.domain.validation

import com.ss.medrecord.core.common.AppConstants
import com.ss.medrecord.domain.model.ReportFileType
import com.ss.medrecord.domain.model.formatFileSize

/**
 * The rules a picked file has to clear before it becomes a report (spec 5.8).
 *
 * The size rule is checked here on the way in and again by the Storage security
 * rules on the way out. Two checks for one limit is deliberate: the client one
 * gives the user an immediate, explanatory message, and the server one is the
 * check that actually holds, since a modified client can skip the first.
 */
object ReportValidator {

    const val MAX_FILE_NAME_LENGTH = 120

    val maxSizeBytes: Long get() = AppConstants.MAX_REPORT_FILE_SIZE_BYTES

    /** Human-readable cap, for messages that have to name the limit. */
    val maxSizeLabel: String get() = formatFileSize(maxSizeBytes)

    fun isWithinSizeLimit(sizeBytes: Long): Boolean = sizeBytes <= maxSizeBytes

    /**
     * Checked after any compression attempt, so the message describes the file
     * as it would actually be stored rather than as it was picked.
     */
    fun validateSize(sizeBytes: Long): ValidationResult = when {
        sizeBytes <= 0 -> ValidationResult.invalid("That file is empty")
        !isWithinSizeLimit(sizeBytes) ->
            ValidationResult.invalid(
                "File exceeds $maxSizeLabel limit (${formatFileSize(sizeBytes)})",
            )

        else -> ValidationResult.Valid
    }

    /** Only what the app can render back to the user; see [ReportFileType]. */
    fun validateFileType(mimeType: String?): ValidationResult =
        when (ReportFileType.fromMimeType(mimeType)) {
            null -> ValidationResult.invalid("Only PDF and image files can be attached")
            else -> ValidationResult.Valid
        }

    fun validateFileName(fileName: String): ValidationResult = when {
        fileName.isBlank() -> ValidationResult.invalid("File name is required")
        fileName.length > MAX_FILE_NAME_LENGTH ->
            ValidationResult.invalid("Keep the file name under $MAX_FILE_NAME_LENGTH characters")

        else -> ValidationResult.Valid
    }

    /**
     * Strips anything that could make the name traverse or escape a directory.
     * The name is only ever a label here - the stored file is named after its
     * report id - but it also becomes Cloud Storage metadata, so it is cleaned
     * at the boundary rather than trusted downstream.
     */
    fun sanitiseFileName(fileName: String): String = fileName
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .replace("..", "")
        .trim()
        .take(MAX_FILE_NAME_LENGTH)
        .ifBlank { "report" }
}
