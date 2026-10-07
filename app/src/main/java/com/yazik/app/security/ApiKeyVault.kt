package com.yazik.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts the user's personal API key at rest with a key held by Android Keystore. */
class ApiKeyVault(context: Context) {
    private val preferences = context.getSharedPreferences("secure_api_settings", Context.MODE_PRIVATE)
    private val keyAlias = "yazik_api_key_aes"

    fun hasKey(): Boolean = preferences.contains(KEY_CIPHERTEXT)

    @Synchronized
    fun put(apiKey: String) {
        require(apiKey.isNotBlank()) { "Enter an API key first." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun get(): String? {
        val ivText = preferences.getString(KEY_IV, null) ?: return null
        val encryptedText = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
        )
        return String(Base64.decode(encryptedText, Base64.NO_WRAP).let(cipher::doFinal), Charsets.UTF_8)
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_IV = "api_iv"
        private const val KEY_CIPHERTEXT = "api_ciphertext"
    }
}
