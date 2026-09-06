package com.ss.medrecord.core.file

import android.graphics.Bitmap
import android.util.Log
import com.ss.medrecord.domain.model.ReportFileType
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReportCompressor"

/**
 * Brings an oversized report under the 2 MB cap where that can be done without
 * destroying what makes it a medical record (spec section 5.8).
 *
 * **Images** are recompressed. A phone photo of a prescription is typically 3-6
 * MB of camera JPEG carrying far more resolution than the text needs, so the
 * pass walks down a ladder of dimensions and JPEG qualities and stops at the
 * first rung that fits. It stops descending well before the point where small
 * print stops being readable, and gives up rather than returning something
 * technically under the cap but clinically useless.
 *
 * **PDFs are not compressed.** The only way to meaningfully shrink one on
 * Android is to rasterise every page through PdfRenderer and re-encode it as
 * JPEG. That silently converts a text document into a picture of a text
 * document: selectable text and any digital signature are gone, and fine print
 * on a lab result is exactly what JPEG artefacts eat. For a record someone may
 * hand to a doctor years later, quietly degrading the document is worse than
 * refusing the file, so an oversized PDF is rejected with a message that says
 * what to do about it.
 */
@Singleton
class ReportCompressor @Inject constructor() {

    /**
     * Bytes that fit within [maxBytes], or null when this file cannot be made
     * to fit - which the caller turns into REJECTED_SIZE_LIMIT.
     */
    fun compressToFit(
        bytes: ByteArray,
        fileType: ReportFileType,
        maxBytes: Long,
    ): ByteArray? {
        if (bytes.size <= maxBytes) return bytes
        return when (fileType) {
            ReportFileType.IMAGE -> compressImage(bytes, maxBytes)
            ReportFileType.PDF -> null
        }
    }

    private fun compressImage(bytes: ByteArray, maxBytes: Long): ByteArray? {
        for (dimension in DIMENSION_LADDER) {
            val bitmap = decodeOrientedBitmap(bytes, dimension) ?: return null
            try {
                for (quality in QUALITY_LADDER) {
                    val encoded = bitmap.toJpeg(quality)
                    if (encoded.size <= maxBytes) {
                        Log.d(
                            TAG,
                            "Compressed ${bytes.size} -> ${encoded.size} bytes " +
                                "at ${dimension}px q$quality",
                        )
                        return encoded
                    }
                }
            } finally {
                bitmap.recycle()
            }
        }
        // Below the last rung the scan would stop being readable, which is not
        // a trade worth making silently.
        Log.w(TAG, "Could not bring a ${bytes.size} byte image under $maxBytes")
        return null
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private companion object {
        /**
         * Longest-edge bounds, tried in order. 2400px keeps A4 text legible at
         * roughly 200 dpi; 1200px is the floor at which a printed lab value is
         * still readable on screen.
         */
        val DIMENSION_LADDER = intArrayOf(2400, 1800, 1200)

        /** Below 60 JPEG artefacts start closing up thin printed strokes. */
        val QUALITY_LADDER = intArrayOf(85, 75, 65)
    }
}
