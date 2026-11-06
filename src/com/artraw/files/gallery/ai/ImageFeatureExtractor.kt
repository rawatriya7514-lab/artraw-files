package com.artraw.files.gallery.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ExtractedFeatures(
    val width: Int,
    val height: Int,
    val isVideo: Boolean,
    val isAudio: Boolean = false,
    val isDocument: Boolean = false,
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

        val isAudio = pathLower.endsWith(".mp3") || pathLower.endsWith(".m4a") ||
                pathLower.endsWith(".wav") || pathLower.endsWith(".flac") ||
                pathLower.endsWith(".aac") || pathLower.endsWith(".ogg")

        val isDoc = pathLower.endsWith(".pdf") || pathLower.endsWith(".doc") ||
                pathLower.endsWith(".docx") || pathLower.endsWith(".txt") ||
                pathLower.endsWith(".xls") || pathLower.endsWith(".xlsx") ||
                pathLower.endsWith(".ppt") || pathLower.endsWith(".pptx")

        return when {
            isVideo -> extractVideoFeatures(file)
            isAudio -> extractAudioFeatures(file)
            isDoc -> extractDocumentFeatures(file)
            else -> extractImageFeatures(file)
        }
    }

    private fun extractAudioFeatures(file: File): ExtractedFeatures {
        var durationMs = 0L
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
        } finally {
            try { retriever.release() } catch (_: Throwable) {}
        }

        val tags = mutableListOf("audio", "music", "song", "mp3")
        tags.addAll(guessTagsFromPath(file, "CYAN"))

        return ExtractedFeatures(
            width = 0,
            height = 0,
            isVideo = false,
            isAudio = true,
            isDocument = false,
            durationMs = durationMs,
            phash = 0L,
            dominantColor = "CYAN",
            colorHex = "#06B6D4",
            blurScore = 100.0,
            isBlurry = false,
            aestheticScore = 70.0,
            detectedTags = tags.distinct(),
            ocrText = "",
            description = "Audio recording (${file.name})",
            dateTaken = file.lastModified()
        )
    }

    private fun extractDocumentFeatures(file: File): ExtractedFeatures {
        val tags = mutableListOf("document", "file", "text")
        val lower = file.name.lowercase(Locale.ROOT)
        if (lower.endsWith(".pdf")) tags.add("pdf")
        if (lower.contains("receipt") || lower.contains("bill") || lower.contains("invoice")) {
            tags.add("receipt")
            tags.add("bill")
        }

        return ExtractedFeatures(
            width = 0,
            height = 0,
            isVideo = false,
            isAudio = false,
            isDocument = true,
            durationMs = 0L,
            phash = 0L,
            dominantColor = "WHITE",
            colorHex = "#F8FAFC",
            blurScore = 100.0,
            isBlurry = false,
            aestheticScore = 65.0,
            detectedTags = tags.distinct(),
            ocrText = file.nameWithoutExtension,
            description = "Document: ${file.name}",
            dateTaken = file.lastModified()
        )
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

        val sampleBitmap = decodeSampledBitmap(file, 128, 128)
        if (sampleBitmap == null) {
            val tags = guessTagsFromPath(file, "UNKNOWN")
            return ExtractedFeatures(
                width = width,
                height = height,
                isVideo = false,
                isAudio = false,
                isDocument = false,
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
        val isBlurry = blurScore < 75.0
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
            isAudio = false,
            isDocument = tags.contains("document"),
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
        val isBlurry = blurScore < 70.0
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
            isAudio = false,
            isDocument = false,
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

    fun hammingDistance(h1: Long, h2: Long): Int {
        return java.lang.Long.bitCount(h1 xor h2)
    }

    fun extractDominantColor(bitmap: Bitmap): ColorResult {
        val scaled = Bitmap.createScaledBitmap(bitmap, 20, 20, false)
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

    fun calculateBlurScore(bitmap: Bitmap): Double {
        val w = 64
        val h = 64
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

    fun calculateAestheticScore(bitmap: Bitmap, blurScore: Double, width: Int, height: Int): Double {
        val sharpnessScore = min(40.0, blurScore / 5.0)
        var minLum = 255
        var maxLum = 0
        val step = max(1, bitmap.width / 12)
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
        val totalPixels = width.toLong() * height.toLong()
        val resolutionScore = when {
            totalPixels >= 8_000_000L -> 25.0
            totalPixels >= 2_000_000L -> 20.0
            else -> 15.0
        }

        return min(100.0, max(10.0, sharpnessScore + contrastScore + resolutionScore))
    }

    fun detectDocumentAndOcr(file: File, bitmap: Bitmap): String {
        val extractedKeywords = mutableListOf<String>()
        val pathLower = file.absolutePath.lowercase(Locale.ROOT)

        val commonDocTerms = listOf(
            "receipt", "invoice", "bill", "ticket", "voucher", "doc", "pdf",
            "scan", "statement", "order", "tax", "gst", "bank", "token", "aadhar", "pan"
        )
        for (term in commonDocTerms) {
            if (pathLower.contains(term)) {
                extractedKeywords.add(term)
            }
        }

        val w = 32
        val h = 32
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, false)
        var whitePixels = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = scaled.getPixel(x, y)
                val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
                if (lum > 210) whitePixels++
            }
        }
        if (scaled != bitmap && !scaled.isRecycled) {
            scaled.recycle()
        }

        if (whitePixels > (w * h * 0.5) || extractedKeywords.isNotEmpty()) {
            if (!extractedKeywords.contains("document")) extractedKeywords.add("document")
        }

        return extractedKeywords.joinToString(" ")
    }

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

        if (dominantColor != "UNKNOWN") {
            tags.add(dominantColor.lowercase(Locale.ROOT))
        }

        if (pathLower.contains("screenshot") || pathLower.endsWith(".png")) {
            tags.add("screenshot")
        }

        if (pathLower.contains("dcim") || pathLower.contains("camera") || pathLower.contains("img_")) {
            tags.add("camera")
        }

        if (pathLower.contains("download")) {
            tags.add("download")
        }

        if (bitmap.height >= 8 && bitmap.width >= 8) {
            val topP = bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
            val botP = bitmap.getPixel(bitmap.width / 2, (bitmap.height * 7) / 8)

            val hsvTop = FloatArray(3)
            val hsvBot = FloatArray(3)
            Color.colorToHSV(topP, hsvTop)
            Color.colorToHSV(botP, hsvBot)

            val topIsSky = (hsvTop[0] in 160f..260f && hsvTop[1] > 0.15f) || (hsvTop[2] > 0.85f && hsvTop[1] < 0.15f)
            val bottomIsEarth = (hsvBot[0] in 60f..160f) || (hsvBot[0] in 15f..50f && hsvBot[2] < 0.7f)

            if ((topIsSky && bottomIsEarth) || pathLower.contains("mountain") || pathLower.contains("nature") || pathLower.contains("hill")) {
                tags.add("mountain")
                tags.add("nature")
                tags.add("landscape")
            }
        }

        if (pathLower.contains("dog") || pathLower.contains("puppy") || pathLower.contains("cat") ||
            pathLower.contains("pet") || pathLower.contains("animal")) {
            tags.add("dog")
            tags.add("pet")
            tags.add("animal")
        }

        if (pathLower.contains("car") || pathLower.contains("vehicle") || pathLower.contains("bike")) {
            tags.add("car")
            tags.add("vehicle")
        }

        if (pathLower.contains("selfie") || pathLower.contains("portrait") || pathLower.contains("person")) {
            tags.add("people")
            tags.add("portrait")
            tags.add("selfie")
        }

        if (ocrText.isNotEmpty() || pathLower.contains("doc") || pathLower.contains("receipt") || pathLower.contains("bill")) {
            tags.add("document")
            if (ocrText.contains("receipt") || pathLower.contains("receipt") || pathLower.contains("bill")) {
                tags.add("receipt")
            }
        }

        if (pathLower.contains("meme") || (pathLower.contains("whatsapp") && width == height)) {
            tags.add("meme")
        }

        if (pathLower.contains("diwali") || pathLower.contains("festival") || pathLower.contains("fireworks")) {
            tags.add("diwali")
            tags.add("festival")
        }

        return tags.distinct()
    }

    fun guessTagsFromPath(file: File, dominantColor: String): List<String> {
        val tags = mutableListOf<String>()
        val p = file.absolutePath.lowercase(Locale.ROOT)
        if (dominantColor != "UNKNOWN") tags.add(dominantColor.lowercase(Locale.ROOT))
        if (p.contains("camera") || p.contains("dcim")) tags.add("camera")
        if (p.contains("screenshot")) tags.add("screenshot")
        if (p.contains("download")) tags.add("download")
        if (p.contains("mountain") || p.contains("nature")) tags.add("mountain")
        if (p.contains("dog") || p.contains("pet")) tags.add("dog")
        if (p.contains("car")) tags.add("car")
        if (p.contains("receipt") || p.contains("bill") || p.contains("doc") || p.endsWith(".pdf")) tags.add("document")
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
            sb.append(" with ").append(dominantColor.lowercase(Locale.ROOT)).append(" color tones")
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
