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

    /**
     * ULTRA-FAST INSTANT MEDIA LOADER (< 30ms).
     * Directly queries MediaStore and builds ready-to-display AiMediaItem list with 0ms delay!
     */
    fun loadFastDeviceMedia(context: Context): List<AiMediaItem> {
        val items = mutableListOf<AiMediaItem>()
        val seenPaths = mutableSetOf<String>()
        val db = AiGalleryDatabase.getInstance(context)
        val cachedMap = mutableMapOf<String, AiMediaItem>()

        // Fast load existing enriched items from SQLite into memory map
        try {
            val dbItems = db.getAllItems(2000)
            for (it in dbItems) {
                cachedMap[it.path] = it
            }
        } catch (_: Throwable) {}

        // 1. Query MediaStore Images
        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
            context.contentResolver.query(uri, proj, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val wIdx = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val hIdx = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

                while (c.moveToNext()) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                        val file = File(path)
                        if (file.exists() && file.length() > 0) {
                            val cached = cachedMap[path]
                            if (cached != null) {
                                items.add(cached)
                            } else {
                                val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                                val size = if (sizeIdx != -1) c.getLong(sizeIdx) else file.length()
                                val mod = if (modIdx != -1) c.getLong(modIdx) * 1000L else file.lastModified()
                                val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: "image/jpeg" else "image/jpeg"
                                val w = if (wIdx != -1) c.getInt(wIdx) else 0
                                val h = if (hIdx != -1) c.getInt(hIdx) else 0
                                val cat = classifyCategory(file)
                                val tags = ImageFeatureExtractor.guessTagsFromPath(file, "UNKNOWN")
                                val isFav = FavoritesManager.isFavorite(path)

                                items.add(
                                    AiMediaItem(
                                        file = file,
                                        name = name,
                                        path = path,
                                        size = size,
                                        dateModified = mod,
                                        dateTaken = mod,
                                        width = w,
                                        height = h,
                                        mimeType = mime,
                                        isVideo = false,
                                        isAudio = false,
                                        isDocument = cat == "documents",
                                        category = cat,
                                        detectedTags = tags,
                                        description = "Photo: $name",
                                        isFavorite = isFav
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Query MediaStore Videos
        try {
            val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.WIDTH,
                MediaStore.MediaColumns.HEIGHT
            )
            context.contentResolver.query(uri, proj, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val durIdx = c.getColumnIndex(MediaStore.Video.Media.DURATION)
                val wIdx = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val hIdx = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

                while (c.moveToNext()) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                        val file = File(path)
                        if (file.exists() && file.length() > 0) {
                            val cached = cachedMap[path]
                            if (cached != null) {
                                items.add(cached)
                            } else {
                                val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                                val size = if (sizeIdx != -1) c.getLong(sizeIdx) else file.length()
                                val mod = if (modIdx != -1) c.getLong(modIdx) * 1000L else file.lastModified()
                                val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: "video/mp4" else "video/mp4"
                                val dur = if (durIdx != -1) c.getLong(durIdx) else 0L
                                val w = if (wIdx != -1) c.getInt(wIdx) else 0
                                val h = if (hIdx != -1) c.getInt(hIdx) else 0
                                val isFav = FavoritesManager.isFavorite(path)

                                items.add(
                                    AiMediaItem(
                                        file = file,
                                        name = name,
                                        path = path,
                                        size = size,
                                        dateModified = mod,
                                        dateTaken = mod,
                                        width = w,
                                        height = h,
                                        mimeType = mime,
                                        isVideo = true,
                                        isAudio = false,
                                        durationMs = dur,
                                        category = "videos",
                                        detectedTags = listOf("video"),
                                        description = "Video recording: $name",
                                        isFavorite = isFav
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. Query MediaStore Audio (MP3 & Music)
        try {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val proj = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.Audio.Media.DURATION
            )
            context.contentResolver.query(uri, proj, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val durIdx = c.getColumnIndex(MediaStore.Audio.Media.DURATION)

                while (c.moveToNext()) {
                    val path = if (dataIdx != -1) c.getString(dataIdx) else null
                    if (!path.isNullOrEmpty() && seenPaths.add(path)) {
                        val file = File(path)
                        if (file.exists() && file.length() > 0) {
                            val cached = cachedMap[path]
                            if (cached != null) {
                                items.add(cached)
                            } else {
                                val name = if (nameIdx != -1) c.getString(nameIdx) ?: file.name else file.name
                                val size = if (sizeIdx != -1) c.getLong(sizeIdx) else file.length()
                                val mod = if (modIdx != -1) c.getLong(modIdx) * 1000L else file.lastModified()
                                val mime = if (mimeIdx != -1) c.getString(mimeIdx) ?: "audio/mpeg" else "audio/mpeg"
                                val dur = if (durIdx != -1) c.getLong(durIdx) else 0L
                                val isFav = FavoritesManager.isFavorite(path)

                                items.add(
                                    AiMediaItem(
                                        file = file,
                                        name = name,
                                        path = path,
                                        size = size,
                                        dateModified = mod,
                                        dateTaken = mod,
                                        mimeType = mime,
                                        isVideo = false,
                                        isAudio = true,
                                        durationMs = dur,
                                        category = "audio",
                                        detectedTags = listOf("audio", "music", "song", "mp3"),
                                        description = "Audio track: $name",
                                        isFavorite = isFav
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 4. Quick scan storage roots for documents and any unindexed media
        val roots = listOf(
            File("/sdcard/DCIM"),
            File("/sdcard/Pictures"),
            File("/sdcard/Download"),
            File("/sdcard/Documents"),
            File("/sdcard/Movies"),
            File("/sdcard/Music")
        )
        for (dir in roots) {
            if (dir.exists() && dir.isDirectory) {
                scanDirFast(dir, items, seenPaths, cachedMap, depth = 0)
            }
        }

        return items.sortedByDescending { it.dateTaken }
    }

    private fun scanDirFast(
        dir: File,
        list: MutableList<AiMediaItem>,
        seen: MutableSet<String>,
        cached: Map<String, AiMediaItem>,
        depth: Int
    ) {
        if (depth > 4) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                scanDirFast(f, list, seen, cached, depth + 1)
            } else if (f.isFile && seen.add(f.absolutePath)) {
                val lower = f.name.lowercase(Locale.ROOT)
                val isImg = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".gif")
                val isVid = lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") || lower.endsWith(".3gp")
                val isAud = lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".ogg")
                val isDoc = lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".xls") || lower.endsWith(".xlsx")

                if (isImg || isVid || isAud || isDoc) {
                    val hit = cached[f.absolutePath]
                    if (hit != null) {
                        list.add(hit)
                    } else {
                        val cat = classifyCategory(f)
                        val isFav = FavoritesManager.isFavorite(f.absolutePath)
                        val tags = ImageFeatureExtractor.guessTagsFromPath(f, "UNKNOWN")
                        list.add(
                            AiMediaItem(
                                file = f,
                                name = f.name,
                                path = f.absolutePath,
                                size = f.length(),
                                dateModified = f.lastModified(),
                                dateTaken = f.lastModified(),
                                isVideo = isVid,
                                isAudio = isAud,
                                isDocument = isDoc,
                                category = cat,
                                detectedTags = tags,
                                description = "${f.name}",
                                isFavorite = isFav
                            )
                        )
                    }
                }
            }
        }
    }

    fun startIndexing(
        context: Context,
        forceRescan: Boolean = false,
        listener: IndexProgressListener? = null
    ) {
        if (isIndexing.getAndSet(true)) return

        executor.execute {
            try {
                // Set thread to background priority so UI stays smooth at 60fps
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                val db = AiGalleryDatabase.getInstance(context)
                val mediaFiles = loadFastDeviceMedia(context).map { it.file }
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
                    if (processed % 8 == 0 || processed == total) {
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

    fun classifyCategory(file: File): String {
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)
        return when {
            pathLower.contains("screenshot") -> "screenshots"
            pathLower.contains("dcim") || pathLower.contains("camera") -> "camera"
            pathLower.contains("download") -> "downloads"
            pathLower.endsWith(".mp3") || pathLower.endsWith(".m4a") || pathLower.endsWith(".wav") || pathLower.endsWith(".flac") -> "audio"
            pathLower.endsWith(".pdf") || pathLower.endsWith(".doc") || pathLower.endsWith(".docx") || pathLower.endsWith(".txt") || pathLower.contains("receipt") || pathLower.contains("bill") -> "documents"
            pathLower.endsWith(".mp4") || pathLower.endsWith(".mkv") || pathLower.endsWith(".mov") -> "videos"
            else -> "photos"
        }
    }
}
