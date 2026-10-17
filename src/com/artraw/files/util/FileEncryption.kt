package com.artraw.files.util

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * FileEncryption — AES-256-GCM based file encryption for sensitive files.
 *
 * Encrypts user-selected files with a per-file random key wrapped by a
 * device-bound master key stored in SharedPreferences.
 * Encrypted files get `.artrawenc` extension and can only be decrypted
 * by this app on this device.
 */
object FileEncryption {

    private const val ENCRYPTED_EXT = ".artrawenc"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    private val random = SecureRandom()

    private lateinit var appContext: Context
    private var masterKey: SecretKey? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Returns true if the file is already encrypted (has .artrawenc extension).
     */
    fun isEncrypted(file: File): Boolean {
        val name = file.name
        return name.endsWith(ENCRYPTED_EXT, ignoreCase = true)
    }

    /**
     * Loads existing master key from SharedPreferences or creates a new one.
     */
    private fun loadOrCreateMasterKey(): SecretKey {
        if (masterKey != null) return masterKey!!

        val prefs = appContext.getSharedPreferences("artraw_encryption_prefs", Context.MODE_PRIVATE)
        val encodedKey = prefs.getString("master_key", null)

        val key: SecretKey = if (encodedKey != null) {
            val bytes = java.util.Base64.getDecoder().decode(encodedKey)
            javax.crypto.spec.SecretKeySpec(bytes, KEY_ALGORITHM)
        } else {
            val kg = KeyGenerator.getInstance(KEY_ALGORITHM)
            kg.init(KEY_SIZE)
            val newKey = kg.generateKey()
            val encoded = java.util.Base64.getEncoder().encodeToString(newKey.encoded)
            prefs.edit().putString("master_key", encoded).apply()
            newKey
        }

        masterKey = key
        return key
    }

    /**
     * Encrypts a file: reads original, writes .artrawenc, deletes original.
     * Returns the encrypted File on success.
     */
    fun encryptFileWithContext(file: File): File? {
        if (!file.exists() || file.isDirectory || isEncrypted(file)) return null

        val encryptedFile = File(file.parentFile, file.name + ENCRYPTED_EXT)
        var input: FileInputStream? = null
        var output: FileOutputStream? = null

        return try {
            val key = loadOrCreateMasterKey()
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)

            val iv = cipher.iv
            input = FileInputStream(file)
            output = FileOutputStream(encryptedFile)

            // Write IV length + IV first
            output.write(iv.size)
            output.write(iv)

            val buffer = ByteArray(64 * 1024)
            var read: Int
            var isFirstChunk = true
            while (input.read(buffer).also { read = it } != -1) {
                val chunk = if (read == buffer.size) buffer else buffer.copyOfRange(0, read)
                if (read < buffer.size) {
                    // Last chunk — finalize GCM
                    output.write(cipher.doFinal(chunk))
                } else if (isFirstChunk) {
                    // First chunk — initialize with update
                    output.write(cipher.update(chunk))
                    isFirstChunk = false
                } else {
                    // Middle chunks — continue update
                    output.write(cipher.update(chunk))
                }
            }
            output.flush()

            // Delete original only after successful write
            file.delete()
            encryptedFile
        } catch (e: Exception) {
            encryptedFile.delete()
            null
        } finally {
            input?.close()
            output?.close()
        }
    }

    /**
     * Decrypts a .artrawenc file back to its original name.
     * Returns the decrypted File on success.
     */
    fun decryptFileWithContext(encryptedFile: File): File? {
        if (!encryptedFile.exists() || !isEncrypted(encryptedFile)) return null

        val originalName = removeEncSuffix(encryptedFile.name)
        val decryptedFile = File(encryptedFile.parentFile, originalName)

        var input: FileInputStream? = null
        var output: FileOutputStream? = null

        return try {
            val key = loadOrCreateMasterKey()
            val cipher = Cipher.getInstance(ALGORITHM)

            input = FileInputStream(encryptedFile)

            // Read IV length + IV
            val ivLen = input.read()
            if (ivLen == -1) throw Exception("Corrupted encrypted file")
            val iv = ByteArray(ivLen)
            var totalRead = 0
            while (totalRead < ivLen) {
                val r = input.read(iv, totalRead, ivLen - totalRead)
                if (r == -1) throw Exception("Corrupted encrypted file")
                totalRead += r
            }

            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            output = FileOutputStream(decryptedFile)
            val buffer = ByteArray(64 * 1024)
            var read: Int
            var isFirstChunk = true
            while (input.read(buffer).also { read = it } != -1) {
                val chunk = if (read == buffer.size) buffer else buffer.copyOfRange(0, read)
                if (read < buffer.size) {
                    // Last chunk — finalize GCM
                    output.write(cipher.doFinal(chunk))
                } else if (isFirstChunk) {
                    // First chunk — initialize with update
                    output.write(cipher.update(chunk))
                    isFirstChunk = false
                } else {
                    // Middle chunks — continue update
                    output.write(cipher.update(chunk))
                }
            }
            output.flush()

            // Delete encrypted file only after successful decryption
            encryptedFile.delete()
            decryptedFile
        } catch (e: Exception) {
            decryptedFile.delete()
            null
        } finally {
            input?.close()
            output?.close()
        }
    }

    /**
     * Removes the .artrawenc suffix case-insensitively.
     */
    private fun removeEncSuffix(name: String): String {
        val extLower = ENCRYPTED_EXT.lowercase()
        val nameLower = name.lowercase()
        if (nameLower.endsWith(extLower)) {
            return name.substring(0, name.length - ENCRYPTED_EXT.length)
        }
        return name
    }
}
