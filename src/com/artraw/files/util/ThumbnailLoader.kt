package com.artraw.files.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors

/**
 * High-performance thumbnail loader with multi-tier fallback system.
 *
 * Capabilities:
 *   - LruCache memory caching (sized to 1/8 of available RAM)
 *   - Multithreaded async decoding pool
 *   - Universal image decoding (JPEG, PNG, WebP, GIF, BMP, HEIC)
 *   - Fast video frame extraction via MediaMetadataRetriever
 *   - Deep APK icon extraction mounting internal application resources
 *   - Active tint clearing ensuring full-color, untainted rendering
 */
object ThumbnailLoader {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(4)

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(2048)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
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

        // 3. Memory cache hit -> apply immediately
        val cached = memoryCache.get(path)
        if (cached != null) {
            imageView.tag = path
            applyBitmap(imageView, cached, item)
            return
        }

        // 4. Default placeholder while decoding
        imageView.tag = path
        imageView.imageTintList = null
        imageView.colorFilter = null
        imageView.clearColorFilter()
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(defaultIconRes)

        val isApk = item.isApk || item.category == FileCategory.APKS || item.extension.equals("apk", ignoreCase = true)
        val isImage = item.category == FileCategory.IMAGES
        val isVideo = item.category == FileCategory.VIDEOS

        // If not a visual asset, fallback icon with category color
        if (!isImage && !isVideo && !isApk) {
            imageView.setColorFilter(getCategoryColor(item))
            return
        }

        // 5. Background thread decoding
        executor.execute {
            var bitmap: Bitmap? = null

            try {
                when {
                    isImage -> {
                        bitmap = decodeSampledBitmap(item.file, targetSize, targetSize)
                    }
                    isVideo -> {
                        bitmap = decodeVideoThumbnail(item.file, targetSize, targetSize)
                    }
                    isApk -> {
                        bitmap = decodeApkIcon(ctx, item.file)
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("ThumbnailLoader", "Decode error for ${item.name}: ${t.message}")
                bitmap = null
            }

            if (bitmap != null) {
                memoryCache.put(path, bitmap)
                mainHandler.post {
                    if (imageView.tag == path) {
                        applyBitmap(imageView, bitmap, item)
                    }
                }
            } else {
                mainHandler.post {
                    if (imageView.tag == path) {
                        imageView.imageTintList = null
                        imageView.colorFilter = null
                        imageView.clearColorFilter()
                        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        imageView.setImageResource(getFallbackIconRes(item))
                        imageView.setColorFilter(getCategoryColor(item))
                    }
                }
            }
        }
    }

    private fun applyBitmap(imageView: ImageView, bitmap: Bitmap, item: FileItem) {
        // Crucial: Clear all tint lists and filters so bitmap is rendered in full native color
        imageView.imageTintList = null
        imageView.colorFilter = null
        imageView.clearColorFilter()

        val isApk = item.isApk || item.category == FileCategory.APKS
        imageView.scaleType = if (isApk) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        imageView.setImageBitmap(bitmap)
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
     * Decode image file to memory-efficient sampled bitmap.
     */
    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists()) return null

        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                // Try via FileInputStream fallback
                FileInputStream(file).use { fis ->
                    BitmapFactory.decodeStream(fis, null, options)
                }
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decoded != null) return decoded

            // Stream fallback
            return FileInputStream(file).use { fis ->
                BitmapFactory.decodeStream(fis, null, options)
            }
        } catch (_: Throwable) {
            return null
        }
    }

    /**
     * Decode video thumbnail using MediaMetadataRetriever and ThumbnailUtils.
     */
    private fun decodeVideoThumbnail(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists()) return null

        // Attempt 1: MediaMetadataRetriever (representative keyframe)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            var frame: Bitmap? = retriever.getFrameAtTime()
            if (frame == null) {
                frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frame == null) {
                frame = retriever.getFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST)
            }
            retriever.release()

            if (frame != null) {
                if (frame.width > reqWidth || frame.height > reqHeight) {
                    val scale = Math.min(reqWidth.toFloat() / frame.width, reqHeight.toFloat() / frame.height)
                    val newWidth = Math.max(1, (frame.width * scale).toInt())
                    val newHeight = Math.max(1, (frame.height * scale).toInt())
                    return Bitmap.createScaledBitmap(frame, newWidth, newHeight, true)
                }
                return frame
            }
        } catch (_: Throwable) {
        }

        // Attempt 2: ThumbnailUtils fallback
        try {
            @Suppress("DEPRECATION")
            val thumb = ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
            if (thumb != null) {
                if (thumb.width > reqWidth || thumb.height > reqHeight) {
                    val scale = Math.min(reqWidth.toFloat() / thumb.width, reqHeight.toFloat() / thumb.height)
                    val newWidth = Math.max(1, (thumb.width * scale).toInt())
                    val newHeight = Math.max(1, (thumb.height * scale).toInt())
                    return Bitmap.createScaledBitmap(thumb, newWidth, newHeight, true)
                }
                return thumb
            }
        } catch (_: Throwable) {
        }

        return null
    }

    /**
     * Extract high-resolution icon from APK package archive.
     */
    private fun decodeApkIcon(context: Context, file: File): Bitmap? {
        if (!file.exists()) return null

        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
            val appInfo = pkgInfo.applicationInfo ?: return null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath

            var iconDrawable: Drawable? = null

            // Tier 1: Mount APK resources directly to get the genuine application icon
            try {
                val res = pm.getResourcesForApplication(appInfo)
                if (appInfo.icon != 0) {
                    iconDrawable = res.getDrawable(appInfo.icon, null)
                }
            } catch (_: Throwable) {
            }

            // Tier 2: Fallback to loadIcon
            if (iconDrawable == null) {
                try {
                    iconDrawable = appInfo.loadIcon(pm)
                } catch (_: Throwable) {
                }
            }

            if (iconDrawable == null) return null

            // Extract bitmap from drawable
            if (iconDrawable is BitmapDrawable && iconDrawable.bitmap != null) {
                return iconDrawable.bitmap
            }

            val w = if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 96
            val h = if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 96
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
            iconDrawable.draw(canvas)
            return bmp
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
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}
