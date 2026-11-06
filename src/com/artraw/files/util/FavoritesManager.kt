package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import com.artraw.files.ArtfilesApp
import com.artraw.files.model.FileItem
import com.artraw.files.model.FileCategory
import java.io.File

/**
 * FavoritesManager — Bookmark important files/folders for quick access.
 * Stored in SharedPreferences as a pipe-delimited list of absolute paths.
 * 100% crash-safe with auto-initialization.
 */
object FavoritesManager {

    private const val PREFS_NAME = "artraw_favorites_prefs"
    private const val KEY_FAVORITES = "favorite_paths"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            synchronized(this) {
                if (prefs == null) {
                    prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                }
            }
        }
    }

    private fun getPrefs(): SharedPreferences? {
        if (prefs != null) return prefs
        synchronized(this) {
            if (prefs != null) return prefs
            val ctx = try { ArtfilesApp.instance } catch (_: Throwable) { null }
            if (ctx != null) {
                prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
            return prefs
        }
    }

    private fun getFavoritePaths(): Set<String> {
        val p = getPrefs() ?: return emptySet()
        val raw = p.getString(KEY_FAVORITES, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split("|").filter { it.isNotBlank() }.toSet()
    }

    private fun saveFavoritePaths(paths: Set<String>) {
        val p = getPrefs() ?: return
        p.edit().putString(KEY_FAVORITES, paths.joinToString("|")).apply()
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
                    mimeType = MimeTypes.getMimeType(file),
                    category = cat,
                    extension = ext,
                    isFavorite = true
                )
            } else null
        }
    }
}
