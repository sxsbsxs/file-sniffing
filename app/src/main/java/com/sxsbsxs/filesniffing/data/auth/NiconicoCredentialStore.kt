package com.sxsbsxs.filesniffing.data.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

data class NiconicoCredentials(
    val username: String,
    val password: String
)

/**
 * Stores Niconico credentials encrypted with a non-exportable Android Keystore AES key.
 * The password is only decrypted for the duration of a local yt-dlp operation.
 */
@Singleton
class NiconicoCredentialStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): NiconicoCredentials? {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null) ?: return null

        return runCatching {
            val iv = Base64.decode(encodedIv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plaintext = cipher.doFinal(ciphertext)
            DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
                NiconicoCredentials(
                    username = input.readUTF(),
                    password = input.readUTF()
                )
            }
        }.getOrNull()
    }

    @Synchronized
    fun save(username: String, password: String) {
        val normalizedUsername = username.trim()
        require(normalizedUsername.isNotEmpty()) { "请输入 Niconico 账号" }
        require(password.isNotEmpty()) { "请输入 Niconico 密码" }
        NiconicoNetrcFormatter.validate(normalizedUsername)
        NiconicoNetrcFormatter.validate(password)

        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeUTF(normalizedUsername)
                output.writeUTF(password)
            }
            bytes.toByteArray()
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload)
        val committed = preferences.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .commit()
        check(committed) { "无法保存 Niconico 账号信息" }
    }

    @Synchronized
    fun clear() {
        val committed = preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_CIPHERTEXT)
            .commit()
        check(committed) { "无法移除 Niconico 账号信息" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "niconico_credentials_encrypted"
        const val KEY_IV = "iv"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_ALIAS = "file_sniffing_niconico_credentials_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/** Minimal formatter for the temporary yt-dlp netrc file. */
internal object NiconicoNetrcFormatter {
    fun format(username: String, password: String): String {
        validate(username)
        validate(password)
        return "machine niconico login " + quote(username) + " password " + quote(password) + "\n"
    }

    fun validate(value: String) {
        require(value.isNotEmpty()) { "账号或密码不能为空" }
        require(value.none { it == '\r' || it == '\n' || it.code < 0x20 || it.code == 0x7f }) {
            "账号或密码包含不支持的控制字符"
        }
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
