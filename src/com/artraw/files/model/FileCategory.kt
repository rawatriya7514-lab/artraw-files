package com.artraw.files.model

enum class FileCategory(val id: String) {
    IMAGES("images"),
    VIDEOS("videos"),
    AUDIO("audio"),
    DOCUMENTS("documents"),
    DOWNLOADS("downloads"),
    ARCHIVES("archives"),
    APKS("apks"),
    OTHER("other");

    companion object {
        fun fromId(id: String): FileCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER
        }
    }
}

enum class ArchiveSupportLevel {
    FULLY_SUPPORTED,    // ZIP, GZ (internal unpack/inspect)
    STREAM_SUPPORTED,   // TAR
    EXTERNAL_APP_NEEDED // 7Z, RAR (honestly reported)
}

data class ArchiveCapability(
    val format: String,
    val supportLevel: ArchiveSupportLevel,
    val description: String
)
