package com.artraw.files.gallery.model

enum class GalleryCategory(val id: String, val displayName: String) {
    ALL("all", "All Media"),
    PHOTOS("photos", "Photos"),
    VIDEOS("videos", "Videos"),
    AUDIO("audio", "Audio & MP3"),
    DOCUMENTS("documents", "Documents"),
    SCREENSHOTS("screenshots", "Screenshots"),
    CAMERA("camera", "Camera"),
    DOWNLOADS("downloads", "Downloads"),
    FAVORITES("favorites", "Favorites"),
    RAW_ART("raw_art", "RAW & Fine Art"),
    VAULT("vault", "Vault"),
    COLLECTIONS("collections", "AI Collections"),
    DUPLICATES("duplicates", "Duplicates");

    companion object {
        fun fromId(id: String): GalleryCategory {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
        }
    }
}
