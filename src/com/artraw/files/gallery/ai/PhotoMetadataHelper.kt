package com.artraw.files.gallery.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * High-performance EXIF & Color Palette extraction engine.
 * Fast, pure Android SDK implementation with zero external dependencies.
 */
object PhotoMetadataHelper {

    data class ExifMetadata(
        val device: String?,
        val aperture: String?,
        val shutterSpeed: String?,
        val iso: String?,
        val focalLength: String?,
        val dateTaken: String?,
        val flash: String?
    )

    data class PaletteResult(
        val dominantColor: Int,
        val dominantHex: String,
        val ambientGlowColor: Int,
        val swatches: List<ColorSwatch>
    )

    data class ColorSwatch(
        val colorInt: Int,
        val hex: String,
        val label: String
    )

    /**
     * Reads EXIF tags directly from the file via native Android ExifInterface.
     */
    fun extractExif(file: File): ExifMetadata {
        if (!file.exists() || !file.canRead()) {
            return ExifMetadata(null, null, null, null, null, null, null)
        }

        return try {
            val exif = ExifInterface(file.absolutePath)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
            val device = when {
                !make.isNullOrEmpty() && !model.isNullOrEmpty() -> {
                    if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
                }
                !model.isNullOrEmpty() -> model
                !make.isNullOrEmpty() -> make
                else -> null
            }

            val apertureVal = exif.getAttribute(ExifInterface.TAG_F_NUMBER)
            val aperture = apertureVal?.toDoubleOrNull()?.let { "f/${String.format(Locale.US, "%.1f", it)}" }

            val expTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
            val shutterSpeed = expTime?.toDoubleOrNull()?.let { sec ->
                if (sec >= 1.0) {
                    "${String.format(Locale.US, "%.1f", sec)}s"
                } else if (sec > 0.0) {
                    val frac = (1.0 / sec).toInt()
                    "1/${frac}s"
                } else null
            }

            val isoVal = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                ?: exif.getAttribute("PhotographicSensitivity")
            val iso = isoVal?.let { "ISO $it" }

            val focalVal = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
            val focal = focalVal?.toDoubleOrNull()?.let { "${String.format(Locale.US, "%.0f", it)}mm" }

            val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

            val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            val flash = if (flashVal != -1) {
                if ((flashVal and 1) != 0) "Flash fired" else "No flash"
            } else null

            ExifMetadata(
                device = device,
                aperture = aperture,
                shutterSpeed = shutterSpeed,
                iso = iso,
                focalLength = focal,
                dateTaken = dateTaken,
                flash = flash
            )
        } catch (_: Throwable) {
            ExifMetadata(null, null, null, null, null, null, null)
        }
    }

    /**
     * Extracts 5-6 distinct color swatches and ambient background tint from a sample bitmap.
     */
    fun extractPalette(bitmap: Bitmap?): PaletteResult {
        if (bitmap == null || bitmap.width == 0 || bitmap.height == 0) {
            val fallback = Color.parseColor("#00F0FF")
            return PaletteResult(
                dominantColor = fallback,
                dominantHex = "#00F0FF",
                ambientGlowColor = Color.parseColor("#2600F0FF"),
                swatches = emptyList()
            )
        }

        // Downsample to 48x48 for sub-millisecond instant quantization
        val sampleSize = 48
        val sample = try {
            Bitmap.createScaledBitmap(bitmap, sampleSize, sampleSize, false)
        } catch (_: Throwable) {
            bitmap
        }

        val width = sample.width
        val height = sample.height
        val pixels = IntArray(width * height)
        sample.getPixels(pixels, 0, width, 0, 0, width, height)

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var totalCount = 0

        // Bucket colors by hue sectors (6 sectors: 0-60, 60-120, etc.)
        val hueBuckets = Array(6) { mutableListOf<Int>() }
        val hsv = FloatArray(3)

        for (c in pixels) {
            val alpha = Color.alpha(c)
            if (alpha < 128) continue

            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            totalR += r
            totalG += g
            totalB += b
            totalCount++

            Color.colorToHSV(c, hsv)
            val s = hsv[1]
            val v = hsv[2]

            // Skip extreme near-black or near-white for swatches
            if (v > 0.15f && v < 0.95f && s > 0.15f) {
                val sector = ((hsv[0] / 60f).toInt()) % 6
                hueBuckets[sector].add(c)
            }
        }

        val dominantInt = if (totalCount > 0) {
            Color.rgb((totalR / totalCount).toInt(), (totalG / totalCount).toInt(), (totalB / totalCount).toInt())
        } else {
            Color.parseColor("#00F0FF")
        }

        val dominantHex = String.format(Locale.US, "#%06X", 0xFFFFFF and dominantInt)
        // Ambient glow has 25% opacity
        val ambientGlow = Color.argb(48, Color.red(dominantInt), Color.green(dominantInt), Color.blue(dominantInt))

        // Create curated swatches
        val swatches = mutableListOf<ColorSwatch>()
        swatches.add(ColorSwatch(dominantInt, dominantHex, "Dominant"))

        val labels = listOf("Vibrant", "Accent", "Warm", "Cool", "Deep")
        var labelIdx = 0

        for (bucket in hueBuckets) {
            if (bucket.isNotEmpty() && swatches.size < 6) {
                // Pick middle color from sector
                val pick = bucket[bucket.size / 2]
                val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and pick)
                val label = labels.getOrElse(labelIdx++) { "Tone" }
                swatches.add(ColorSwatch(pick, hex, label))
            }
        }

        if (sample !== bitmap) {
            sample.recycle()
        }

        return PaletteResult(
            dominantColor = dominantInt,
            dominantHex = dominantHex,
            ambientGlowColor = ambientGlow,
            swatches = swatches
        )
    }
}
