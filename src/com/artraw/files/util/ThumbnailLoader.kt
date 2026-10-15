package com.artraw.files.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.widget.ImageView
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import java.io.File
import java.util.concurrent.Executors

/**
 * High-performance, memory-conscious thumbnail loader for Gallery-style media browsing.
 * Uses an in-memory LruCache and thread pool to deliver buttery-smooth 60/120fps scrolling.
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

    fun loadThumbnail(imageView: ImageView, item: FileItem, targetSize: Int = 180, defaultIconRes: Int) {
        if (item.isDirectory || (item.category != FileCategory.IMAGES && item.category != FileCategory.VIDEOS)) {
            imageView.tag = null
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
            imageView.setImageResource(defaultIconRes)
            return
        }

        val path = item.file.absolutePath
        val cached = memoryCache.get(path)
        if (cached != null) {
            imageView.tag = path
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageBitmap(cached)
            return
        }

        // Default placeholder while background decoding
        imageView.tag = path
        imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        imageView.setImageResource(defaultIconRes)

        executor.execute {
            val bitmap = try {
                if (item.category == FileCategory.IMAGES) {
                    decodeSampledBitmap(item.file, targetSize, targetSize)
                } else if (item.category == FileCategory.VIDEOS) {
                    ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
                } else null
            } catch (_: Throwable) {
                null
            }

            if (bitmap != null) {
                memoryCache.put(path, bitmap)
                mainHandler.post {
                    if (imageView.tag == path) {
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565
        return BitmapFactory.decodeFile(file.absolutePath, options)
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
