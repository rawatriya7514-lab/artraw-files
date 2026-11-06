package com.artraw.files.gallery.model

enum class AiCollectionType(
    val id: String,
    val title: String,
    val description: String,
    val iconEmoji: String,
    val matchingTags: List<String>
) {
    NATURE(
        "nature",
        "Nature & Mountains",
        "Landscapes, mountains, sky, forests and outdoor views",
        "🏔️",
        listOf("mountain", "nature", "landscape", "outdoor", "sky", "forest", "tree", "beach")
    ),
    PETS(
        "pets",
        "Pets & Animals",
        "Dogs, cats, birds and wildlife pictures",
        "🐶",
        listOf("dog", "cat", "pet", "animal", "puppy", "kitten", "bird")
    ),
    VEHICLES(
        "vehicles",
        "Cars & Vehicles",
        "Cars, motorcycles, bikes and transport",
        "🚗",
        listOf("car", "vehicle", "automobile", "bike", "motorcycle", "truck")
    ),
    PEOPLE(
        "people",
        "Portraits & Selfies",
        "People, selfies, family and friends",
        "🤳",
        listOf("people", "portrait", "selfie", "person", "face")
    ),
    DOCUMENTS(
        "documents",
        "Documents & Receipts",
        "Receipts, bills, invoices, IDs, and notes",
        "📄",
        listOf("document", "receipt", "invoice", "bill", "paper", "text", "form", "statement")
    ),
    MEMES(
        "memes",
        "Memes & Fun",
        "Memes, reaction images, and social posts",
        "🎭",
        listOf("meme", "funny", "humor", "joke", "comic")
    ),
    NIGHT(
        "night",
        "Night & Celebrations",
        "Diwali, festivals, city lights, fireworks and night shots",
        "✨",
        listOf("diwali", "festival", "fireworks", "night", "lights", "celebration", "sunset")
    );

    companion object {
        fun fromId(id: String): AiCollectionType? {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) }
        }
    }
}
