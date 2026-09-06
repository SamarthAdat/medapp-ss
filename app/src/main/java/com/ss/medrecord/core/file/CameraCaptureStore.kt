package com.ss.medrecord.core.file

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The staging area for photos taken with the device camera.
 *
 * The camera app writes where it is told, and it cannot write into another
 * app's private storage encrypted - so a freshly taken photo of a prescription
 * necessarily lands as an ordinary file first. This class keeps that window as
 * small as it can be: captures go into one dedicated directory, the directory
 * is emptied before every capture and again as soon as the import has read the
 * bytes, and it is emptied on sign-out. Nothing else is ever written there, so
 * "delete everything in it" is always the right cleanup.
 */
@Singleton
class CameraCaptureStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private val captureDir: File
        get() = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }

    /**
     * A content Uri the camera app may write one photo to. Clears anything left
     * behind first, so a capture the user abandoned does not outlive the next
     * one.
     */
    fun newCaptureUri(): Uri {
        clear()
        val file = File(captureDir, "capture.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    fun clear() {
        runCatching { captureDir.listFiles()?.forEach { it.delete() } }
    }

    private companion object {
        const val CAPTURE_DIR = "capture"
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}
