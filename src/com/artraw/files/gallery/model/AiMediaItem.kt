package com.artraw.files.gallery.model

import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import java.io.File

data class AiMediaItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val size: Long = file.length(),
    val dateModified: Long = file.lastModified(),
    val dateTaken: Long = file.lastModified(),
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "image/jpeg",
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isDocument: Boolean = false,
    val durationMs: Long = 0L,
    val category: String = "photos",
    val phash: Long = 0L,
    val dominantColor: String = "UNKNOWN",
    val colorHex: String = "#888888",
    val blurScore: Double = 0.0,
    val isBlurry: Boolean = false,
    val aestheticScore: Double = 50.0,
    val detectedTags: List<String> = emptyList(),
    val ocrText: String = "",
    val description: String = "",
    var isFavorite: Boolean = false,
    var isSelected: Boolean = false
) {
    fun toFileItem(): FileItem {
        val cat = when {
            isAudio -> FileCategory.AUDIO
            isVideo -> FileCategory.VIDEOS
            isDocument -> FileCategory.DOCUMENTS
            else -> FileCategory.IMAGES
        }
        return FileItem(
            file = file,
            name = name,
            path = path,
            isDirectory = false,
            size = size,
            lastModified = dateModified,
            category = cat,
            mimeType = mimeType,
            isFavorite = isFavorite,
            isSelected = isSelected
        )
    }

    fun formattedDuration(): String {
        if (durationMs <= 0) return ""
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
