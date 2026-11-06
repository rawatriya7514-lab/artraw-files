package com.artraw.files.gallery.ai

import com.artraw.files.gallery.model.GalleryCategory
import java.util.Calendar
import java.util.Locale

data class ParsedAiQuery(
    val rawQuery: String,
    val targetCategory: GalleryCategory? = null,
    val requiredTags: List<String> = emptyList(),
    val colorFilter: String? = null,
    val isBlurryOnly: Boolean = false,
    val isBestPhotosOnly: Boolean = false,
    val isDuplicatesOnly: Boolean = false,
    val isSimilarOnly: Boolean = false,
    val minDateMs: Long? = null,
    val maxDateMs: Long? = null,
    val textSearch: String? = null
)

object AiQueryParser {

    private val COLOR_KEYWORDS = mapOf(
        "red" to "RED",
        "blue" to "BLUE",
        "green" to "GREEN",
        "yellow" to "YELLOW",
        "orange" to "ORANGE",
        "purple" to "PURPLE",
        "pink" to "PINK",
        "white" to "WHITE",
        "black" to "BLACK",
        "gray" to "GRAY",
        "grey" to "GRAY",
        "cyan" to "CYAN"
    )

    private val TAG_SYNONYMS = mapOf(
        "mountain" to listOf("mountain", "nature", "landscape", "hill"),
        "mountains" to listOf("mountain", "nature", "landscape", "hill"),
        "hill" to listOf("mountain", "landscape"),
        "nature" to listOf("nature", "mountain", "forest", "landscape"),
        "dog" to listOf("dog", "pet", "animal", "puppy"),
        "dogs" to listOf("dog", "pet", "animal", "puppy"),
        "puppy" to listOf("dog", "pet", "puppy"),
        "cat" to listOf("cat", "pet", "animal", "kitten"),
        "cats" to listOf("cat", "pet", "kitten"),
        "pet" to listOf("dog", "cat", "pet", "animal"),
        "pets" to listOf("dog", "cat", "pet", "animal"),
        "animal" to listOf("dog", "cat", "pet", "animal"),
        "car" to listOf("car", "vehicle", "automobile"),
        "cars" to listOf("car", "vehicle", "automobile"),
        "vehicle" to listOf("car", "vehicle"),
        "bike" to listOf("bike", "motorcycle", "vehicle"),
        "document" to listOf("document", "receipt", "invoice", "bill"),
        "documents" to listOf("document", "receipt", "invoice", "bill"),
        "receipt" to listOf("receipt", "bill", "document"),
        "receipts" to listOf("receipt", "bill", "document"),
        "invoice" to listOf("invoice", "bill", "document"),
        "bill" to listOf("bill", "receipt", "document"),
        "bills" to listOf("bill", "receipt", "document"),
        "selfie" to listOf("selfie", "portrait", "people"),
        "selfies" to listOf("selfie", "portrait", "people"),
        "people" to listOf("people", "portrait", "selfie"),
        "portrait" to listOf("portrait", "people", "selfie"),
        "meme" to listOf("meme", "funny"),
        "memes" to listOf("meme", "funny"),
        "diwali" to listOf("diwali", "festival", "celebration", "fireworks"),
        "deepavali" to listOf("diwali", "festival"),
        "festival" to listOf("diwali", "festival", "celebration"),
        "fireworks" to listOf("fireworks", "diwali", "celebration")
    )

    fun parse(raw: String): ParsedAiQuery {
        val q = raw.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return ParsedAiQuery(raw)

        var category: GalleryCategory? = null
        var color: String? = null
        var blurryOnly = false
        var bestOnly = false
        var dupOnly = false
        var similarOnly = false
        var minDate: Long? = null
        var maxDate: Long? = null
        val tags = mutableListOf<String>()

        // 1. Direct intent detection
        if (q.contains("duplicate") || q.contains("copies") || q.contains("same photo")) {
            dupOnly = true
            category = GalleryCategory.DUPLICATES
        }
        if (q.contains("blurry") || q.contains("blur") || q.contains("unsharp") || q.contains("shaky")) {
            blurryOnly = true
        }
        if (q.contains("best") || q.contains("highest quality") || q.contains("top photo") || q.contains("favorite")) {
            bestOnly = true
        }
        if (q.contains("similar") || q.contains("like this") || q.contains("matching")) {
            similarOnly = true
        }

        // 2. Category Keywords
        if (q.contains("screenshot") || q.contains("screenshots")) {
            category = GalleryCategory.SCREENSHOTS
            tags.add("screenshot")
        } else if (q.contains("video") || q.contains("videos") || q.contains("recording")) {
            category = GalleryCategory.VIDEOS
        } else if (q.contains("document") || q.contains("documents") || q.contains("receipt") || q.contains("bill") || q.contains("invoice")) {
            category = GalleryCategory.DOCUMENTS
            tags.add("document")
        } else if (q.contains("camera") || q.contains("clicks")) {
            category = GalleryCategory.CAMERA
        } else if (q.contains("download") || q.contains("downloads")) {
            category = GalleryCategory.DOWNLOADS
        }

        // 3. Color detection
        val words = q.split(Regex("[\\s,;:.?!]+")).filter { it.isNotBlank() }
        for (w in words) {
            val matchedColor = COLOR_KEYWORDS[w]
            if (matchedColor != null) {
                color = matchedColor
                break
            }
        }

        // 4. Object & Scene detection
        for (w in words) {
            val mapped = TAG_SYNONYMS[w]
            if (mapped != null) {
                tags.addAll(mapped)
            }
        }

        // 5. Date parsing
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        if (q.contains("last month")) {
            cal.add(Calendar.MONTH, -1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            minDate = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            maxDate = cal.timeInMillis
        } else if (q.contains("yesterday")) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            minDate = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            maxDate = cal.timeInMillis
        } else if (q.contains("last week")) {
            minDate = now - (7L * 24 * 3600 * 1000)
            maxDate = now
        } else if (q.contains("diwali")) {
            tags.add("diwali")
        }

        return ParsedAiQuery(
            rawQuery = raw,
            targetCategory = category,
            requiredTags = tags.distinct(),
            colorFilter = color,
            isBlurryOnly = blurryOnly,
            isBestPhotosOnly = bestOnly,
            isDuplicatesOnly = dupOnly,
            isSimilarOnly = similarOnly,
            minDateMs = minDate,
            maxDateMs = maxDate,
            textSearch = if (tags.isEmpty() && color == null && !blurryOnly && !bestOnly) raw else null
        )
    }
}
