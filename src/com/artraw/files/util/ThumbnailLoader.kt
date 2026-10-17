package com.artraw.files.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import java.io.File
import java.util.concurrent.Executors

/**
 * High-performance thumbnail loader with robust fallback system.
 * Features:
 *   - Memory cache (LruCache)
 *   - Async decoding on thread pool
 *   - MIME-type based fallback icons when thumbnail generation fails
 *   - APK icon extraction from package manager
 *   - Video thumbnail via MediaMetadataRetriever (non-deprecated)
 *   - Permission-aware: skips thumbnail if storage not accessible
 */
object ThumbnailLoader {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4)

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    /**
     * Load thumbnail for a file item.
     * If thumbnail generation fails, falls back to category-based drawable icon.
     */
    fun loadThumbnail(imageView: ImageView, item: FileItem, targetSize: Int = 96, defaultIconRes: Int) {
        val ctx = imageView.context
        val path = item.file.absolutePath

        // Directory -> show folder icon
        if (item.isDirectory) {
            imageView.tag = path
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(R.drawable.ic_folder)
            imageView.setColorFilter(android.graphics.Color.parseColor("#FF8C42"))
            return
        }

        // Encrypted -> show lock icon
        if (item.isEncrypted) {
            imageView.tag = path
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(R.drawable.ic_lock)
            imageView.setColorFilter(android.graphics.Color.parseColor("#8A2BE2"))
            return
        }

        // Check cache
        val cached = memoryCache.get(path)
        if (cached != null) {
            applyBitmap(imageView, path, cached, item)
            return
        }

        // Show default placeholder while decoding
        imageView.tag = path
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(defaultIconRes)
        imageView.clearColorFilter()

        // Decode on background thread
        executor.execute {
            var bitmap: Bitmap? = null

            try {
                when {
                    item.category == FileCategory.IMAGES -> {
                        bitmap = decodeSampledBitmap(item.file, targetSize, targetSize)
                    }
                    item.category == FileCategory.VIDEOS -> {
                        bitmap = decodeVideoThumbnail(item.file, targetSize, targetSize)
                    }
                    item.isApk -> {
                        bitmap = decodeApkIcon(ctx, item.file)
                    }
                }
            } catch (_: Throwable) {
                bitmap = null
            }

            // Fallback: if bitmap is null, use category drawable
            if (bitmap == null) {
                mainHandler.post {
                    if (imageView.tag == path) {
                        val fallbackRes = getFallbackIconRes(item)
                        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        imageView.setImageResource(fallbackRes)
                        imageView.setColorFilter(getCategoryColor(item))
                    }
                }
            } else {
                // Cache and apply
                memoryCache.put(path, bitmap)
                mainHandler.post {
                    if (imageView.tag == path) {
                        applyBitmap(imageView, path, bitmap, item)
                    }
                }
            }
        }
    }

    private fun applyBitmap(imageView: ImageView, path: String, bitmap: Bitmap, item: FileItem) {
        imageView.scaleType = if (item.isApk) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        imageView.setImageBitmap(bitmap)
        imageView.clearColorFilter()
    }

    private fun getFallbackIconRes(item: FileItem): Int {
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

    private fun getCategoryColor(item: FileItem): Int {
        return when (item.category) {
            FileCategory.IMAGES -> android.graphics.Color.parseColor("#E53935")
            FileCategory.VIDEOS -> android.graphics.Color.parseColor("#AB47BC")
            FileCategory.AUDIO -> android.graphics.Color.parseColor("#26A69A")
            FileCategory.DOCUMENTS -> android.graphics.Color.parseColor("#FFA726")
            FileCategory.DOWNLOADS -> android.graphics.Color.parseColor("#29B6F6")
            FileCategory.ARCHIVES -> android.graphics.Color.parseColor("#7E57C2")
            FileCategory.APKS -> android.graphics.Color.parseColor("#66BB6A")
            FileCategory.OTHER -> android.graphics.Color.parseColor("#78909C")
        }
    }

    /**
     * Decode image file to sampled bitmap (memory efficient).
     */
    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || !file.canRead()) return null

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        options.inPurgeable = true
        options.inInputShareable = true

        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /**
     * Decode video thumbnail using MediaMetadataRetriever (non-deprecated alternative).
     */
    private fun decodeVideoThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val retriever = MediaMetadataRetriever().apply {
                setDataSource(file.absolutePath)
            }
            val frame: Bitmap? = retriever.getFrameAtTime(
                1000000, // 1 second in microseconds
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            retriever.release()

            if (frame != null) {
                // Scale down if needed
                if (frame.width > reqWidth || frame.height > reqHeight) {
                    val scale = Math.min(reqWidth.toFloat() / frame.width, reqHeight.toFloat() / frame.height)
                    val newWidth = (frame.width * scale).toInt()
                    val newHeight = (frame.height * scale).toInt()
                    Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
                } else {
                    frame
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extract icon from APK file.
     */
    private fun decodeApkIcon(context: Context, file: File): Bitmap? {
        if (!file.exists() || !file.canRead()) return null

        return try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            val appInfo = pkgInfo.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val iconDrawable: Drawable = appInfo.loadIcon(pm) ?: return null

            if (iconDrawable is BitmapDrawable && iconDrawable.bitmap != null) {
                iconDrawable.bitmap
            } else {
                val w = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 96
                val h = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 96
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                iconDrawable.draw(canvas)
                bmp
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
