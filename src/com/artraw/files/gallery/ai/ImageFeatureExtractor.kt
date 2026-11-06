package com.artraw.files.gallery.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ExtractedFeatures(
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val durationMs: Long,
    val phash: Long,
    val dominantColor: String,
    val colorHex: String,
    val blurScore: Double,
    val isBlurry: Boolean,
    val aestheticScore: Double,
    val detectedTags: List<String>,
    val ocrText: String,
    val description: String,
    val dateTaken: Long
)

data class ColorResult(
    val name: String,
    val hex: String
)

object ImageFeatureExtractor {

    fun extractFeatures(file: File): ExtractedFeatures {
        val pathLower = file.name.lowercase(Locale.ROOT)
        val isVideo = pathLower.endsWith(".mp4") || pathLower.endsWith(".mkv") ||
                pathLower.endsWith(".webm") || pathLower.endsWith(".3gp") ||
                pathLower.endsWith(".mov") || pathLower.endsWith(".avi")

        if (isVideo) {
            return extractVideoFeatures(file)
        } else {
            return extractImageFeatures(file)
        }
    }

    private fun extractImageFeatures(file: File): ExtractedFeatures {
        var width = 0
        var height = 0
        var dateTaken = file.lastModified()

        try {
            val exif = ExifInterface(file.absolutePath)
            width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            val dateStr = exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (dateStr != null) {
                try {
                    val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val parsed = sdf.parse(dateStr)
                    if (parsed != null) dateTaken = parsed.time
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        val sampleBitmap = decodeSampledBitmap(file, 256, 256)
        if (sampleBitmap == null) {
            val tags = guessTagsFromPath(file, "UNKNOWN")
            return ExtractedFeatures(
                width = width,
                height = height,
                isVideo = false,
                durationMs = 0L,
                phash = 0L,
                dominantColor = "UNKNOWN",
                colorHex = "#888888",
                blurScore = 100.0,
                isBlurry = false,
                aestheticScore = 50.0,
                detectedTags = tags,
                ocrText = "",
                description = "Image file: ${file.name}",
                dateTaken = dateTaken
            )
        }

        if (width <= 0) width = sampleBitmap.width
        if (height <= 0) height = sampleBitmap.height

        val phash = calculatePHash(sampleBitmap)
        val colorResult = extractDominantColor(sampleBitmap)
        val blurScore = calculateBlurScore(sampleBitmap)
        val isBlurry = blurScore < 80.0
        val aestheticScore = calculateAestheticScore(sampleBitmap, blurScore, width, height)
        val ocrResult = detectDocumentAndOcr(file, sampleBitmap)
        val tags = detectTagsAndScene(file, sampleBitmap, colorResult.name, ocrResult, width, height)
        val description = generateDescription(file, tags, colorResult.name, isBlurry, aestheticScore)

        if (!sampleBitmap.isRecycled) {
            sampleBitmap.recycle()
        }

        return ExtractedFeatures(
            width = width,
            height = height,
            isVideo = false,
            durationMs = 0L,
            phash = phash,
            dominantColor = colorResult.name,
            colorHex = colorResult.hex,
            blurScore = blurScore,
            isBlurry = isBlurry,
            aestheticScore = aestheticScore,
            detectedTags = tags,
            ocrText = ocrResult,
            description = description,
            dateTaken = dateTaken
        )
    }

    private fun extractVideoFeatures(file: File): ExtractedFeatures {
        var durationMs = 0L
        var width = 0
        var height = 0
        var frame: Bitmap? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
            val wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            width = wStr?.toIntOrNull() ?: 0
            height = hStr?.toIntOrNull() ?: 0

            frame = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }

        val dominant = if (frame != null) extractDominantColor(frame) else ColorResult("UNKNOWN", "#888888")
        val phash = if (frame != null) calculatePHash(frame) else 0L
        val blurScore = if (frame != null) calculateBlurScore(frame) else 100.0
        val isBlurry = blurScore < 75.0
        val aestheticScore = 60.0
        val tags = mutableListOf("video")
        tags.addAll(guessTagsFromPath(file, dominant.name))

        if (frame != null && !frame.isRecycled) {
            frame.recycle()
        }

        return ExtractedFeatures(
            width = width,
            height = height,
            isVideo = true,
            durationMs = durationMs,
            phash = phash,
            dominantColor = dominant.name,
            colorHex = dominant.hex,
            blurScore = blurScore,
            isBlurry = isBlurry,
            aestheticScore = aestheticScore,
            detectedTags = tags.distinct(),
            ocrText = "",
            description = "Video recording (${file.name})",
            dateTaken = file.lastModified()
        )
    }

    /**
     * 64-bit Perceptual difference hash (dHash)
     * Resizes to 9x8, computes gradient across columns.
     */
    fun calculatePHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        val gray = IntArray(72)
        var idx = 0
        for (y in 0 until 8) {
            for (x in 0 until 9) {
                val p = scaled.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                gray[idx++] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = gray[y * 9 + x]
                val right = gray[y * 9 + x + 1]
                if (left > right) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    /**
     * Hamming distance between two 64-bit hashes.
     * 0 = exact duplicate, 1-4 = near duplicate / burst, 5-10 = visually similar.
     */
    fun hammingDistance(h1: Long, h2: Long): Int {
        return java.lang.Long.bitCount(h1 xor h2)
    }

    /**
     * Extracts dominant color by sampling HSV color space.
     */
    fun extractDominantColor(bitmap: Bitmap): ColorResult {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, false)
        val colorCounts = mutableMapOf<String, Int>()
        val hsv = FloatArray(3)
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var count = 0

        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val p = scaled.getPixel(x, y)
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                totalR += r
                totalG += g
                totalB += b
                count++

                Color.colorToHSV(p, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                val name = when {
                    v < 0.18f -> "BLACK"
                    v > 0.85f && s < 0.12f -> "WHITE"
                    s < 0.16f -> "GRAY"
                    h in 0.0f..15.0f || h in 345.0f..360.0f -> "RED"
                    h in 15.0f..45.0f -> "ORANGE"
                    h in 45.0f..70.0f -> "YELLOW"
                    h in 70.0f..160.0f -> "GREEN"
                    h in 160.0f..195.0f -> "CYAN"
                    h in 195.0f..255.0f -> "BLUE"
                    h in 255.0f..290.0f -> "PURPLE"
                    h in 290.0f..345.0f -> "PINK"
                    else -> "GRAY"
                }
                colorCounts[name] = (colorCounts[name] ?: 0) + 1
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        val dominant = colorCounts.maxByOrNull { it.value }?.key ?: "GRAY"
        val avgR = (totalR / max(1, count)).toInt().coerceIn(0, 255)
        val avgG = (totalG / max(1, count)).toInt().coerceIn(0, 255)
        val avgB = (totalB / max(1, count)).toInt().coerceIn(0, 255)
        val hex = String.format("#%02X%02X%02X", avgR, avgG, avgB)

        return ColorResult(dominant, hex)
    }

    /**
     * Blur detection via discrete 3x3 Laplacian variance.
     */
    fun calculateBlurScore(bitmap: Bitmap): Double {
        val w = 96
        val h = 96
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)
        val gray = IntArray(w * h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                gray[idx++] = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        // Apply Laplacian kernel: [ [0,1,0], [1,-4,1], [0,1,0] ]
        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val center = gray[y * w + x]
                val up = gray[(y - 1) * w + x]
                val down = gray[(y + 1) * w + x]
                val left = gray[y * w + (x - 1)]
                val right = gray[y * w + (x + 1)]

                val lap = (up + down + left + right) - (4 * center)
                sum += lap
                sumSq += (lap * lap)
                count++
            }
        }

        if (count == 0) return 100.0
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return max(0.0, variance)
    }

    /**
     * Computes holistic aesthetic quality score (0 - 100)
     */
    fun calculateAestheticScore(bitmap: Bitmap, blurScore: Double, width: Int, height: Int): Double {
        // Sharpness component: 0 - 40 pts
        val sharpnessScore = min(40.0, blurScore / 5.0)

        // Dynamic range & contrast component: 0 - 35 pts
        var minLum = 255
        var maxLum = 0
        val step = max(1, bitmap.width / 16)
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                val p = bitmap.getPixel(x, y)
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum < minLum) minLum = lum
                if (lum > maxLum) maxLum = lum
            }
        }
        val contrastRange = max(0, maxLum - minLum)
        val contrastScore = (contrastRange / 255.0) * 35.0

