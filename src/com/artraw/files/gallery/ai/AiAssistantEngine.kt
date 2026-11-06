package com.artraw.files.gallery.ai

import android.content.Context
import com.artraw.files.R
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.util.Formatters
import java.util.Locale

data class AssistantAction(
    val id: String,
    val label: String,
    val iconRes: Int
)

data class AssistantResult(
    val query: String,
    val answer: String,
    val items: List<AiMediaItem>,
    val actions: List<AssistantAction> = emptyList()
)

object AiAssistantEngine {

    fun ask(context: Context, query: String): AssistantResult {
        val db = AiGalleryDatabase.getInstance(context)
        val qLower = query.trim().lowercase(Locale.ROOT)
        val parsed = AiQueryParser.parse(query)

        // 1. "Find my best photos"
        if (parsed.isBestPhotosOnly || qLower.contains("best") || qLower.contains("top")) {
            val best = db.search(parsed.copy(isBestPhotosOnly = true), limit = 40)
            val answer = if (best.isNotEmpty()) {
                "Found ${best.size} of your best photos rated highest for sharpness, dynamic contrast, and lighting! Here are the top selections:"
            } else {
                "No photos analyzed yet. Let the gallery index your media to score aesthetics and sharpness."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = best,
                actions = listOf(
                    AssistantAction("fav_all", "Add to Favorites", R.drawable.ic_star_filled)
                )
            )
        }

        // 2. "Find blurry pictures"
        if (parsed.isBlurryOnly || qLower.contains("blurry") || qLower.contains("unsharp")) {
            val blurry = db.search(parsed.copy(isBlurryOnly = true), limit = 50)
            val totalSize = blurry.sumOf { it.size }
            val answer = if (blurry.isNotEmpty()) {
                "Found ${blurry.size} blurry photos (${Formatters.formatFileSize(totalSize)}) with low edge sharpness. You can safely review or remove them."
            } else {
                "Great news! No blurry or degraded photos were detected in your gallery."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = blurry,
                actions = if (blurry.isNotEmpty()) listOf(
                    AssistantAction("clean_blurry", "Review & Clean", R.drawable.ic_delete)
                ) else emptyList()
            )
        }

        // 3. "Find duplicate photos"
        if (parsed.isDuplicatesOnly || qLower.contains("duplicate") || qLower.contains("copies")) {
            val duplicates = db.getDuplicateItems()
            val totalSize = duplicates.sumOf { it.size }
            val answer = if (duplicates.isNotEmpty()) {
                "Identified ${duplicates.size} duplicate photos occupying approx. ${Formatters.formatFileSize(totalSize)}. Free up space by keeping the best copy."
            } else {
                "No duplicate photos found! Your gallery is completely deduplicated."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = duplicates,
                actions = if (duplicates.isNotEmpty()) listOf(
                    AssistantAction("manage_duplicates", "Clean Duplicates", R.drawable.ic_delete)
                ) else emptyList()
            )
        }

        // 4. "Organize my screenshots"
        if (qLower.contains("screenshot") || qLower.contains("screenshots")) {
            val screenshots = db.search(parsed, limit = 100)
            val answer = if (screenshots.isNotEmpty()) {
                "Found ${screenshots.size} screenshots on your device. Most recent ones are displayed below for easy management."
            } else {
                "No screenshots found in your media storage."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = screenshots,
                actions = listOf(
                    AssistantAction("select_screenshots", "Select All", R.drawable.ic_select_all)
                )
            )
        }

        // 5. "Show all photos from Diwali" / Celebrations
        if (qLower.contains("diwali") || qLower.contains("deepavali") || qLower.contains("festival") || qLower.contains("fireworks")) {
            val festive = db.search(parsed, limit = 50)
            val answer = if (festive.isNotEmpty()) {
                "Found ${festive.size} festive Diwali & celebration photos with night lights, sparklers, and golden hues!"
            } else {
                "No photos tagged with Diwali or festival themes were found. Try searching by month or color (e.g. 'orange lights')."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = festive
            )
        }

        // 6. "Find similar images"
        if (parsed.isSimilarOnly || qLower.contains("similar")) {
            val all = db.getAllItems(10)
            val source = all.firstOrNull()
            val similar = if (source != null) db.getSimilarItems(source, 20) else emptyList()
            val answer = if (similar.isNotEmpty()) {
                "Found ${similar.size} visually similar images matching color harmony and compositional layout."
            } else {
                "Select any photo first to discover visually similar shots."
            }
            return AssistantResult(
                query = query,
                answer = answer,
                items = similar
            )
        }

        // 7. General Natural Language Search (e.g. "photos with mountains", "red car", "dog", "documents")
        val results = db.search(parsed, limit = 80)
        val answer = if (results.isNotEmpty()) {
            val subject = when {
                parsed.requiredTags.isNotEmpty() -> parsed.requiredTags.joinToString(", ")
                parsed.colorFilter != null -> "${parsed.colorFilter.lowercase(Locale.ROOT)} colored items"
                parsed.textSearch != null -> "'${parsed.textSearch}'"
                else -> "your search"
            }
            "Found ${results.size} matches for $subject in your gallery!"
        } else {
            "No matching photos found for \"$query\". Try searching by scene (mountains, nature), object (dog, car), or color."
        }

        return AssistantResult(
            query = query,
            answer = answer,
            items = results
        )
    }
}
