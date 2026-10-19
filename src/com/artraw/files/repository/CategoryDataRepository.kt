package com.artraw.files.repository

import com.artraw.files.model.FileItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory repository & cache for Category file listings (Images, Videos, Audio, Documents, etc.).
 *
 * Provides:
 *   - 0ms instant display when navigating back from Photo Viewer or switching categories
 *   - Avoids repeated MediaStore / filesystem traversal queries on back navigation
 *   - Real-time synchronization when items are deleted, renamed, or modified
 */
object CategoryDataRepository {

    private val cache = ConcurrentHashMap<String, List<FileItem>>()
    private val lastLoadedTime = ConcurrentHashMap<String, Long>()

    // Cache TTL: 30 seconds before a background diff refresh is considered
    private const val CACHE_TTL_MS = 30_000L

    /**
     * Retrieve cached file items for the given category id, or null if not yet loaded.
     */
    fun getCached(catId: String): List<FileItem>? {
        return cache[catId]
    }

    /**
     * Store loaded items into the cache.
     */
    fun putCache(catId: String, items: List<FileItem>) {
        cache[catId] = ArrayList(items)
        lastLoadedTime[catId] = System.currentTimeMillis()
    }

    /**
     * Check if the cache for the given category is fresh.
     */
    fun isCacheFresh(catId: String): Boolean {
        val loaded = lastLoadedTime[catId] ?: return false
        return (System.currentTimeMillis() - loaded) < CACHE_TTL_MS
    }

    /**
     * Remove a single item by path from all category caches.
     * Called immediately when an image or file is deleted from Photo Viewer or Dialog.
     */
    fun removeItem(path: String) {
        for ((catId, list) in cache) {
            val filtered = list.filterNot { it.file.absolutePath == path }
            if (filtered.size != list.size) {
                cache[catId] = ArrayList(filtered)
            }
        }
    }

    /**
     * Remove multiple items by path from all category caches.
     * Called immediately on batch delete.
     */
    fun removeItems(paths: Collection<String>) {
        val pathSet = if (paths is Set<*>) paths else paths.toSet()
        for ((catId, list) in cache) {
            val filtered = list.filterNot { pathSet.contains(it.file.absolutePath) }
            if (filtered.size != list.size) {
                cache[catId] = ArrayList(filtered)
            }
        }
    }

    /**
     * Rename an item across all category caches.
     */
    fun renameItem(oldPath: String, newFile: File) {
        for ((catId, list) in cache) {
            val updated = list.map { item ->
                if (item.file.absolutePath == oldPath) {
                    item.copy(
                        file = newFile,
                        name = newFile.name,
                        lastModified = newFile.lastModified()
                    )
                } else {
                    item
                }
            }
            cache[catId] = ArrayList(updated)
        }
    }

    /**
     * Clear all cached categories.
     */
    fun clear() {
        cache.clear()
        lastLoadedTime.clear()
    }

    /**
     * Invalidate a specific category so it is reloaded from disk on next query.
     */
    fun invalidate(catId: String) {
        cache.remove(catId)
        lastLoadedTime.remove(catId)
    }
}
