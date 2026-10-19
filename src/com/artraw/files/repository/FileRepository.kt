package com.artraw.files.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.artraw.files.model.AppStorageInfo
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
            var stat = StatFs(primary.absolutePath)
            var totalBytes = stat.totalBytes
            var freeBytes = stat.availableBytes
            if (totalBytes <= 0L) {
                try {
                    stat = StatFs(Environment.getDataDirectory().absolutePath)
                    totalBytes = stat.totalBytes
                    freeBytes = stat.availableBytes
                } catch (_: Throwable) {}
            }
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
            val isEncrypted = !isDir && ext == "artrawenc"
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
                    apkLabel = label,
                    isEncrypted = isEncrypted
                )
            )
        }

        items.sortWith { a, b -> sortOption.compare(a, b) }
        return items
    }

    private fun queryMediaStore(category: FileCategory, maxResults: Int): List<FileItem> {
        val uri = when (category) {
            FileCategory.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            FileCategory.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            FileCategory.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> null
        } ?: return emptyList()

        val items = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (c.moveToNext() && items.size < maxResults) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                        val size = if (sizeIdx != -1) {
                            val s = c.getLong(sizeIdx)
                            if (s > 0) s else file.length()
                        } else file.length()
                        val mod = if (modIdx != -1) {
                            val m = c.getLong(modIdx)
                            if (m > 0) m * 1000L else file.lastModified()
                        } else file.lastModified()
                        val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: MimeTypes.getMimeType(file) else MimeTypes.getMimeType(file)
                        val ext = MimeTypes.getExtension(name)

                        items.add(
                            FileItem(
                                file = file,
                                name = name,
                                path = file.absolutePath,
                                isDirectory = false,
                                size = size,
                                lastModified = mod,
                                category = category,
                                mimeType = mime,
                                extension = ext
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return items
    }

    private fun queryRecentMediaStore(limit: Int): List<FileItem> {
        val items = mutableListOf<FileItem>()
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE
            )
            val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            val cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (c.moveToNext() && items.size < limit) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        if (!file.isDirectory) {
                            val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                            val size = if (sizeIdx != -1) {
                                val s = c.getLong(sizeIdx)
                                if (s > 0) s else file.length()
                            } else file.length()
                            val mod = if (modIdx != -1) {
                                val m = c.getLong(modIdx)
                                if (m > 0) m * 1000L else file.lastModified()
                            } else file.lastModified()
                            val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: MimeTypes.getMimeType(file) else MimeTypes.getMimeType(file)
                            val ext = MimeTypes.getExtension(name)

                            items.add(
                                FileItem(
                                    file = file,
                                    name = name,
                                    path = file.absolutePath,
                                    isDirectory = false,
                                    size = size,
                                    lastModified = mod,
                                    category = MimeTypes.getCategory(file),
                                    mimeType = mime,
                                    extension = ext
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return items
    }

    fun getCategoryFiles(
        category: FileCategory,
        maxDepth: Int = 6,
        maxResults: Int = 500
    ): List<FileItem> {
        val resultMap = LinkedHashMap<String, FileItem>()

        // 1. Instant MediaStore query for Media categories (Images, Videos, Audio)
        if (category == FileCategory.IMAGES || category == FileCategory.VIDEOS || category == FileCategory.AUDIO) {
            val mediaItems = queryMediaStore(category, maxResults)
            for (item in mediaItems) {
                resultMap[item.path] = item
            }
        }

        // 2. Direct folder inspection for targeted standard directories
        val root = getPrimaryStorageRoot()
        val targetedDirs = when (category) {
            FileCategory.IMAGES -> listOf(
                File(root, "DCIM/Camera"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Pictures/Screenshots"),
                File(root, "Download")
            )
            FileCategory.VIDEOS -> listOf(
                File(root, "Movies"),
                File(root, "DCIM/Camera"),
                File(root, "DCIM"),
                File(root, "Download")
            )
            FileCategory.AUDIO -> listOf(
                File(root, "Music"),
                File(root, "Download"),
                File(root, "Podcasts"),
                File(root, "Ringtones")
            )
            FileCategory.DOWNLOADS -> listOf(
                File(root, "Download"),
                File(root, "Downloads")
            )
            else -> emptyList()
        }

        for (targetDir in targetedDirs) {
            if (targetDir.exists() && targetDir.isDirectory) {
                val directFiles = targetDir.listFiles() ?: continue
                for (f in directFiles) {
                    if (f.name.startsWith(".")) continue
                    if (!f.isDirectory) {
                        val cat = MimeTypes.getCategory(f)
                        if (category == FileCategory.DOWNLOADS || cat == category) {
                            if (!resultMap.containsKey(f.absolutePath)) {
                                resultMap[f.absolutePath] = mapToFileItem(f)
                            }
                        }
                    }
                }
            }
        }

        // 3. Fallback recursive scan if more items are needed
        if (resultMap.size < maxResults) {
            fun scanRecursive(dir: File, currentDepth: Int) {
                if (currentDepth > maxDepth || resultMap.size >= maxResults) return
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
                                if (!resultMap.containsKey(f.absolutePath)) {
                                    resultMap[f.absolutePath] = mapToFileItem(f)
                                }
                            }
                        } else if (cat == category) {
                            if (!resultMap.containsKey(f.absolutePath)) {
                                resultMap[f.absolutePath] = mapToFileItem(f)
                            }
                        }
                    }
                    if (resultMap.size >= maxResults) break
                }
            }

            scanRecursive(root, 1)
        }

        val results = resultMap.values.toMutableList()
        results.sortByDescending { it.lastModified }
        return results
    }

    fun getRecentFiles(limit: Int = 30): List<FileItem> {
        val resultMap = LinkedHashMap<String, FileItem>()

        // 1. Query MediaStore for recently added media/files
        val mediaRecent = queryRecentMediaStore(limit)
        for (item in mediaRecent) {
            resultMap[item.path] = item
        }

        // 2. Direct inspection of frequent creation directories
        val root = getPrimaryStorageRoot()
        val quickDirs = listOf(
            File(root, "Download"),
            File(root, "DCIM/Camera"),
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Documents")
        )
        for (d in quickDirs) {
            if (d.exists() && d.isDirectory) {
                val files = d.listFiles() ?: continue
                for (f in files) {
                    if (!f.isDirectory && !f.name.startsWith(".")) {
                        if (!resultMap.containsKey(f.absolutePath)) {
                            resultMap[f.absolutePath] = mapToFileItem(f)
                        }
                    }
                }
            }
        }

        // 3. Shallow storage scan
        fun scan(dir: File, depth: Int) {
            if (depth > 4 || resultMap.size > 200) return
            if (PathSecurity.isAndroidRestrictedStorage(dir)) return
            val files = dir.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    scan(f, depth + 1)
                } else {
                    if (!resultMap.containsKey(f.absolutePath)) {
                        resultMap[f.absolutePath] = mapToFileItem(f)
                    }
                }
            }
        }

        scan(root, 1)
        val allFiles = resultMap.values.toMutableList()
        allFiles.sortByDescending { it.lastModified }
        return allFiles.take(limit)
    }

    fun getLargeFiles(thresholdBytes: Long = 50L * 1024 * 1024, limit: Int = 50): List<FileItem> {
        val largeMap = LinkedHashMap<String, FileItem>()

        // 1. Instant MediaStore query for large files
        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE
            )
            val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
            val selectionArgs = arrayOf(thresholdBytes.toString())
            val sortOrder = "${MediaStore.MediaColumns.SIZE} DESC"
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (c.moveToNext() && largeMap.size < limit) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                        val size = if (sizeIdx != -1) c.getLong(sizeIdx) else file.length()
                        val mod = if (modIdx != -1) c.getLong(modIdx) * 1000L else file.lastModified()
                        val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: MimeTypes.getMimeType(file) else MimeTypes.getMimeType(file)
                        val ext = MimeTypes.getExtension(name)

                        largeMap[file.absolutePath] = FileItem(
                            file = file,
                            name = name,
                            path = file.absolutePath,
                            isDirectory = false,
                            size = size,
                            lastModified = mod,
                            category = MimeTypes.getCategory(file),
                            mimeType = mime,
                            extension = ext
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Direct folder scans for high-probability large files
        val root = getPrimaryStorageRoot()
        val targetedDirs = listOf(
            File(root, "Download"),
            File(root, "Movies"),
            File(root, "DCIM"),
            File(root, "DCIM/Camera"),
            File(root, "Pictures")
        )
        for (dir in targetedDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (!f.isDirectory && !f.name.startsWith(".") && f.length() >= thresholdBytes) {
                        if (!largeMap.containsKey(f.absolutePath)) {
                            largeMap[f.absolutePath] = mapToFileItem(f)
                        }
                    }
                }
            }
        }

        // 3. Fallback filesystem recursive scan if needed
        if (largeMap.size < limit) {
            fun scan(dir: File, depth: Int) {
                if (depth > 6 || largeMap.size >= limit) return
                if (PathSecurity.isAndroidRestrictedStorage(dir)) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        scan(f, depth + 1)
                    } else {
                        if (f.length() >= thresholdBytes) {
                            if (!largeMap.containsKey(f.absolutePath)) {
                                largeMap[f.absolutePath] = mapToFileItem(f)
                            }
                        }
                    }
                    if (largeMap.size >= limit) break
                }
            }

            scan(root, 1)
        }

        val largeList = largeMap.values.toMutableList()
        largeList.sortByDescending { it.size }
        return largeList.take(limit)
    }

    private fun mapToFileItem(file: File): FileItem {
        val isDir = file.isDirectory
        val ext = if (isDir) "" else MimeTypes.getExtension(file.name)
        val isApk = !isDir && ext == "apk"
        val isEncrypted = !isDir && ext == "artrawenc"

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
            apkLabel = label,
            isEncrypted = isEncrypted
        )
    }

    /**
     * Get installed apps with their storage usage (code + data + cache).
     * Requires PACKAGE_USAGE_STATS or queries PackageManager directly.
     */
    fun getInstalledAppsWithStorage(): List<AppStorageInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<AppStorageInfo>()
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in packages) {
            try {
                val pkgName = appInfo.packageName

                // Skip system apps if they have no label or are framework
                val appLabel = appInfo.loadLabel(pm)?.toString() ?: pkgName

                // Get package info for version
                var versionName = ""
                var versionCode = 0
                try {
                    val pkgInfo = pm.getPackageInfo(pkgName, 0)
                    versionName = pkgInfo.versionName ?: ""
                    versionCode = pkgInfo.versionCode
                } catch (e: Exception) {}

                // Get code size (APK size)
                var codeSize: Long = 0L
                var dataSize: Long = 0L
                var cacheSize: Long = 0L

                try {
                    val appFile = File(appInfo.sourceDir)
                    if (appFile.exists()) {
                        codeSize = appFile.length()
                    }
                } catch (e: Exception) {}

                try {
                    val dataDir = appInfo.dataDir
                    if (dataDir != null) {
                        val dataFile = File(dataDir)
                        if (dataFile.exists()) {
                            dataSize = dirSize(dataFile)
                        }
                    }
                } catch (e: Exception) {}

                try {
                    // Construct cache/data dir paths manually since ApplicationInfo.cacheDir may not be available
                    val pkgName = appInfo.packageName
                    val dataRoot = File("/data/data/$pkgName")
                    if (dataRoot.exists()) {
                        val cacheDir = File(dataRoot, "cache")
                        if (cacheDir.exists()) {
                            cacheSize = dirSize(cacheDir)
                        }
                    }
                } catch (e: Exception) {}

                val totalSize = codeSize + dataSize + cacheSize

                // Get icon resource id
                var iconRes: Int = 0
                try {
                    iconRes = appInfo.icon
                } catch (e: Exception) {}

                apps.add(AppStorageInfo(
                    packageName = pkgName,
                    appName = appLabel,
                    icon = iconRes,
                    codeSize = codeSize,
                    dataSize = dataSize,
                    cacheSize = cacheSize,
                    totalSize = totalSize,
                    versionName = versionName,
                    versionCode = versionCode
                ))
            } catch (e: Exception) {
                // Skip apps that can't be read
            }
        }

        // Sort by total size descending
        apps.sortByDescending { it.totalSize }
        return apps
    }

    /**
     * Recursively calculate directory size.
     */
    private fun dirSize(dir: File): Long {
        var size: Long = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            if (file.isDirectory) {
                size += dirSize(file)
            } else {
                size += file.length()
            }
        }
        return size
    }
}
