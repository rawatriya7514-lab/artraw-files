package com.artraw.files.gallery.indexer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.artraw.files.gallery.ai.ImageFeatureExtractor
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.util.FavoritesManager
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object GalleryIndexer {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isIndexing = AtomicBoolean(false)

    interface IndexProgressListener {
        fun onProgress(current: Int, total: Int)
        fun onComplete(totalIndexed: Int)
    }

    fun isCurrentlyIndexing(): Boolean = isIndexing.get()

    fun startIndexing(
        context: Context,
        forceRescan: Boolean = false,
        listener: IndexProgressListener? = null
    ) {
        if (isIndexing.getAndSet(true)) return

        executor.execute {
            try {
                val db = AiGalleryDatabase.getInstance(context)
                val mediaFiles = scanMediaFiles(context)
                val total = mediaFiles.size

                mainHandler.post { listener?.onProgress(0, total) }

                var processed = 0
                for (file in mediaFiles) {
                    try {
                        if (!file.exists()) continue
                        val isCached = !forceRescan && db.isIndexed(file.absolutePath, file.lastModified())
                        if (!isCached) {
                            val features = ImageFeatureExtractor.extractFeatures(file)
                            val category = classifyCategory(file)
                            val isFav = FavoritesManager.isFavorite(file.absolutePath)
                            db.upsertItem(file, features, category, isFav)
                        }
                    } catch (_: Throwable) {
                    }
                    processed++
                    if (processed % 5 == 0 || processed == total) {
                        val curr = processed
                        mainHandler.post { listener?.onProgress(curr, total) }
                    }
                }

                mainHandler.post { listener?.onComplete(processed) }
            } catch (_: Throwable) {
            } finally {
                isIndexing.set(false)
            }
        }
    }

    private fun scanMediaFiles(context: Context): List<File> {
        val files = mutableListOf<File>()
        val seenPaths = mutableSetOf<String>()

        // 1. Query MediaStore Images
        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, proj, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    if (dataIdx != -1) {
                        val path = c.getString(dataIdx)
                        if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                            val f = File(path)
                            if (f.exists() && f.length() > 0) files.add(f)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Query MediaStore Videos
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, proj, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    if (dataIdx != -1) {
                        val path = c.getString(dataIdx)
                        if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                            val f = File(path)
                            if (f.exists() && f.length() > 0) files.add(f)
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. Fallback scan standard directories (DCIM, Pictures, Download, Documents)
        val roots = listOf(
            File("/sdcard/DCIM"),
            File("/sdcard/Pictures"),
            File("/sdcard/Download"),
            File("/sdcard/Documents")
        )
        for (dir in roots) {
            if (dir.exists() && dir.isDirectory) {
                scanDirRecursive(dir, files, seenPaths, depth = 0)
            }
        }

        return files.sortedByDescending { it.lastModified() }
    }

    private fun scanDirRecursive(dir: File, list: MutableList<File>, seen: MutableSet<String>, depth: Int) {
        if (depth > 4) return
        val items = dir.listFiles() ?: return
        for (f in items) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanDirRecursive(f, list, seen, depth + 1)
            } else if (f.isFile) {
                val lower = f.name.lowercase(Locale.ROOT)
                val isMedia = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp") ||
                        lower.endsWith(".gif") || lower.endsWith(".mp4") ||
                        lower.endsWith(".mkv") || lower.endsWith(".mov")
                if (isMedia && seen.add(f.absolutePath)) {
                    list.add(f)
                }
            }
        }
    }

    private fun classifyCategory(file: File): String {
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)
        return when {
            pathLower.contains("screenshot") -> "screenshots"
            pathLower.contains("dcim") || pathLower.contains("camera") -> "camera"
            pathLower.contains("download") -> "downloads"
            pathLower.contains("doc") || pathLower.contains("receipt") || pathLower.contains("bill") -> "documents"
            pathLower.endsWith(".mp4") || pathLower.endsWith(".mkv") || pathLower.endsWith(".mov") -> "videos"
            else -> "photos"
        }
    }
}
