package com.artraw.files.util

import com.artraw.files.model.ArchiveCapability
import com.artraw.files.model.ArchiveSupportLevel
import com.artraw.files.model.FileCategory
import java.io.File
import java.util.Locale

object MimeTypes {

    private val MIME_MAP = mapOf(
        // Documents
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "csv" to "text/csv",
        "rtf" to "application/rtf",
        "odt" to "application/vnd.oasis.opendocument.text",
        "ods" to "application/vnd.oasis.opendocument.spreadsheet",
        "odp" to "application/vnd.oasis.opendocument.presentation",
        "md" to "text/markdown",
        "json" to "application/json",
        "xml" to "application/xml",
        "html" to "text/html",
        "htm" to "text/html",
        "log" to "text/plain",

        // Archives
        "zip" to "application/zip",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "tgz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "rar" to "application/vnd.rar",

        // APKs
        "apk" to "application/vnd.android.package-archive",

        // Images
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "bmp" to "image/bmp",
        "heic" to "image/heic",

        // Videos
        "mp4" to "video/mp4",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "3gp" to "video/3gpp",

        // Audio
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "aac" to "audio/aac",
        "opus" to "audio/opus"
    )

    fun getMimeType(file: File): String {
        if (file.isDirectory) return "resource/folder"
        val ext = getExtension(file.name)
        return MIME_MAP[ext] ?: "application/octet-stream"
    }

    fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        if (lastDot == -1 || lastDot == fileName.length - 1) return ""
        return fileName.substring(lastDot + 1).lowercase(Locale.ROOT)
    }

    fun getCategory(file: File): FileCategory {
        if (file.isDirectory) return FileCategory.OTHER
        val ext = getExtension(file.name)
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic", "ico" -> FileCategory.IMAGES
            "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv" -> FileCategory.VIDEOS
            "mp3", "wav", "ogg", "flac", "m4a", "aac", "opus", "wma", "mid" -> FileCategory.AUDIO
            "pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "csv",
            "rtf", "odt", "ods", "odp", "md", "json", "xml", "html", "htm", "log", "epub" -> FileCategory.DOCUMENTS
            "zip", "7z", "tar", "gz", "tgz", "bz2", "xz", "rar", "jar" -> FileCategory.ARCHIVES
            "apk", "xapk", "apks" -> FileCategory.APKS
            else -> FileCategory.OTHER
        }
    }

    fun isPreviewableText(file: File): Boolean {
        val ext = getExtension(file.name)
        return ext in listOf("txt", "csv", "log", "md", "json", "xml", "html", "htm", "js", "ts", "kt", "java", "py", "sh", "properties", "gradle", "yaml", "yml", "ini", "conf")
    }

    /**
     * Reports archive format support capability honestly to the user.
     */
    fun getArchiveCapability(extension: String): ArchiveCapability {
        return when (extension.lowercase(Locale.ROOT)) {
            "zip" -> ArchiveCapability(
                format = "ZIP",
                supportLevel = ArchiveSupportLevel.FULLY_SUPPORTED,
                description = "Full internal viewing and extraction supported natively."
            )
            "gz", "tgz" -> ArchiveCapability(
                format = "GZ",
                supportLevel = ArchiveSupportLevel.FULLY_SUPPORTED,
                description = "GZIP decompression supported natively."
            )
            "tar" -> ArchiveCapability(
                format = "TAR",
                supportLevel = ArchiveSupportLevel.STREAM_SUPPORTED,
                description = "TAR archive stream structure inspection supported."
            )
            "7z" -> ArchiveCapability(
                format = "7Z",
                supportLevel = ArchiveSupportLevel.EXTERNAL_APP_NEEDED,
                description = "Proprietary LZMA 7-Zip format. Requires external utility (e.g. ZArchiver) for extraction."
            )
            "rar" -> ArchiveCapability(
                format = "RAR",
                supportLevel = ArchiveSupportLevel.EXTERNAL_APP_NEEDED,
                description = "Proprietary RAR format. Requires external utility (e.g. RAR / ZArchiver) for extraction."
            )
            else -> ArchiveCapability(
                format = extension.uppercase(Locale.ROOT),
                supportLevel = ArchiveSupportLevel.EXTERNAL_APP_NEEDED,
                description = "Unsupported archive format. Requires an external viewer app."
            )
        }
    }
}
