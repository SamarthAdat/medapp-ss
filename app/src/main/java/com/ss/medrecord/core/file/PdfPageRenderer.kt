package com.ss.medrecord.core.file

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PdfPageRenderer"

/**
 * Turns a stored PDF report into page bitmaps for the in-app viewer.
 *
 * Rendering happens in the app rather than by handing the file to whatever PDF
 * viewer the device has: sending a medical report out through a share intent
 * would put it in another app's hands, which is exactly what an app promising
 * per-patient scoping should not do.
 *
 * The decrypted bytes never exist as a file anyone can open - see
 * [EncryptedFileStore.openTransientPlaintext] for how the descriptor is
 * obtained and unlinked.
 */
@Singleton
class PdfPageRenderer @Inject constructor(
    private val fileStore: EncryptedFileStore,
) {

    /**
     * Renders up to [MAX_PAGES] pages at [targetWidth] pixels wide. Empty if
     * the file cannot be opened or is not a readable PDF.
     */
    fun renderPages(path: String, targetWidth: Int): List<Bitmap> {
        val descriptor = fileStore.openTransientPlaintext(path) ?: return emptyList()

        return try {
            descriptor.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    (0 until minOf(renderer.pageCount, MAX_PAGES)).mapNotNull { index ->
                        runCatching { renderer.renderPage(index, targetWidth) }
                            .onFailure { Log.w(TAG, "Could not render page $index", it) }
                            .getOrNull()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not open PDF at $path", e)
            emptyList()
        }
    }

    private fun PdfRenderer.renderPage(index: Int, targetWidth: Int): Bitmap =
        openPage(index).use { page ->
            val scale = targetWidth.toFloat() / page.width
            val bitmap = Bitmap.createBitmap(
                targetWidth,
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            // PDF pages render with a transparent background, which reads as
            // black text on nothing. Paper is white.
            Canvas(bitmap).drawColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }

    private companion object {
        /**
         * A 2 MB PDF is realistically a handful of scanned pages. The cap is
         * there so a pathological file cannot allocate bitmaps until the app
         * dies; the viewer says when it has stopped short.
         */
        const val MAX_PAGES = 40
    }
}
