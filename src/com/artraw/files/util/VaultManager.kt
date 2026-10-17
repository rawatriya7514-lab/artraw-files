package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import com.artraw.files.model.FileItem
import java.io.File

/**
 * VaultManager — Secure hidden folder. Files moved to vault are renamed with
 * .artvault extension and stored in app-private directory. Protected by PIN.
 */
object VaultManager {

    private const val PREFS_NAME = "artraw_vault_prefs"
    private const val KEY_VAULT_PIN = "vault_pin"
    private const val KEY_VAULT_ITEMS = "vault_items"
    private const val KEY_VAULT_LOCKED = "vault_locked"

    private lateinit var prefs: SharedPreferences
    private lateinit var vaultRoot: File

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        vaultRoot = File(context.applicationContext.filesDir, "vault")
        vaultRoot.mkdirs()
    }

    data class VaultItem(
        val originalPath: String,
        val originalName: String,
        val vaultPath: String,
        val size: Long,
        val addedAt: Long
    )

    private fun getVaultList(): List<VaultItem> {
        val raw = prefs.getString(KEY_VAULT_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotBlank() }.map { entry ->
            val parts = entry.split(":::")
            if (parts.size == 5) {
                VaultItem(parts[0], parts[1], parts[2], parts[3].toLong(), parts[4].toLong())
            } else null
        }.filterNotNull()
    }

    private fun saveVaultList(list: List<VaultItem>) {
        val sb = StringBuilder()
        for ((i, item) in list.withIndex()) {
            if (i > 0) sb.append("|")
            sb.append("${item.originalPath}:::${item.originalName}:::${item.vaultPath}:::${item.size}:::${item.addedAt}")
        }
        prefs.edit().putString(KEY_VAULT_ITEMS, sb.toString()).apply()
    }

    fun isVaultEnabled(): Boolean = prefs.getBoolean(KEY_VAULT_LOCKED, false)

    fun setVaultLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_VAULT_LOCKED, locked).apply()
    }

    fun setPin(pin: String) {
        prefs.edit().putString(KEY_VAULT_PIN, pin).apply()
    }

    fun hasPin(): Boolean {
        val pin = prefs.getString(KEY_VAULT_PIN, null)
        return pin != null && pin.isNotEmpty()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_VAULT_PIN, null)
        return stored != null && stored == pin
    }

    fun moveToVault(file: File): Boolean {
        if (!file.exists() || file.isDirectory) return false
        val vaultFile = File(vaultRoot, System.currentTimeMillis().toString() + "_" + file.name + ".artvault")
        val success = file.renameTo(vaultFile)
        if (success) {
            val list = getVaultList().toMutableList()
            list.add(VaultItem(
                originalPath = file.absolutePath,
                originalName = file.name,
                vaultPath = vaultFile.absolutePath,
                size = file.length(),
                addedAt = System.currentTimeMillis()
            ))
            saveVaultList(list)
        }
        return success
    }

    fun restoreFromVault(vaultPath: String): Boolean {
        val list = getVaultList().toMutableList()
        val item = list.find { it.vaultPath == vaultPath } ?: return false
        val vaultFile = File(vaultPath)
        var target = File(item.originalPath)
        if (target.exists()) {
            val base = item.originalName
            val dot = base.lastIndexOf('.')
            val ext = if (dot > 0) base.substring(dot) else ""
            val name = base.removeSuffix(ext)
            target = File(target.parentFile, name + "_restored" + ext)
        }
        val success = vaultFile.renameTo(target)
        if (success) {
            list.remove(item)
            saveVaultList(list)
        }
        return success
    }

    fun deleteFromVault(vaultPath: String): Boolean {
        val list = getVaultList().toMutableList()
        val item = list.find { it.vaultPath == vaultPath } ?: return false
        val file = File(vaultPath)
        val success = file.delete()
        if (success) {
            list.remove(item)
            saveVaultList(list)
        }
        return success
    }

    fun getVaultItems(): List<VaultItem> = getVaultList()
    fun vaultSize(): Long = getVaultList().sumOf { it.size }
    fun vaultCount(): Int = getVaultList().size
}
