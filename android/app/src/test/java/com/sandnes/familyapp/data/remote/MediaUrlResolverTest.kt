package com.sandnes.familyapp.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MediaUrlResolverTest {
    private val project = "https://fictional.supabase.co"
    private var account: String? = "fictional-account-a"
    private val signed = "$project/storage/v1/object/sign/avatars/user/avatar.jpg?token=fictional"
    private val public = "$project/storage/v1/object/public/avatars/user/avatar.jpg?t=123"

    private fun resolver(sign: suspend (StorageMediaReference) -> String = { signed }) =
        MediaUrlResolver(project, { account }, sign)

    @Test
    fun extractsEachBucketAndPreservesEncodedUnicodeAndPlus() {
        for (bucket in listOf("avatars", "group-images", "wish-images", "chat-media")) {
            for (mode in listOf("public", "authenticated", "sign")) {
                val url = "$project/storage/v1/object/$mode/$bucket/user/p%C3%A5+ss.jpg?token=old&t=123"
                assertEquals(StorageMediaReference(bucket, "user/på+ss.jpg"), resolver().reference(url))
            }
        }
    }

    @Test
    fun rejectsUnsafeObjectsAndUnsupportedStorageRoutes() {
        for (suffix in listOf("", "user/", "user//image.jpg", "user/../image.jpg", "user/%2e%2e/image.jpg", "user/%252e%252e/image.jpg", "user/%2fimage.jpg", "user/%5cimage.jpg", "user/%00image.jpg")) {
            assertThrows(IllegalArgumentException::class.java) {
                resolver().reference("$project/storage/v1/object/public/avatars/$suffix")
            }
        }
        for (url in listOf("$project/storage/v1/object/public/unknown/user/file", "$project/storage/v1/render/image/public/avatars/user/file", "$public#fragment", public.replace("https:", "http:"), public.replace("fictional.supabase.co", "user@fictional.supabase.co"), public.replace("fictional.supabase.co", "fictional.supabase.co:444"))) {
            assertThrows(IllegalArgumentException::class.java) { resolver().reference(url) }
        }
    }

    @Test
    fun externalAssetsNeverAskForAnAccountOrSigning() =
        runTest {
            account = null
            val resolver = resolver { error("External asset was signed") }
            for (url in listOf("https://shop.example/image.jpg", "https://fictional.supabase.co.attacker.example/storage/v1/object/public/avatars/user/avatar.jpg", "$project/other/image.jpg")) {
                assertNull(resolver.reference(url))
                assertEquals(url, resolver.resolve(url))
            }
        }

    @Test
    fun signsTheStableObjectInsteadOfReusingPersistedTokens() =
        runTest {
            var count = 0
            val resolver =
                resolver {
                    assertEquals(StorageMediaReference("avatars", "user/avatar.jpg"), it)
                    count++
                    signed
                }
            assertEquals("$signed&t=123", resolver.resolve(public))
            assertEquals(signed, resolver.resolve(signed.replace("token=fictional", "token=stale")))
            assertEquals(2, count)
        }

    @Test
    fun signOutOrAnAccountChangeCannotReturnAnOldAccountsLink() =
        runTest {
            for (next in listOf(null, "fictional-account-b")) {
                account = "fictional-account-a"
                val resolver =
                    resolver {
                        account = next
                        signed
                    }
                expectFailure<IllegalStateException> {
                    resolver.resolve(public)
                }
                assertEquals(next, account)
            }
        }

    @Test
    fun failedSigningNeverFallsBackToAPublicLink() =
        runTest {
            val resolver = resolver { throw java.io.IOException("offline") }
            expectFailure<java.io.IOException> {
                resolver.resolve(public)
            }
            account = null
            expectFailure<IllegalStateException> {
                resolver.resolve(public)
            }
        }

    @Test
    fun signerCannotSubstituteAnotherOriginObjectOrPublicRoute() =
        runTest {
            for (result in listOf(signed.replace("fictional.supabase.co", "attacker.example"), signed.replace("avatar.jpg", "other.jpg"), public)) {
                expectFailure<IllegalArgumentException> {
                    resolver { result }.resolve(public)
                }
            }
        }

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (failure: Throwable) {
            assertTrue("Unexpected failure type", failure is T)
            return
        }
        fail("Expected ${T::class.java.simpleName}")
    }
}
