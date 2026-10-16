package com.artraw.files.model

/**
 * AppStorageInfo — Information about an installed app's storage usage.
 */
data class AppStorageInfo(
    val packageName: String,
    val appName: String,
    val icon: Int = 0, // resource id placeholder; actual icon loaded via PackageManager
    val codeSize: Long = 0L,      // APK code size
    val dataSize: Long = 0L,      // App data size
    val cacheSize: Long = 0L,     // Cache size
    val totalSize: Long = 0L,     // Total = code + data + cache
    val versionName: String = "",
    val versionCode: Int = 0
) {
    val formattedSize: String
        get() = when {
            totalSize >= 1024L * 1024 * 1024 -> "%.2f GB".format(totalSize / (1024.0 * 1024.0 * 1024.0))
            totalSize >= 1024L * 1024 -> "%.2f MB".format(totalSize / (1024.0 * 1024.0))
            totalSize >= 1024L -> "%.2f KB".format(totalSize / 1024.0)
            else -> "$totalSize B"
        }
}
