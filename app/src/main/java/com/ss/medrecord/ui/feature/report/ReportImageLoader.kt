package com.ss.medrecord.ui.feature.report

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ss.medrecord.core.common.DispatcherProvider
import com.ss.medrecord.core.file.EncryptedFileStore
import com.ss.medrecord.core.file.PdfPageRenderer
import com.ss.medrecord.core.file.decodeOrientedBitmap
import com.ss.medrecord.domain.model.Report
import com.ss.medrecord.domain.model.ReportFileType
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decrypts stored reports into bitmaps the UI can draw.
 *
 * Sits in the UI layer because [ImageBitmap] is a Compose type and the layers
 * below have no business knowing about it. Every call decodes at a bound rather
 * than at full size: a thumbnail grid that decoded 12-megapixel originals would
 * run the app out of memory long before it ran out of reports.
 */
@Singleton
class ReportImageLoader @Inject constructor(
    private val fileStore: EncryptedFileStore,
    private val pdfPageRenderer: PdfPageRenderer,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun thumbnail(report: Report): ImageBitmap? {
        if (report.fileType != ReportFileType.IMAGE) return null
        val path = report.localFilePath ?: return null
        return decode(path, THUMBNAIL_MAX_DIMENSION)
    }

    suspend fun fullImage(path: String): ImageBitmap? = decode(path, FULL_MAX_DIMENSION)

    suspend fun pdfPages(path: String, targetWidth: Int): List<ImageBitmap> =
        withContext(dispatchers.io) {
            pdfPageRenderer.renderPages(path, targetWidth).map { it.asImageBitmap() }
        }

    private suspend fun decode(path: String, maxDimension: Int): ImageBitmap? =
        withContext(dispatchers.io) {
            val bytes = fileStore.read(path) ?: return@withContext null
            decodeOrientedBitmap(bytes, maxDimension)?.asImageBitmap()
        }

    private companion object {
        const val THUMBNAIL_MAX_DIMENSION = 320

        /**
         * Enough to zoom into printed values on a scan without holding a
         * needlessly large bitmap for a phone screen.
         */
        const val FULL_MAX_DIMENSION = 2048
    }
}
