package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import com.artraw.files.model.FileItem
import java.io.File

/**
 * TrashManager — Soft-delete system. Deleted files move to a trash folder
 * instead of permanent deletion. User can Restore or Empty Trash.
 */
object TrashManager {

    private const val PREFS_NAME = "artraw_trash_prefs"
    private const val KEY_TRASH_ITEMS = "trash_items"

    private lateinit var prefs: SharedPreferences
    private lateinit var trashRoot: File

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        trashRoot = File(context.applicationContext.cacheDir, "artraw_trash")
        trashRoot.mkdirs()
    }

    data class TrashedItem(
        val originalPath: String,
        val originalName: String,
        val trashedPath: String,
        val trashedAt: Long,
        val size: Long,
        val isDirectory: Boolean
    )

    private fun getTrashedList(): List<TrashedItem> {
        val raw = prefs.getString(KEY_TRASH_ITEMS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|").filter { it.isNotBlank() }.map { entry ->
            val parts = entry.split(":::")
            if (parts.size == 6) {
                TrashedItem(parts[0], parts[1], parts[2], parts[3].toLong(), parts[4].toLong(), parts[5] == "true")
            } else null
        }.filterNotNull()
    }

    private fun saveTrashedList(list: List<TrashedItem>) {
        val sb = StringBuilder()
        for ((i, item) in list.withIndex()) {
            if (i > 0) sb.append("|")
            sb.append("${item.originalPath}:::${item.originalName}:::${item.trashedPath}:::${item.trashedAt}:::${item.size}:::${item.isDirectory}")
        }
        prefs.edit().putString(KEY_TRASH_ITEMS, sb.toString()).apply()
    }

    fun moveToTrash(file: File): Boolean {
        if (!file.exists()) return false
        val trashedFile = File(trashRoot, System.currentTimeMillis().toString() + "_" + file.name)
        val success = file.renameTo(trashedFile)
        if (success) {
            val list = getTrashedList().toMutableList()
            list.add(TrashedItem(
                originalPath = file.absolutePath,
                originalName = file.name,
                trashedPath = trashedFile.absolutePath,
                trashedAt = System.currentTimeMillis(),
                size = if (file.isDirectory) 0L else file.length(),
                isDirectory = file.isDirectory
            ))
            saveTrashedList(list)
        }
        return success
    }

    fun restoreItem(trashedPath: String): Boolean {
        val list = getTrashedList().toMutableList()
        val item = list.find { it.trashedPath == trashedPath } ?: return false
        val trashedFile = File(trashedPath)
        val originalFile = File(item.originalPath)

        // If original path exists, add suffix
        var target = originalFile
        if (target.exists()) {
            val base = item.originalName
            val ext = if (item.isDirectory) "" else {
                val dot = base.lastIndexOf('.')
                if (dot > 0) base.substring(dot) else ""
            }
            val name = if (item.isDirectory) base else base.removeSuffix(ext)
            target = File(originalFile.parentFile, name + "_restored" + ext)
        }

        val success = trashedFile.renameTo(target)
        if (success) {
            list.remove(item)
            saveTrashedList(list)
        }
        return success
    }

    fun deletePermanently(trashedPath: String): Boolean {
        val list = getTrashedList().toMutableList()
        val item = list.find { it.trashedPath == trashedPath } ?: return false
        val file = File(trashedPath)
        val success = if (file.isDirectory) deleteRecursive(file) else file.delete()
        if (success) {
            list.remove(item)
            saveTrashedList(list)
        }
        return success
    }

    private fun deleteRecursive(dir: File): Boolean {
        val files = dir.listFiles() ?: return dir.delete()
        for (f in files) {
            if (f.isDirectory) deleteRecursive(f) else if (!f.delete()) return false
        }
        return dir.delete()
    }

    fun getTrashItems(): List<TrashedItem> = getTrashedList()

    fun emptyTrash(): Int {
        val list = getTrashedList()
        var count = 0
        for (item in list) {
            val f = File(item.trashedPath)
            if (f.exists()) {
                if (deleteRecursiveIfNeeded(f)) count++
            }
        }
        prefs.edit().remove(KEY_TRASH_ITEMS).apply()
        return count
    }

    private fun deleteRecursiveIfNeeded(dir: File): Boolean {
        val files = dir.listFiles() ?: return dir.delete()
        for (f in files) {
            if (f.isDirectory) deleteRecursiveIfNeeded(f) else if (!f.delete()) return false
        }
        return dir.delete()
    }

    fun trashSize(): Long {
        var total = 0L
        for (item in getTrashedList()) total += item.size
        return total
    }

    fun trashCount(): Int = getTrashedList().size
}
