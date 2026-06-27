package com.example.mainactivity.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private const val ROTATE_90 = 90f
private const val ROTATE_180 = 180f
private const val ROTATE_270 = 270f

/**
 * Decodes [bytes], applies the JPEG's EXIF orientation (so portrait photos from the
 * camera/gallery aren't rotated sideways), downscales so the longest edge is at most
 * [maxDim], and re-encodes as JPEG at [quality]. Returns the original [bytes] if it
 * can't be decoded.
 */
fun compressImageWithOrientation(
    bytes: ByteArray,
    maxDim: Int,
    quality: Int,
): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val oriented = applyExifOrientation(decoded, bytes)
    val scale = minOf(maxDim.toFloat() / oriented.width, maxDim.toFloat() / oriented.height, 1f)
    val scaled =
        if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).toInt(),
                (oriented.height * scale).toInt(),
                true,
            )
        } else {
            oriented
        }
    return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
}

/** Returns [bitmap] rotated/flipped to match the EXIF orientation stored in [bytes]. */
private fun applyExifOrientation(
    bitmap: Bitmap,
    bytes: ByteArray,
): Bitmap {
    val orientation =
        runCatching {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(ROTATE_90)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(ROTATE_180)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(ROTATE_270)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(ROTATE_90)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(ROTATE_270)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
