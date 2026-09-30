package com.sandnes.familyapp.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidSecretStore(
    context: Context,
) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences("encrypted_auth_v1", Context.MODE_PRIVATE)
    private val cipher = AuthCipher(::encryptionKey)

    override suspend fun read(name: String): String? =
        withContext(Dispatchers.IO) {
            preferences.getString(name, null)?.let { cipher.decrypt(name, Base64.decode(it, Base64.NO_WRAP)) }
        }

    override suspend fun write(
        name: String,
        value: String,
    ) =
        withContext(Dispatchers.IO) {
            val encrypted = Base64.encodeToString(cipher.encrypt(name, value), Base64.NO_WRAP)
            check(preferences.edit().putString(name, encrypted).commit()) { "Unable to persist encrypted auth" }
        }

    override suspend fun remove(name: String) =
        withContext(Dispatchers.IO) {
            check(preferences.edit().remove(name).commit()) { "Unable to clear encrypted auth" }
        }

    @Synchronized
    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "family_auth_v1"
        const val KEY_BITS = 256
    }
}
