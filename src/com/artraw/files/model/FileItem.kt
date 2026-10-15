package com.artraw.files.model

import java.io.File

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isDirectory) 0L else file.length(),
    val lastModified: Long = file.lastModified(),
    val category: FileCategory = FileCategory.OTHER,
    val mimeType: String = "application/octet-stream",
    val extension: String = "",
    val childCount: Int? = null,
    val isFavorite: Boolean = false,
    val isSelected: Boolean = false,
    val isCutPending: Boolean = false,
    val isApk: Boolean = false,
    val apkPackageName: String? = null,
    val apkVersionName: String? = null,
    val apkLabel: String? = null,
    val isEncrypted: Boolean = false
)