        // Resolution & aspect score: 0 - 25 pts
        val totalPixels = width.toLong() * height.toLong()
        val resolutionScore = when {
            totalPixels >= 12_000_000L -> 25.0
            totalPixels >= 4_000_000L -> 20.0
            totalPixels >= 2_000_000L -> 15.0
            else -> 10.0
        }

        return min(100.0, max(10.0, sharpnessScore + contrastScore + resolutionScore))
    }

    /**
     * Document & OCR extraction: scans document lines, receipts, keywords and text patterns.
     */
    fun detectDocumentAndOcr(file: File, bitmap: Bitmap): String {
        val extractedKeywords = mutableListOf<String>()
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)

        // Path / file heuristics
        val commonDocTerms = listOf(
            "receipt", "invoice", "bill", "ticket", "voucher", "doc", "pdf",
            "scan", "statement", "order", "tax", "gst", "bank", "token", "aadhar", "pan", "pass"
        )
        for (term in commonDocTerms) {
            if (pathLower.contains(term)) {
                extractedKeywords.add(term)
            }
        }

        // Bitmap text line density analysis
        val w = 64
        val h = 64
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)
        var whitePixels = 0
        var darkPixels = 0
        var highFreqHorizEdges = 0

        for (y in 0 until h) {
            var rowTransitions = 0
            var prevDark = false
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum > 210) whitePixels++
                if (lum < 60) {
                    darkPixels++
                    if (!prevDark) {
                        rowTransitions++
                        prevDark = true
                    }
                } else {
                    prevDark = false
                }
            }
            if (rowTransitions in 3..14) {
                highFreqHorizEdges++
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        // If paper background (mostly white/light) and dense horizontal line transitions
        if ((whitePixels > (w * h * 0.45) && highFreqHorizEdges > 12) || extractedKeywords.isNotEmpty()) {
            if (!extractedKeywords.contains("document")) extractedKeywords.add("document")
            if (highFreqHorizEdges > 18 && !extractedKeywords.contains("receipt")) {
                extractedKeywords.add("receipt")
                extractedKeywords.add("bill")
            }
        }

        return extractedKeywords.joinToString(" ")
    }

    /**
     * Identifies scenes, objects, festivals, and categories.
     */
    fun detectTagsAndScene(
        file: File,
        bitmap: Bitmap,
        dominantColor: String,
        ocrText: String,
        width: Int,
        height: Int
    ): List<String> {
        val tags = mutableListOf<String>()
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)

        // 1. Dominant color as a direct tag
        tags.add(dominantColor.lowercase(Locale.ROOT))

        // 2. Screenshots
        val isScreenAspect = (height > 0 && width > 0) &&
                (abs((height.toFloat() / width) - (20f / 9f)) < 0.35f ||
                        abs((height.toFloat() / width) - (16f / 9f)) < 0.25f)
        if (pathLower.contains("screenshot") || (pathLower.endsWith(".png") && isScreenAspect)) {
            tags.add("screenshot")
            tags.add("screen")
        }

        // 3. Camera photos
        if (pathLower.contains("dcim") || pathLower.contains("camera") || pathLower.contains("img_")) {
            tags.add("camera")
        }

        // 4. Downloads
        if (pathLower.contains("download")) {
            tags.add("download")
        }

        // 5. Nature & Mountains:
        // Top section has sky (blue/cyan/white) + middle/bottom has green/earth/rock
        if (bitmap.height >= 16 && bitmap.width >= 16) {
            val topP = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
            val midP = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            val botP = bitmap.getPixel(bitmap.width / 2, (bitmap.height * 7) / 8)

            val hsvTop = FloatArray(3)
            val hsvMid = FloatArray(3)
            val hsvBot = FloatArray(3)
            Color.colorToHSV(topP, hsvTop)
            Color.colorToHSV(midP, hsvMid)
            Color.colorToHSV(botP, hsvBot)

            val topIsSky = (hsvTop[0] in 160f..260f && hsvTop[1] > 0.15f) || (hsvTop[2] > 0.85f && hsvTop[1] < 0.15f)
            val bottomIsEarth = (hsvBot[0] in 60f..160f) || (hsvBot[0] in 15f..50f && hsvBot[2] < 0.7f)

            if ((topIsSky && bottomIsEarth) || pathLower.contains("mountain") || pathLower.contains("nature") || pathLower.contains("hill") || pathLower.contains("trek")) {
                tags.add("mountain")
                tags.add("nature")
                tags.add("landscape")
                tags.add("outdoor")
            }
        }

        // 6. Pets & Dogs / Cats / Animals
        if (pathLower.contains("dog") || pathLower.contains("puppy") || pathLower.contains("cat") ||
            pathLower.contains("kitten") || pathLower.contains("pet") || pathLower.contains("animal")) {
            tags.add("dog")
            tags.add("pet")
            tags.add("animal")
        }

        // 7. Cars & Vehicles
        if (pathLower.contains("car") || pathLower.contains("auto") || pathLower.contains("vehicle") ||
            pathLower.contains("bike") || pathLower.contains("motorcycle") || pathLower.contains("drive")) {
            tags.add("car")
            tags.add("vehicle")
        }

        // 8. People & Selfies:
        // Check for skin-tone colors in the center (Hue in 10..38, Saturation 0.2..0.68, Value > 0.35)
        var skinPixels = 0
        val sampleSize = 16
        val hsv = FloatArray(3)
        val stepX = max(1, bitmap.width / sampleSize)
        val stepY = max(1, bitmap.height / sampleSize)
        var tested = 0

        for (y in (bitmap.height / 4) until (bitmap.height * 3 / 4) step stepY) {
            for (x in (bitmap.width / 4) until (bitmap.width * 3 / 4) step stepX) {
                Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                if (hsv[0] in 8f..40f && hsv[1] in 0.18f..0.68f && hsv[2] > 0.35f) {
                    skinPixels++
                }
                tested++
            }
        }
        val skinRatio = if (tested > 0) skinPixels.toFloat() / tested else 0f
        if (skinRatio > 0.22f || pathLower.contains("selfie") || pathLower.contains("portrait") || pathLower.contains("face")) {
            tags.add("people")
            tags.add("portrait")
            tags.add("selfie")
        }

        // 9. Documents & Receipts
        if (ocrText.isNotEmpty() || pathLower.contains("doc") || pathLower.contains("bill") || pathLower.contains("receipt")) {
            tags.add("document")
            if (ocrText.contains("receipt") || pathLower.contains("receipt") || pathLower.contains("bill")) {
                tags.add("receipt")
            }
        }

        // 10. Memes
        if (pathLower.contains("meme") || pathLower.contains("sticker") || pathLower.contains("whatsapp") && (width == height)) {
            tags.add("meme")
        }

        // 11. Festivals & Diwali & Night
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = file.lastModified()
        val month = cal.get(java.util.Calendar.MONTH) // 9 = Oct, 10 = Nov
        if (pathLower.contains("diwali") || pathLower.contains("deepavali") || pathLower.contains("festival") || pathLower.contains("fireworks") ||
            ((month == java.util.Calendar.OCTOBER || month == java.util.Calendar.NOVEMBER) && (dominantColor == "BLACK" || dominantColor == "ORANGE" || dominantColor == "YELLOW"))) {
            tags.add("diwali")
            tags.add("festival")
            tags.add("celebration")
        }

        return tags.distinct()
    }

    private fun guessTagsFromPath(file: File, dominantColor: String): List<String> {
        val tags = mutableListOf<String>()
        val p = file.absolutePath.lowercase(Locale.ROOT)
        if (dominantColor != "UNKNOWN") tags.add(dominantColor.lowercase(Locale.ROOT))
        if (p.contains("camera") || p.contains("dcim")) tags.add("camera")
        if (p.contains("screenshot")) tags.add("screenshot")
        if (p.contains("download")) tags.add("download")
        if (p.contains("mountain") || p.contains("nature")) tags.add("mountain")
        if (p.contains("dog") || p.contains("pet")) tags.add("dog")
        if (p.contains("car")) tags.add("car")
        if (p.contains("receipt") || p.contains("bill") || p.contains("doc")) tags.add("document")
        if (p.contains("diwali")) tags.add("diwali")
        return tags.distinct()
    }

    private fun generateDescription(
        file: File,
        tags: List<String>,
        dominantColor: String,
        isBlurry: Boolean,
        aestheticScore: Double
    ): String {
        val sb = StringBuilder()
        when {
            tags.contains("screenshot") -> sb.append("Device screenshot")
            tags.contains("document") -> sb.append("Document or receipt")
            tags.contains("mountain") || tags.contains("nature") -> sb.append("Outdoor landscape & nature photo")
            tags.contains("dog") || tags.contains("pet") -> sb.append("Pet & animal picture")
            tags.contains("car") || tags.contains("vehicle") -> sb.append("Automobile & vehicle photo")
            tags.contains("people") || tags.contains("portrait") -> sb.append("Portrait photo with people")
            tags.contains("diwali") -> sb.append("Diwali festival celebration")
            tags.contains("meme") -> sb.append("Humorous meme image")
            else -> sb.append("Gallery photo (${file.name})")
        }

        if (dominantColor != "UNKNOWN") {
            sb.append(" with vibrant ").append(dominantColor.lowercase(Locale.ROOT)).append(" tones")
        }
        if (isBlurry) {
            sb.append(" • Low sharpness detected")
        } else if (aestheticScore >= 75.0) {
            sb.append(" • High aesthetic clarity")
        }
        return sb.toString()
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var inSampleSize = 1
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565
            return BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Throwable) {
            return null
        }
    }
}
