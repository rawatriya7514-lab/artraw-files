package com.artraw.files.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.model.SortOption
import com.artraw.files.model.StorageStats
import com.artraw.files.util.MimeTypes
import com.artraw.files.util.PathSecurity
import java.io.File

class FileRepository(private val context: Context) {

    fun getPrimaryStorageRoot(): File {
        return Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }

    fun getStorageStats(): List<StorageStats> {
        val list = mutableListOf<StorageStats>()
        val primary = getPrimaryStorageRoot()

        try {
            val stat = StatFs(primary.absolutePath)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            list.add(
                StorageStats(
                    rootPath = primary.absolutePath,
                    name = "Internal Storage",
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    isExternalSd = false
                )
            )
        } catch (e: Exception) {
            list.add(
                StorageStats(
                    rootPath = primary.absolutePath,
                    name = "Internal Storage",
                    totalBytes = 0L,
                    freeBytes = 0L,
                    isExternalSd = false
                )
            )
        }

        // Secondary SD cards if present
        try {
            val extFiles = context.getExternalFilesDirs(null)
            for (dir in extFiles) {
                if (dir != null && !dir.absolutePath.startsWith(primary.absolutePath)) {
                    val root = dir.absolutePath.split("/Android/")[0]
                    val stat = StatFs(root)
                    list.add(
                        StorageStats(
                            rootPath = root,
                            name = "SD Card",
                            totalBytes = stat.totalBytes,
                            freeBytes = stat.availableBytes,
                            isExternalSd = true
                        )
                    )
                }
            }
        } catch (ignored: Exception) {
        }

        return list
    }

    fun listFiles(
        directory: File,
        showHidden: Boolean = false,
        sortOption: SortOption = SortOption()
    ): List<FileItem> {
        if (!directory.exists() || !directory.canRead()) {
            return emptyList()
        }

        val rawFiles = directory.listFiles() ?: return emptyList()
        val items = mutableListOf<FileItem>()

        for (file in rawFiles) {
            if (!showHidden && file.name.startsWith(".")) {
                continue
            }

            val isDir = file.isDirectory
            val cat = if (isDir) FileCategory.OTHER else MimeTypes.getCategory(file)
            val ext = if (isDir) "" else MimeTypes.getExtension(file.name)
            val mime = if (isDir) "resource/folder" else MimeTypes.getMimeType(file)
            val childCount = if (isDir) {
                try {
                    file.list()?.size ?: 0
                } catch (e: Exception) {
                    0
                }
            } else null

            val isApk = !isDir && ext == "apk"
            var pkgName: String? = null
            var verName: String? = null
            var label: String? = null

            if (isApk) {
                try {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(
                        file.absolutePath,
                        PackageManager.GET_ACTIVITIES
                    )
                    if (pkgInfo != null) {
                        pkgName = pkgInfo.packageName
                        verName = pkgInfo.versionName
                        pkgInfo.applicationInfo?.sourceDir = file.absolutePath
                        pkgInfo.applicationInfo?.publicSourceDir = file.absolutePath
                        label = pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()
                    }
                } catch (ignored: Exception) {
                }
            }

            items.add(
                FileItem(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = isDir,
                    size = if (isDir) 0L else file.length(),
                    lastModified = file.lastModified(),
                    category = cat,
                    mimeType = mime,
                    extension = ext,
                    childCount = childCount,
                    isApk = isApk,
                    apkPackageName = pkgName,
                    apkVersionName = verName,
                    apkLabel = label
                )
            )
        }

        items.sortWith { a, b -> sortOption.compare(a, b) }
        return items
    }

    fun getCategoryFiles(
        category: FileCategory,
        maxDepth: Int = 4,
        maxResults: Int = 500
    ): List<FileItem> {
        val root = getPrimaryStorageRoot()
        val results = mutableListOf<FileItem>()

        fun scanRecursive(dir: File, currentDepth: Int) {
            if (currentDepth > maxDepth || results.size >= maxResults) return
            if (PathSecurity.isAndroidRestrictedStorage(dir)) return

            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scanRecursive(f, currentDepth + 1)
                } else {
                    val cat = MimeTypes.getCategory(f)
                    if (category == FileCategory.DOWNLOADS) {
                        if (f.absolutePath.contains("/Download/")) {
                            results.add(mapToFileItem(f))
                        }
                    } else if (cat == category) {
                        results.add(mapToFileItem(f))
                    }
                }
                if (results.size >= maxResults) break
            }
        }

        scanRecursive(root, 1)
        results.sortByDescending { it.lastModified }
        return results
    }

    fun getRecentFiles(limit: Int = 30): List<FileItem> {
        val root = getPrimaryStorageRoot()
        val allFiles = mutableListOf<FileItem>()

        fun scan(dir: File, depth: Int) {
            if (depth > 3 || allFiles.size > 200) return
            if (PathSecurity.isAndroidRestrictedStorage(dir)) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, depth + 1)
                } else {
                    allFiles.add(mapToFileItem(f))
                }
            }
        }

        scan(root, 1)
        allFiles.sortByDescending { it.lastModified }
        return allFiles.take(limit)
    }

    fun getLargeFiles(thresholdBytes: Long = 50L * 1024 * 1024, limit: Int = 50): List<FileItem> {
        val root = getPrimaryStorageRoot()
        val largeList = mutableListOf<FileItem>()

        fun scan(dir: File, depth: Int) {
            if (depth > 5) return
            if (PathSecurity.isAndroidRestrictedStorage(dir)) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, depth + 1)
                } else {
                    if (f.length() >= thresholdBytes) {
                        largeList.add(mapToFileItem(f))
                    }
                }
            }
        }

        scan(root, 1)
        largeList.sortByDescending { it.size }
        return largeList.take(limit)
    }

    private fun mapToFileItem(file: File): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else MimeTypes.getExtension(file.name)
        val isApk = !isDir && ext == "apk"

        var pkgName: String? = null
        var verName: String? = null
        var label: String? = null

        if (isApk) {
            try {
                val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                if (pkgInfo != null) {
                    pkgName = pkgInfo.packageName
                    verName = pkgInfo.versionName
                    pkgInfo.applicationInfo?.sourceDir = file.absolutePath
                    pkgInfo.applicationInfo?.publicSourceDir = file.absolutePath
                    label = pkgInfo.applicationInfo?.loadLabel(context.packageManager)?.toString()
                }
            } catch (ignored: Exception) {
            }
        }

        return FileItem(
            file = file,
            name = file.name,
            path = file.absolutePath,
            isDirectory = isDir,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            category = if (isDir) FileCategory.OTHER else MimeTypes.getCategory(file),
            mimeType = if (isDir) "resource/folder" else MimeTypes.getMimeType(file),
            extension = ext,
            isApk = isApk,
            apkPackageName = pkgName,
            apkVersionName = verName,
            apkLabel = label
        )
    }
}
