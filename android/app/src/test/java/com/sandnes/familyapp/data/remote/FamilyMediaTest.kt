package com.sandnes.familyapp.data.remote

import com.sandnes.familyapp.testutil.FakeSupabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The process-wide media resolver, wired to the app's Supabase client. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FamilyMediaTest {
    private val backend = FakeSupabase().signIn("u1")

    @After
    fun tearDown() {
        backend.uninstall()
    }

    @Test
    fun `external urls pass through untouched`() =
        runBlocking {
            backend.install()
            assertEquals("https://shop.example/a.jpg", FamilyMedia.resolve("https://shop.example/a.jpg"))
        }
}
