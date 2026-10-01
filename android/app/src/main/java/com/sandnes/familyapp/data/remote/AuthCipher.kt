package com.sandnes.familyapp.data.remote

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticate the slot name as well as its ciphertext to prevent value substitution. */
internal class AuthCipher(
    private val key: () -> SecretKey,
) {
    fun encrypt(
        name: String,
        plaintext: String,
    ): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        check(cipher.iv.size == IV_BYTES)
        return cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(
        name: String,
        encrypted: ByteArray,
    ): String {
        require(encrypted.size >= IV_BYTES + TAG_BYTES) { "Invalid encrypted auth record" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, encrypted.copyOfRange(0, IV_BYTES)))
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(encrypted.copyOfRange(IV_BYTES, encrypted.size)).toString(Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val TAG_BITS = 128
    }
}
