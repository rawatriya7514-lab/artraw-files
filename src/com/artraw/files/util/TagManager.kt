package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import java.io.File

/**
 * TagManager — Color tags for files/folders. Tags are stored as path->tag map.
 */
object TagManager {

    private const val PREFS_NAME = "artraw_tags_prefs"
    private const val KEY_TAGS = "file_tags"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setTag(path: String, tag: String?) {
        val tags = getAllTags().toMutableMap()
        if (tag == null || tag.isBlank()) {
            tags.remove(path)
        } else {
            tags[path] = tag
        }
        prefs.edit().putString(KEY_TAGS, tags.entries.joinToString("|") { "${it.key}:::${it.value}" }).apply()
    }

    fun getTag(path: String): String? = getAllTags()[path]

    private fun getAllTags(): Map<String, String> {
        val raw = prefs.getString(KEY_TAGS, "") ?: ""
        if (raw.isBlank()) return emptyMap()
        return raw.split("|").filter { it.isNotBlank() }.map { entry ->
            val parts = entry.split(":::")
            if (parts.size == 2) parts[0] to parts[1] else null
        }.filterNotNull().toMap()
    }
}

/**
 * QuickAccessManager — Pinned folders for one-tap navigation.
 */
object QuickAccessManager {

    private const val PREFS_NAME = "artraw_quickaccess_prefs"
    private const val KEY_PINNED = "pinned_paths"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isPinned(path: String): Boolean = path in getPinnedPaths()

    fun togglePin(path: String) {
        val paths = getPinnedPaths().toMutableSet()
        if (paths.contains(path)) paths.remove(path) else paths.add(path)
        prefs.edit().putString(KEY_PINNED, paths.joinToString("|")).apply()
    }

    fun getPinnedPaths(): Set<String> {
        val raw = prefs.getString(KEY_PINNED, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split("|").filter { it.isNotBlank() }.toSet()
    }

    fun clearPins() {
        prefs.edit().remove(KEY_PINNED).apply()
    }
}
