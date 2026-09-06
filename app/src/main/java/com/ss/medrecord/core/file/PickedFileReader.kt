package com.ss.medrecord.core.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ss.medrecord.domain.model.ReportFileType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PickedFileReader"

/** What the app learned about a file the user chose, before it accepts it. */
data class PickedFile(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val fileType: ReportFileType?,
)

/**
 * Resolves a `content://` Uri from the photo picker, the document picker or the
 * camera into metadata and bytes.
 *
 * A picked Uri is another app's data behind a temporary permission grant, so
 * everything it reports is treated as a claim: the declared size is used to
 * refuse the obviously impossible before any of it is read, and the size that
 * actually matters is measured from the bytes the app ends up holding.
 */
@Singleton
class PickedFileReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun describe(uri: Uri): PickedFile? {
        val mimeType = context.contentResolver.getType(uri)
        var displayName = uri.lastPathSegment.orEmpty()
        var size = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { displayName = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { size = cursor.getLong(it) }
            }
        }.onFailure { Log.w(TAG, "Could not query $uri", it) }

        if (displayName.isBlank()) return null

        return PickedFile(
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = size,
            fileType = ReportFileType.fromMimeType(mimeType),
        )
    }

    /**
     * The file's bytes, or null if it cannot be read or is beyond
     * [READ_CEILING_BYTES].
     *
     * The ceiling is well above the 2 MB cap on purpose: files between the two
     * are read so compression gets a chance at them, and only a file too large
     * for any plausible compression to save is refused without being opened.
     * Without it, picking a 400 MB video would try to load 400 MB into memory
     * before discovering it is not a report.
     */
    fun readBytes(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                if (out.size() > READ_CEILING_BYTES) {
                    Log.w(TAG, "Refusing $uri: larger than the read ceiling")
                    return null
                }
            }
            out.toByteArray()
        }
    }.onFailure { Log.w(TAG, "Could not read $uri", it) }.getOrNull()

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
        const val READ_CEILING_BYTES = 40L * 1024 * 1024
    }
}
