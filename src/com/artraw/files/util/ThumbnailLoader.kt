package com.artraw.files.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * High-performance dual-tier thumbnail loader with persistent WebP disk cache.
 *
 * Architecture:
 *   - Tier 1: LruCache RAM cache (1/6th max heap) with strictly bounded 256px RGB_565 bitmaps
 *   - Tier 2: Persistent disk cache at context.cacheDir/thumbnails/<hash>.webp (1-2ms instant recall)
 *   - Universal decoding: Images (with EXIF auto-rotation), Videos, Audio (embedded ID3 & folder art), APKs
 *   - Negative caching: skips repeated I/O for corrupt / non-media files
 *   - Seamless recycling & tag isolation avoiding UI flashes
 */
object ThumbnailLoader {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val threadCount = AtomicInteger(1)
    private val executor = Executors.newFixedThreadPool(4, ThreadFactory { r ->
        Thread(r, "thumb-loader-${threadCount.getAndIncrement()}").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    })

    // Memory cache sized to 1/6th of max available heap, bounded between 16MB and 48MB
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 6).coerceIn(16384, 49152)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    // Negative cache: paths known to fail decoding or have no artwork, preventing scroll lag
    private val failedPaths = Collections.synchronizedSet(object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            if (size > 1000) {
                val it = iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            return super.add(element)
        }
    })

    @Volatile
    private var diskCacheDir: File? = null

    fun getDiskCacheDir(context: Context): File {
        val dir = diskCacheDir ?: synchronized(this) {
            diskCacheDir ?: File(context.applicationContext.cacheDir, "thumbnails").also {
                if (!it.exists()) it.mkdirs()
                diskCacheDir = it
            }
        }
        return dir
    }

    fun getCachedBitmap(path: String): Bitmap? {
        val bmp = memoryCache.get(path)
        if (bmp != null && !bmp.isRecycled) return bmp

        // Fallback: Check disk cache if diskCacheDir was initialized
        diskCacheDir?.let { dir ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val cacheFile = File(dir, "${getCacheKey(file)}.webp")
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        val options = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        val diskBmp = BitmapFactory.decodeFile(cacheFile.absolutePath, options)
                        if (diskBmp != null) {
                            memoryCache.put(path, diskBmp)
                            return diskBmp
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun putCachedBitmap(path: String, bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            memoryCache.put(path, bitmap)
        }
    }

    fun clearCache(context: Context) {
        memoryCache.evictAll()
        failedPaths.clear()
        executor.execute {
            try {
                val dir = getDiskCacheDir(context)
                dir.listFiles()?.forEach { it.delete() }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Load thumbnail for a file item into the given ImageView.
     */
    fun loadThumbnail(imageView: ImageView, item: FileItem, targetSize: Int = 120, defaultIconRes: Int) {
        val ctx = imageView.context
        val path = item.file.absolutePath

        // 1. Directory -> standard folder icon
        if (item.isDirectory) {
            imageView.tag = path
            imageView.imageTintList = null
            imageView.colorFilter = null
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(R.drawable.ic_folder)
            imageView.setColorFilter(android.graphics.Color.parseColor("#FF8C42"))
            return
        }

        // 2. Encrypted -> standard lock icon
        if (item.isEncrypted) {
            imageView.tag = path
            imageView.imageTintList = null
            imageView.colorFilter = null
            imageView.clearColorFilter()
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(R.drawable.ic_lock)
            imageView.setColorFilter(android.graphics.Color.parseColor("#8A2BE2"))
            return
        }

        // Initialize disk cache directory lazily
        getDiskCacheDir(ctx)

        // 3. Memory cache hit -> apply immediately (0ms)
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) {
            imageView.tag = path
            applyBitmap(imageView, cached, item)
            return
        }

        // 4. Known failed path -> apply category fallback immediately without background worker
        if (failedPaths.contains(path)) {
            imageView.tag = path
            applyFallback(imageView, item, defaultIconRes)
            return
        }

        val isApk = item.isApk || item.category == FileCategory.APKS || item.extension.equals("apk", ignoreCase = true)
        val isImage = item.category == FileCategory.IMAGES
        val isVideo = item.category == FileCategory.VIDEOS
        val isAudio = item.category == FileCategory.AUDIO

        // If not a visual asset, fallback icon with category color
        if (!isImage && !isVideo && !isApk && !isAudio) {
            imageView.tag = path
            applyFallback(imageView, item, defaultIconRes)
            return
        }

        // 5. Placeholder while decoding
        imageView.tag = path
        imageView.imageTintList = null
        imageView.colorFilter = null
        imageView.clearColorFilter()
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(defaultIconRes)
        imageView.setColorFilter(getCategoryColor(item))

        // 6. Background thread: Disk Cache -> Raw Decode -> Save Disk Cache -> Apply UI
        val reqSize = targetSize.coerceIn(120, 320)
        executor.execute {
            // Check Tier 2 Disk Cache first!
            val diskBmp = loadFromDiskCache(ctx, item.file)
            if (diskBmp != null) {
                memoryCache.put(path, diskBmp)
                mainHandler.post {
                    if (imageView.tag == path) {
                        applyBitmap(imageView, diskBmp, item)
                    }
                }
                return@execute
            }

            // Not on disk cache -> Decode from raw media file
            var bitmap: Bitmap? = null
            try {
                bitmap = when {
                    isImage -> decodeSampledBitmap(item.file, reqSize, reqSize)
                    isVideo -> decodeVideoThumbnail(item.file, reqSize, reqSize)
                    isAudio -> decodeAudioAlbumArt(item.file, reqSize, reqSize)
                    isApk -> decodeApkIcon(ctx, item.file, reqSize, reqSize)
                    else -> null
                }
            } catch (t: Throwable) {
                bitmap = null
            }

            if (bitmap != null && !bitmap.isRecycled) {
                // Save to persistent disk cache
                saveToDiskCache(ctx, item.file, bitmap)
                memoryCache.put(path, bitmap)
                mainHandler.post {
                    if (imageView.tag == path) {
                        applyBitmap(imageView, bitmap, item)
                    }
                }
            } else {
                // Record negative cache
                failedPaths.add(path)
                mainHandler.post {
                    if (imageView.tag == path) {
                        applyFallback(imageView, item, defaultIconRes)
                    }
                }
            }
        }
    }

    private fun applyFallback(imageView: ImageView, item: FileItem, defaultIconRes: Int) {
        imageView.imageTintList = null
        imageView.colorFilter = null
        imageView.clearColorFilter()
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        val iconRes = if (defaultIconRes != 0) defaultIconRes else getFallbackIconRes(item)
        imageView.setImageResource(iconRes)
        imageView.setColorFilter(getCategoryColor(item))
    }

    private fun applyBitmap(imageView: ImageView, bitmap: Bitmap, item: FileItem) {
        if (bitmap.isRecycled) return
        imageView.imageTintList = null
        imageView.colorFilter = null
        imageView.clearColorFilter()

        val isApk = item.isApk || item.category == FileCategory.APKS
        imageView.scaleType = if (isApk) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        imageView.setImageBitmap(bitmap)
    }

    fun getFallbackIconRes(item: FileItem): Int {
        return when (item.category) {
            FileCategory.IMAGES -> R.drawable.ic_image
            FileCategory.VIDEOS -> R.drawable.ic_video
            FileCategory.AUDIO -> R.drawable.ic_audio
            FileCategory.DOCUMENTS -> R.drawable.ic_document
            FileCategory.DOWNLOADS -> R.drawable.ic_download
            FileCategory.ARCHIVES -> R.drawable.ic_archive
            FileCategory.APKS -> R.drawable.ic_apk
            FileCategory.OTHER -> R.drawable.ic_file
        }
    }

    fun getCategoryColor(item: FileItem): Int {
        return when (item.category) {
            FileCategory.IMAGES -> android.graphics.Color.parseColor("#42A5F5")   // Soft Blue
            FileCategory.VIDEOS -> android.graphics.Color.parseColor("#AB47BC")   // Soft Purple
            FileCategory.AUDIO -> android.graphics.Color.parseColor("#26A69A")    // Soft Teal
            FileCategory.DOCUMENTS -> android.graphics.Color.parseColor("#FFA726")// Soft Amber
            FileCategory.DOWNLOADS -> android.graphics.Color.parseColor("#29B6F6")// Soft Cyan
            FileCategory.ARCHIVES -> android.graphics.Color.parseColor("#7E57C2") // Soft Deep Purple
            FileCategory.APKS -> android.graphics.Color.parseColor("#66BB6A")     // Soft Green
            FileCategory.OTHER -> android.graphics.Color.parseColor("#78909C")    // Soft Slate
        }
    }

    private fun getCacheKey(file: File): String {
        val raw = "${file.absolutePath}_${file.lastModified()}_${file.length()}"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            val sb = java.lang.StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            sb.toString()
        } catch (_: Throwable) {
            raw.hashCode().toString()
        }
    }

    private fun loadFromDiskCache(context: Context, file: File): Bitmap? {
        if (!file.exists()) return null
        return try {
            val cacheDir = getDiskCacheDir(context)
            val cacheFile = File(cacheDir, "${getCacheKey(file)}.webp")
            if (!cacheFile.exists() || cacheFile.length() == 0L) return null

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(cacheFile.absolutePath, options)
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveToDiskCache(context: Context, file: File, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        try {
            val cacheDir = getDiskCacheDir(context)
            val cacheFile = File(cacheDir, "${getCacheKey(file)}.webp")
            val tempFile = File(cacheDir, "${cacheFile.name}.tmp")

            FileOutputStream(tempFile).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, fos)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, fos)
                }
                fos.flush()
            }
            tempFile.renameTo(cacheFile)
        } catch (_: Throwable) {
            // Ignore disk cache write errors gracefully
        }
    }

    private fun scaleToFit(bmp: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxW && h <= maxH) return bmp

        val ratio = Math.min(maxW.toFloat() / w, maxH.toFloat() / h)
        val newW = Math.max(1, (w * ratio).toInt())
        val newH = Math.max(1, (h * ratio).toInt())
        val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
        if (scaled != bmp && !bmp.isRecycled) {
            bmp.recycle()
        }
        return scaled
    }

    /**
     * Decode image file to memory-efficient sampled bitmap with EXIF rotation support.
     */
    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                FileInputStream(file).use { fis ->
                    BitmapFactory.decodeStream(fis, null, options)
                }
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565

            var decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decoded == null) {
                decoded = FileInputStream(file).use { fis ->
                    BitmapFactory.decodeStream(fis, null, options)
                }
            }
            if (decoded == null) return null

            // EXIF Orientation check
            val rotation = getExifRotation(file)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded && !decoded.isRecycled) {
                    decoded.recycle()
                }
                decoded = rotated
            }

            return scaleToFit(decoded, reqWidth, reqHeight)
        } catch (_: Throwable) {
            return null
        }
    }

    private fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Decode video thumbnail using native hardware acceleration and scaled keyframes.
     */
    private fun decodeVideoThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        // Fast Path 1: Android 10+ (API 29+) hardware accelerated ThumbnailUtils
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val size = Size(reqWidth, reqHeight)
                val thumb = ThumbnailUtils.createVideoThumbnail(file, size, null)
                if (thumb != null) {
                    return scaleToFit(thumb, reqWidth, reqHeight)
                }
            } catch (_: Throwable) {}
        }

        // Fast Path 2: MediaMetadataRetriever scaled frame directly (Android 8.1+ / API 27+)
        try {
            val retriever = MediaMetadataRetriever()
            var frame: Bitmap? = null
            try {
                retriever.setDataSource(file.absolutePath)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    frame = retriever.getScaledFrameAtTime(
                        1000000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        reqWidth,
                        reqHeight
                    )
                }
                if (frame == null) {
                    frame = retriever.getFrameAtTime(1000000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                if (frame == null) {
                    frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            if (frame != null) {
                return scaleToFit(frame, reqWidth, reqHeight)
            }
        } catch (_: Throwable) {}

        // Fallback 3: Legacy ThumbnailUtils
        try {
            @Suppress("DEPRECATION")
            val thumb = ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            if (thumb != null) {
                return scaleToFit(thumb, reqWidth, reqHeight)
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Decode audio album cover art from ID3/MP4/FLAC metadata or folder image.
     */
    private fun decodeAudioAlbumArt(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        // Tier 1: Extract embedded album cover artwork (ID3 APIC, MP4 covr, FLAC picture)
        try {
            val retriever = MediaMetadataRetriever()
            var artBytes: ByteArray? = null
            try {
                retriever.setDataSource(file.absolutePath)
                artBytes = retriever.embeddedPicture
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }

            if (artBytes != null && artBytes.isNotEmpty()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
                    options.inJustDecodeBounds = false
                    options.inPreferredConfig = Bitmap.Config.RGB_565
                    val bmp = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                    if (bmp != null) {
                        return scaleToFit(bmp, reqWidth, reqHeight)
                    }
                }
            }
        } catch (_: Throwable) {}

        // Tier 2: Search for common cover art image files in the same folder
        try {
            val parent = file.parentFile
            if (parent != null && parent.isDirectory) {
                val coverFileNames = arrayOf(
                    "cover.jpg", "cover.png", "cover.jpeg",
                    "folder.jpg", "folder.png", "album.jpg",
                    "album.png", "art.jpg", "front.jpg"
                )
                for (name in coverFileNames) {
                    val candidate = File(parent, name)
                    if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                        val bmp = decodeSampledBitmap(candidate, reqWidth, reqHeight)
                        if (bmp != null) return bmp
                    }
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Extract high-resolution icon from APK package archive.
     */
    private fun decodeApkIcon(context: Context, file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            val appInfo = pkgInfo.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath

            var iconDrawable: Drawable? = null

            // Tier 1: Mount APK resources directly to get genuine application icon
            try {
                val res = pm.getResourcesForApplication(appInfo)
                if (appInfo.icon != 0) {
                    iconDrawable = res.getDrawable(appInfo.icon, null)
                }
            } catch (_: Throwable) {}

            // Tier 2: Fallback to loadIcon
            if (iconDrawable == null) {
                try {
                    iconDrawable = appInfo.loadIcon(pm)
                } catch (_: Throwable) {}
            }

            if (iconDrawable == null) return null

            // Extract bitmap from drawable
            if (iconDrawable is BitmapDrawable && iconDrawable.bitmap != null) {
                return scaleToFit(iconDrawable.bitmap, reqWidth, reqHeight)
            }

            val w = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else reqWidth
            val h = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else reqHeight
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
            iconDrawable.draw(canvas)
            return scaleToFit(bmp, reqWidth, reqHeight)
        } catch (_: Throwable) {
            return null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight || halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
