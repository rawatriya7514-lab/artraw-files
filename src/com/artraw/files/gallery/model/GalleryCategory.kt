package com.artraw.files.gallery.model

enum class GalleryCategory(val id: String, val displayName: String) {
    ALL("all", "All Media"),
    PHOTOS("photos", "Photos"),
    VIDEOS("videos", "Videos"),
    SCREENSHOTS("screenshots", "Screenshots"),
    CAMERA("camera", "Camera"),
    DOWNLOADS("downloads", "Downloads"),
    DOCUMENTS("documents", "Documents"),
    FAVORITES("favorites", "Favorites"),
    COLLECTIONS("collections", "AI Collections"),
    DUPLICATES("duplicates", "Duplicates");

    companion object {
        fun fromId(id: String): GalleryCategory {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ALL
        }
    }
}
