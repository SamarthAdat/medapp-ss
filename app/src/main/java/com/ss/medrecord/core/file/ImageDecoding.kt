package com.ss.medrecord.core.file

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/**
 * Bitmap decoding for report images.
 *
 * Two things every decode here has to get right. It must not load a 12-megapixel
 * photo at full size to draw a thumbnail, so decoding is always sampled down to
 * a requested bound. And it must honour the EXIF orientation tag: phone cameras
 * store the sensor image unrotated and record which way up it was, so a decode
 * that ignores the tag shows a photographed prescription on its side.
 */

/**
 * Decodes [bytes] no larger than [maxDimension] on its longest edge, rotated
 * the way the camera held it. Null if the bytes are not a decodable image.
 */
fun decodeOrientedBitmap(bytes: ByteArray, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return bitmap.applyExifRotation(bytes)
}

/**
 * The power of two that brings the longest edge under [maxDimension].
 * BitmapFactory rounds anything else down to a power of two anyway.
 */
internal fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxDimension) {
        longest /= 2
        sample *= 2
    }
    return sample
}

private fun Bitmap.applyExifRotation(source: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(source)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return this
    }
    return runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }.getOrDefault(this)
}
