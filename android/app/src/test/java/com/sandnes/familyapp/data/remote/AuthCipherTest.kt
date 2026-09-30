package com.sandnes.familyapp.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator

class AuthCipherTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AuthCipher { key }

    @Test
    fun `Unicode secrets round trip with independent random nonces`() {
        val text = "Fictional session 😀 æøå".repeat(200)
        val first = cipher.encrypt("session", text)
        val second = cipher.encrypt("session", text)
        assertFalse(first.contentEquals(second))
        assertEquals(text, cipher.decrypt("session", first))
        assertEquals(text, cipher.decrypt("session", second))
    }

    @Test
    fun `tampered ciphertext and swapped slots fail authentication`() {
        val encrypted = cipher.encrypt("session", "fictional-token")
        val tampered = encrypted.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt("session", tampered) }
        assertThrows(AEADBadTagException::class.java) { cipher.decrypt("pkce_verifier", encrypted) }
    }

    @Test
    fun `lost key and truncated records cannot restore a session`() {
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val encrypted = cipher.encrypt("session", "fictional-token")
        assertThrows(AEADBadTagException::class.java) { AuthCipher { otherKey }.decrypt("session", encrypted) }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt("session", byteArrayOf(1)) }
    }
}
