package com.artraw.files.util

import android.content.Context
import com.artraw.files.model.FileItem
import com.artraw.files.model.FileCategory
import java.io.File

/**
 * JunkCleaner — Scans for cache files, temp files, APK installers, duplicate
 * large files, and empty folders that can be safely cleaned.
 */
object JunkCleaner {

    data class JunkCategory(
        val name: String,
        val items: List<FileItem>,
        val totalSize: Long,
        val description: String
    )

    private val cacheExtensions = listOf(".cache", ".tmp", ".temp", ".log", ".bak")
    private val apkExtensions = listOf(".apk", ".xapk", ".apks")

    fun scanJunk(context: Context): List<JunkCategory> {
        val root = context.getExternalFilesDir(null)?.parentFile?.parentFile ?: File("/storage/emulated/0")

        var cacheItems = mutableListOf<FileItem>()
        var apkItems = mutableListOf<FileItem>()
        var tempItems = mutableListOf<FileItem>()
        var emptyFolders = mutableListOf<FileItem>()

        scanDirectory(root, cacheItems, apkItems, tempItems, emptyFolders)

        val categories = mutableListOf<JunkCategory>()

        if (cacheItems.isNotEmpty()) {
            categories.add(JunkCategory(
                name = "App Cache Files",
                items = cacheItems,
                totalSize = cacheItems.sumOf { it.size },
                description = "Temporary app cache files safe to delete"
            ))
        }
        if (apkItems.isNotEmpty()) {
            categories.add(JunkCategory(
                name = "APK Installers",
                items = apkItems,
                totalSize = apkItems.sumOf { it.size },
                description = "Old APK installer files taking space"
            ))
        }
        if (tempItems.isNotEmpty()) {
            categories.add(JunkCategory(
                name = "Temporary Files",
                items = tempItems,
                totalSize = tempItems.sumOf { it.size },
                description = "Temp and log files safe to remove"
            ))
        }
        if (emptyFolders.isNotEmpty()) {
            categories.add(JunkCategory(
                name = "Empty Folders",
                items = emptyFolders,
                totalSize = 0,
                description = "Empty directories with no content"
            ))
        }

        return categories
    }

    private fun scanDirectory(
        dir: File,
        cacheItems: MutableList<FileItem>,
        apkItems: MutableList<FileItem>,
        tempItems: MutableList<FileItem>,
        emptyFolders: MutableList<FileItem>
    ) {
        val files = dir.listFiles() ?: return
        var hasContent = false

        for (f in files) {
            if (f.isDirectory) {
                hasContent = true
                scanDirectory(f, cacheItems, apkItems, tempItems, emptyFolders)
                // Check if now empty
                if (f.listFiles()?.isEmpty() == true) {
                    emptyFolders.add(FileItem(
                        file = f, name = f.name, path = f.absolutePath,
                        isDirectory = true, size = 0L, lastModified = f.lastModified(),
                        category = FileCategory.OTHER, mimeType = "resource/folder",
                        extension = ""
                    ))
                }
            } else {
                hasContent = true
                val name = f.name.lowercase()
                val size = f.length()
                val cat = MimeTypes.getCategory(f)

                if (name.endsWith(".cache") || name.endsWith(".tmp") || name.endsWith(".temp") ||
                    name.endsWith(".log") || name.endsWith(".bak")) {
                    tempItems.add(FileItem(
                        file = f, name = f.name, path = f.absolutePath,
                        isDirectory = false, size = size, lastModified = f.lastModified(),
                        category = cat, mimeType = MimeTypes.getMimeType(f),
                        extension = MimeTypes.getExtension(f.name)
                    ))
                } else if (name.endsWith(".apk") || name.endsWith(".xapk") || name.endsWith(".apks")) {
                    apkItems.add(FileItem(
                        file = f, name = f.name, path = f.absolutePath,
                        isDirectory = false, size = size, lastModified = f.lastModified(),
                        category = cat, mimeType = MimeTypes.getMimeType(f),
                        extension = MimeTypes.getExtension(f.name)
                    ))
                } else if (cacheItems.size < 500) {
                    cacheItems.add(FileItem(
                        file = f, name = f.name, path = f.absolutePath,
                        isDirectory = false, size = size, lastModified = f.lastModified(),
                        category = cat, mimeType = MimeTypes.getMimeType(f),
                        extension = MimeTypes.getExtension(f.name)
                    ))
                }
            }
        }
    }

    fun deleteItems(items: List<FileItem>): Int {
        var count = 0
        for (item in items) {
            val f = File(item.path)
            if (f.exists()) {
                if (f.isDirectory) {
                    if (deleteRecursive(f)) count++
                } else if (f.delete()) count++
            }
        }
        return count
    }

    private fun deleteRecursive(dir: File): Boolean {
        val files = dir.listFiles() ?: return dir.delete()
        for (f in files) {
            if (f.isDirectory) deleteRecursive(f) else if (!f.delete()) return false
        }
        return dir.delete()
    }
}
