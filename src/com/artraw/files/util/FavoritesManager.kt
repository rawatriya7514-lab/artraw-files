package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import com.artraw.files.model.FileItem
import java.io.File

/**
 * FavoritesManager — Bookmark important files/folders for quick access.
 * Stored in SharedPreferences as a pipe-delimited list of absolute paths.
 */
object FavoritesManager {

    private const val PREFS_NAME = "artraw_favorites_prefs"
    private const val KEY_FAVORITES = "favorite_paths"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getFavoritePaths(): Set<String> {
        val raw = prefs.getString(KEY_FAVORITES, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split("|").filter { it.isNotBlank() }.toSet()
    }

    private fun saveFavoritePaths(paths: Set<String>) {
        prefs.edit().putString(KEY_FAVORITES, paths.joinToString("|")).apply()
    }

    fun isFavorite(path: String): Boolean = path in getFavoritePaths()

    fun toggleFavorite(fileItem: FileItem): Boolean {
        val paths = getFavoritePaths().toMutableSet()
        val isNowFavorite = if (paths.contains(fileItem.path)) {
            paths.remove(fileItem.path)
            false
        } else {
            paths.add(fileItem.path)
            true
        }
        saveFavoritePaths(paths)
        return isNowFavorite
    }

    fun getFavoriteFiles(): List<FileItem> {
        val paths = getFavoritePaths()
        return paths.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) {
                val cat = MimeTypes.getCategory(file)
                val ext = MimeTypes.getExtension(file.name)
                FileItem(
                    file = file,
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    category = cat,
                    mimeType = if (file.isDirectory) "resource/folder" else MimeTypes.getMimeType(file),
                    extension = ext,
                    isFavorite = true
                )
            } else null
        }.sortedByDescending { it.lastModified }
    }

    fun clearAllFavorites() {
        prefs.edit().remove(KEY_FAVORITES).apply()
    }

    fun favoriteCount(): Int = getFavoritePaths().size
}
