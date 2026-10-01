package com.sandnes.familyapp.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageUtilsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun jpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** Writes [bytes] to a file, stamps the EXIF [orientation] onto it and reads the file back. */
    private fun withOrientation(
        bytes: ByteArray,
        orientation: Int,
    ): ByteArray {
        val file = folder.newFile()
        file.writeBytes(bytes)
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file.readBytes()
    }

    private fun size(bytes: ByteArray): Pair<Int, Int> {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        return decoded.width to decoded.height
    }

    @Test
    fun undecodableBytesAreReturnedUnchanged() {
        val garbage = byteArrayOf(1, 2, 3, 4)
        mockkStatic(BitmapFactory::class)
        try {
            every { BitmapFactory.decodeByteArray(any(), any(), any()) } returns null
            assertArrayEquals(garbage, compressImageWithOrientation(garbage, maxDim = 100, quality = 80))
        } finally {
            unmockkStatic(BitmapFactory::class)
        }
    }

    @Test
    fun smallImagesKeepTheirSize() {
        val (w, h) = size(compressImageWithOrientation(jpeg(40, 20), maxDim = 100, quality = 80))
        assertEquals(40 to 20, w to h)
    }

    @Test
    fun largeImagesAreScaledSoTheLongestEdgeFits() {
        val (w, h) = size(compressImageWithOrientation(jpeg(400, 200), maxDim = 100, quality = 80))
        assertEquals(100, w)
        assertEquals(50, h)
    }

    @Test
    fun rotationOrientationsSwapWidthAndHeight() {
        for (orientation in listOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )) {
            val source = withOrientation(jpeg(60, 30), orientation)
            val (w, h) = size(compressImageWithOrientation(source, maxDim = 1000, quality = 80))
            assertEquals("orientation $orientation", 30 to 60, w to h)
        }
    }

    @Test
    fun flipAndHalfTurnOrientationsKeepDimensions() {
        for (orientation in listOf(
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_UNDEFINED,
        )) {
            val source = withOrientation(jpeg(60, 30), orientation)
            val (w, h) = size(compressImageWithOrientation(source, maxDim = 1000, quality = 80))
            assertEquals("orientation $orientation", 60 to 30, w to h)
        }
    }

    @Test
    fun lowerQualityProducesSmallerOutput() {
        val noisy =
            Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also { b ->
                for (x in 0 until 200) for (y in 0 until 200) b.setPixel(x, y, (x * 31 + y * 17) or 0xFF000000.toInt())
            }
        val source = ByteArrayOutputStream().also { noisy.compress(Bitmap.CompressFormat.JPEG, 100, it) }.toByteArray()
        val high = compressImageWithOrientation(source, maxDim = 1000, quality = 95)
        val low = compressImageWithOrientation(source, maxDim = 1000, quality = 10)
        assertTrue(low.size < high.size)
    }
}
