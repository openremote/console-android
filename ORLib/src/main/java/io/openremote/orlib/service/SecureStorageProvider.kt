package io.openremote.orlib.service

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageProvider(val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    private val keyAlias = context.packageName + ".secure_storage_key"
    private val mapper = jacksonObjectMapper()

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION =
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    }

    init {
        if (!isKeyExists()) {
            generateKey()

            // Migrate data from default shared preferences to secure storage
            // This is a one-time operation
            val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val allEntries = defaultSharedPreferences.all
            for ((key, value) in allEntries) {
                storeData(key, value as String?)
                defaultSharedPreferences.edit().remove(key).apply()
            }
        }
    }

    private fun getKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
    }

    private fun generateKey() {
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    private fun isKeyExists(): Boolean {
        val keyStore = getKeyStore()
        return keyStore.containsAlias(keyAlias)
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = getKeyStore()
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    private fun encryptData(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    fun initialize(): Map<String, Any> {
        return hashMapOf(
            "action" to "PROVIDER_INIT",
            "provider" to "storage",
            "version" to "1.0.0",
            "enabled" to true,
            "requiresPermission" to false,
            "hasPermission" to true,
            "success" to true,
        )
    }

    fun enable(): Map<String, Any> {
        return hashMapOf(
            "action" to "PROVIDER_ENABLE",
            "provider" to "storage",
            "hasPermission" to true,
            "success" to true,
        )
    }

    fun storeData(key: String?, data: String?) {
        if (key == null) return

        val editor = sharedPreferences.edit()
        if (data == null) {
            editor.remove(key)
        } else {
            val encryptedData = encryptData(data)
            editor.putString(key, encryptedData)
        }
        editor.apply()
    }

    fun retrieveData(key: String?): Map<String, Any?> {
        val result = hashMapOf(
            "action" to "RETRIEVE",
            "provider" to "storage",
            "key" to key,
            "value" to null as Any?,
        )

        val encryptedData = sharedPreferences.getString(key, null) ?: return result

        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        val iv = combined.copyOfRange(0, 12)
        val data = combined.copyOfRange(12, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        val plainData = cipher.doFinal(data).toString(Charsets.UTF_8)

        val value = try {
            mapper.readTree(plainData)
        } catch (e: JsonProcessingException) {
            plainData
        }

        result["value"] = value
        return result
    }
}