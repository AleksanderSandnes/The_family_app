package com.sandnes.familyapp.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise Coil's real cache/fetch pipeline with fictional pixels and no network. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class PrivateMediaInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val project = "https://fictional.supabase.co"
    private val public = "$project/storage/v1/object/public/avatars/user/avatar.jpg"
    private val signed = "$project/storage/v1/object/sign/avatars/user/avatar.jpg?token=fictional"
    private var account: String? = "fictional-a"
    private var signCount = 0
    private var fetchCount = 0
    private var duringFetch: () -> Unit = {}

    private fun loader(): ImageLoader {
        val memory = MemoryCache.Builder().maxSizeBytes(1024).build()
        memory[MemoryCache.Key("fixture")] = MemoryCache.Value(pixel(Color.BLUE).asImage())
        val resolver =
            MediaUrlResolver(project, { account }) {
                signCount++
                signed
            }
        return ImageLoader
            .Builder(context)
            .memoryCache(memory)
            .components {
                add(PrivateMediaInterceptor(resolver))
                add(
                    object : Fetcher.Factory<Uri> {
                        override fun create(
                            data: Uri,
                            options: Options,
                            imageLoader: ImageLoader,
                        ): Fetcher? {
                            if (data.toString() != signed) return null
                            return Fetcher {
                                fetchCount++
                                duringFetch()
                                ImageFetchResult(pixel(Color.RED).asImage(), false, DataSource.NETWORK)
                            }
                        }
                    },
                )
            }.build()
    }

    private fun pixel(color: Int): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun request(url: String) =
        ImageRequest
            .Builder(context)
            .data(url)
            .memoryCacheKey("fixture")
            .size(1, 1)
            .build()

    @Test
    fun oldSharedCacheCannotServeEitherAccountsProtectedImages() =
        runTest {
            val loader = loader()
            try {
                for (id in listOf("fictional-a", "fictional-b")) {
                    account = id
                    val result = loader.execute(request(public)) as SuccessResult
                    assertEquals(Color.RED, result.image.toBitmap().getPixel(0, 0))
                    assertEquals(DataSource.NETWORK, result.dataSource)
                }
                assertEquals(2, signCount)
                assertEquals(2, fetchCount)
            } finally {
                loader.shutdown()
            }
        }

    @Test
    fun missingSessionCannotReadSeededPrivatePixels() =
        runTest {
            val loader = loader()
            try {
                account = null
                assertTrue(loader.execute(request(public)) is ErrorResult)
                assertEquals(0, signCount)
                assertEquals(0, fetchCount)
            } finally {
                loader.shutdown()
            }
        }

    @Test
    fun accountSwitchDuringTheFetchDiscardsDownloadedPixels() =
        runTest {
            val loader = loader()
            try {
                duringFetch = { account = "fictional-b" }
                val result = loader.execute(request(public)) as ErrorResult
                assertTrue(result.throwable is IllegalStateException)
                assertEquals(1, fetchCount)
            } finally {
                loader.shutdown()
            }
        }

    @Test
    fun publicExternalAssetsRetainNormalCachingWithoutASession() =
        runTest {
            val loader = loader()
            try {
                account = null
                val result = loader.execute(request("https://shop.example/image.jpg")) as SuccessResult
                assertEquals(Color.BLUE, result.image.toBitmap().getPixel(0, 0))
                assertEquals(DataSource.MEMORY_CACHE, result.dataSource)
                assertEquals(0, signCount)
                assertEquals(0, fetchCount)
            } finally {
                loader.shutdown()
            }
        }
}
