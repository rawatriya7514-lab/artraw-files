package com.artraw.files.util

import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF

/**
 * ImageProcessor — Lightroom-style image adjustments using pure Android framework.
 * Supports: crop, rotate, flip, brightness, contrast, saturation, hue, temperature,
 * shadows, highlights, clarity, and basic filters.
 */
object ImageProcessor {

    /**
     * Apply rotation (degrees: 90, 180, 270) or flip.
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float, flipHorizontal: Boolean = false, flipVertical: Boolean = false): Bitmap {
        val matrix = Matrix()
        if (flipHorizontal) matrix.preScale(-1f, 1f)
        if (flipVertical) matrix.preScale(1f, -1f)
        if (degrees != 0f) matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Crop bitmap to a rectangular region.
     */
    fun cropBitmap(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap {
        val cropWidth = right - left
        val cropHeight = bottom - top
        if (cropWidth <= 0 || cropHeight <= 0) return bitmap
        return Bitmap.createBitmap(bitmap, left.coerceAtLeast(0), top.coerceAtLeast(0),
            cropWidth.coerceAtMost(bitmap.width), cropHeight.coerceAtMost(bitmap.height))
    }

    /**
     * Apply color adjustments: brightness, contrast, saturation, hue, temperature.
     * All params are in range -1.0 to 1.0 except temperature which is Kelvin shift.
     */
    fun adjustColors(
        bitmap: Bitmap,
        brightness: Float = 0f,    // -1 to 1
        contrast: Float = 0f,      // -1 to 1
        saturation: Float = 0f,    // -1 to 1
        hue: Float = 0f,           // -1 to 1
        temperature: Float = 0f    // -1000 to 1000 (K shift)
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        // --- Brightness ---
        if (brightness != 0f) {
            val bm = ColorMatrix()
            bm.setScale(1f + brightness, 1f + brightness, 1f + brightness, 1f)
            cm.postConcat(bm)
        }

        // --- Contrast ---
        if (contrast != 0f) {
            val c = 1f + contrast
            val translate = 0.5f * (1f - c)
            val bm = ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(bm)
        }

        // --- Saturation ---
        if (saturation != 0f) {
            val bm = ColorMatrix()
            bm.setSaturation(1f + saturation)
            cm.postConcat(bm)
        }

        // --- Hue ---
        if (hue != 0f) {
            val bm = ColorMatrix()
            bm.setRotate(0, hue)
            bm.setRotate(1, hue)
            bm.setRotate(2, hue)
            cm.postConcat(bm)
        }

        // --- Temperature (blue/orange shift) ---
        if (temperature != 0f) {
            val bm = ColorMatrix()
            // Warm = more red, less blue; Cool = more blue, less red
            val rShift = temperature * 0.0005f
            val bShift = -temperature * 0.0005f
            bm.setScale(1f + rShift, 1f, 1f + bShift, 1f)
            cm.postConcat(bm)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Apply shadows/highlights adjustment.
     * shadows: -1 to 1 (lift shadows)
     * highlights: -1 to 1 (recover highlights)
     */
    fun adjustShadowsHighlights(bitmap: Bitmap, shadows: Float = 0f, highlights: Float = 0f): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        // Simple lift/gamma curve using lookup via ColorMatrix scales
        // Shadows lift: raise low values
        if (shadows != 0f) {
            val lift = 1f + shadows * 0.5f
            val bm = ColorMatrix()
            bm.setScale(lift, lift, lift, 1f)
            cm.postConcat(bm)
        }

        // Highlights recovery: reduce high values
        if (highlights != 0f) {
            val recover = 1f - highlights * 0.5f
            val bm = ColorMatrix()
            bm.setScale(recover, recover, recover, 1f)
            cm.postConcat(bm)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Apply clarity (mid-tone contrast enhancement).
     */
    fun adjustClarity(bitmap: Bitmap, clarity: Float = 0f): Bitmap {
        if (clarity == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()

        // Clarity = contrast in midtones only
        val c = 1f + clarity * 0.5f
        val translate = 0.25f * (1f - c)
        val bm = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(bm)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /**
     * Apply a preset filter.
     */
    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.NONE -> bitmap
            FilterType.VIVID -> adjustColors(bitmap, saturation = 0.2f, contrast = 0.1f)
            FilterType.MOODY -> adjustColors(bitmap, saturation = -0.1f, contrast = 0.15f, brightness = -0.05f)
            FilterType.WARM -> adjustColors(bitmap, temperature = 400f, saturation = 0.1f)
            FilterType.COOL -> adjustColors(bitmap, temperature = -400f, saturation = 0.05f)
            FilterType.BW -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val width = bitmap.width
                val height = bitmap.height
                val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                output
            }
            FilterType.SEPIA -> {
                val cm = ColorMatrix()
                val array = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                cm.set(array)
                val width = bitmap.width
                val height = bitmap.height
                val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
                val canvas = Canvas(output)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                output
            }
        }
    }

    /**
     * Add text overlay to image.
     */
    fun addTextOverlay(
        bitmap: Bitmap,
        text: String,
        textSize: Float = 48f,
        textColor: Int = -1,
        x: Float = 0f,
        y: Float = 0f
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = textSize
        paint.color = textColor
        paint.textAlign = android.graphics.Paint.Align.LEFT

        paint.color = textColor
        val textBounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawRect(x, y - textBounds.height(), x + textBounds.width(), y, paint)
        canvas.drawText(text, x, y, paint)
        return output
    }

    /**
     * Resize bitmap while maintaining aspect ratio.
     */
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val ratio = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Apply vignette effect (darkened edges).
     */
    fun applyVignette(bitmap: Bitmap, amount: Float = 0.5f): Bitmap {
        if (amount == 0f) return bitmap
        val width = bitmap.width
        val height = bitmap.height
        val output = Bitmap.createBitmap(width, height, Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gradient = android.graphics.RadialGradient(
            width / 2f, height / 2f, Math.max(width, height) * 0.7f,
            intArrayOf(0x00000000, (amount * 255).toInt() shl 24 or 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return output
    }
}

/**
 * Filter presets for the image editor.
 */
enum class FilterType {
    NONE, VIVID, MOODY, WARM, COOL, BW, SEPIA
}

/**
 * Current editing state for an image.
 */
data class EditState(
    var brightness: Float = 0f,
    var contrast: Float = 0f,
    var saturation: Float = 0f,
    var hue: Float = 0f,
    var temperature: Float = 0f,
    var shadows: Float = 0f,
    var highlights: Float = 0f,
    var clarity: Float = 0f,
    var rotation: Float = 0f,
    var flipHorizontal: Boolean = false,
    var flipVertical: Boolean = false,
    var filterType: FilterType = FilterType.NONE,
    var vignette: Float = 0f
)
