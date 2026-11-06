package com.artraw.files.gallery.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.artraw.files.gallery.ai.ExtractedFeatures
import com.artraw.files.gallery.ai.ImageFeatureExtractor
import com.artraw.files.gallery.ai.ParsedAiQuery
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.gallery.model.GalleryCategory
import java.io.File
import java.util.Locale

class AiGalleryDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "artfiles_ai_gallery.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_MEDIA = "ai_media"
        private const val COL_PATH = "path"
        private const val COL_NAME = "name"
        private const val COL_SIZE = "size"
        private const val COL_DATE_MOD = "date_modified"
        private const val COL_DATE_TAKEN = "date_taken"
        private const val COL_WIDTH = "width"
        private const val COL_HEIGHT = "height"
        private const val COL_MIME = "mime_type"
        private const val COL_IS_VIDEO = "is_video"
        private const val COL_DURATION = "duration_ms"
        private const val COL_CATEGORY = "category"
        private const val COL_PHASH = "phash"
        private const val COL_DOMINANT_COLOR = "dominant_color"
        private const val COL_COLOR_HEX = "color_hex"
        private const val COL_BLUR_SCORE = "blur_score"
        private const val COL_IS_BLURRY = "is_blurry"
        private const val COL_AESTHETIC_SCORE = "aesthetic_score"
        private const val COL_TAGS = "detected_tags"
        private const val COL_OCR_TEXT = "ocr_text"
        private const val COL_DESC = "description"
        private const val COL_IS_FAVORITE = "is_favorite"

        @Volatile
        private var instance: AiGalleryDatabase? = null

        fun getInstance(context: Context): AiGalleryDatabase {
            return instance ?: synchronized(this) {
                instance ?: AiGalleryDatabase(context).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MEDIA (
                $COL_PATH TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_SIZE INTEGER NOT NULL,
                $COL_DATE_MOD INTEGER NOT NULL,
                $COL_DATE_TAKEN INTEGER NOT NULL,
                $COL_WIDTH INTEGER NOT NULL,
                $COL_HEIGHT INTEGER NOT NULL,
                $COL_MIME TEXT NOT NULL,
                $COL_IS_VIDEO INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL,
                $COL_CATEGORY TEXT NOT NULL,
                $COL_PHASH INTEGER NOT NULL,
                $COL_DOMINANT_COLOR TEXT NOT NULL,
                $COL_COLOR_HEX TEXT NOT NULL,
                $COL_BLUR_SCORE REAL NOT NULL,
                $COL_IS_BLURRY INTEGER NOT NULL,
                $COL_AESTHETIC_SCORE REAL NOT NULL,
                $COL_TAGS TEXT NOT NULL,
                $COL_OCR_TEXT TEXT NOT NULL,
                $COL_DESC TEXT NOT NULL,
                $COL_IS_FAVORITE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cat ON $TABLE_MEDIA ($COL_CATEGORY)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_phash ON $TABLE_MEDIA ($COL_PHASH)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_color ON $TABLE_MEDIA ($COL_DOMINANT_COLOR)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_date ON $TABLE_MEDIA ($COL_DATE_TAKEN)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_fav ON $TABLE_MEDIA ($COL_IS_FAVORITE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEDIA")
        onCreate(db)
    }

    fun isIndexed(path: String, lastModified: Long): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_DATE_MOD FROM $TABLE_MEDIA WHERE $COL_PATH = ? LIMIT 1",
            arrayOf(path)
        )
        cursor.use {
            if (it.moveToFirst()) {
                val storedMod = it.getLong(0)
                return storedMod == lastModified
            }
        }
        return false
    }

    fun upsertItem(file: File, features: ExtractedFeatures, category: String, isFavorite: Boolean = false) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_PATH, file.absolutePath)
            put(COL_NAME, file.name)
            put(COL_SIZE, file.length())
            put(COL_DATE_MOD, file.lastModified())
            put(COL_DATE_TAKEN, features.dateTaken)
            put(COL_WIDTH, features.width)
            put(COL_HEIGHT, features.height)
            put(COL_MIME, if (features.isVideo) "video/mp4" else if (features.isAudio) "audio/mpeg" else "image/jpeg")
            put(COL_IS_VIDEO, if (features.isVideo) 1 else 0)
            put(COL_DURATION, features.durationMs)
            put(COL_CATEGORY, category)
            put(COL_PHASH, features.phash)
            put(COL_DOMINANT_COLOR, features.dominantColor)
            put(COL_COLOR_HEX, features.colorHex)
            put(COL_BLUR_SCORE, features.blurScore)
            put(COL_IS_BLURRY, if (features.isBlurry) 1 else 0)
            put(COL_AESTHETIC_SCORE, features.aestheticScore)
            put(COL_TAGS, features.detectedTags.joinToString(","))
            put(COL_OCR_TEXT, features.ocrText)
            put(COL_DESC, features.description)
            put(COL_IS_FAVORITE, if (isFavorite) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_MEDIA, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllItems(limit: Int = 2000): List<AiMediaItem> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_MEDIA ORDER BY $COL_DATE_TAKEN DESC LIMIT ?",
            arrayOf(limit.toString())
        )
        return cursorToItems(cursor)
    }

    fun getByCategory(category: GalleryCategory, limit: Int = 2000): List<AiMediaItem> {
        val db = readableDatabase
        val sql = when (category) {
            GalleryCategory.ALL -> "SELECT * FROM $TABLE_MEDIA ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.PHOTOS -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_IS_VIDEO = 0 AND $COL_CATEGORY = 'photos' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.VIDEOS -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_IS_VIDEO = 1 ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.AUDIO -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_CATEGORY = 'audio' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.SCREENSHOTS -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_CATEGORY = 'screenshots' OR $COL_TAGS LIKE '%screenshot%' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.CAMERA -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_CATEGORY = 'camera' OR $COL_TAGS LIKE '%camera%' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.DOWNLOADS -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_CATEGORY = 'downloads' OR $COL_TAGS LIKE '%download%' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.DOCUMENTS -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_CATEGORY = 'documents' OR $COL_TAGS LIKE '%document%' OR $COL_OCR_TEXT != '' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.FAVORITES -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_IS_FAVORITE = 1 ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.RAW_ART -> "SELECT * FROM $TABLE_MEDIA WHERE $COL_AESTHETIC_SCORE >= 80 OR $COL_PATH LIKE '%.raw' OR $COL_PATH LIKE '%.dng' OR $COL_PATH LIKE '%.svg' ORDER BY $COL_DATE_TAKEN DESC LIMIT $limit"
            GalleryCategory.VAULT -> "SELECT * FROM $TABLE_MEDIA WHERE 1=0"
            GalleryCategory.DUPLICATES -> return getDuplicateItems()
            GalleryCategory.COLLECTIONS -> return getAllItems(limit)
        }
        val cursor = db.rawQuery(sql, null)
        return cursorToItems(cursor)
    }

    fun search(parsed: ParsedAiQuery, limit: Int = 500): List<AiMediaItem> {
        if (parsed.isDuplicatesOnly) {
            return getDuplicateItems()
        }

        val db = readableDatabase
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (parsed.isBlurryOnly) {
            conditions.add("($COL_IS_BLURRY = 1 OR $COL_BLUR_SCORE < 75)")
        }

        if (parsed.colorFilter != null) {
            conditions.add("$COL_DOMINANT_COLOR = ?")
            args.add(parsed.colorFilter)
        }

        if (parsed.targetCategory != null) {
            when (parsed.targetCategory) {
                GalleryCategory.VIDEOS -> conditions.add("$COL_IS_VIDEO = 1")
                GalleryCategory.PHOTOS -> conditions.add("($COL_IS_VIDEO = 0 AND $COL_CATEGORY = 'photos')")
                GalleryCategory.AUDIO -> conditions.add("$COL_CATEGORY = 'audio'")
                GalleryCategory.SCREENSHOTS -> conditions.add("($COL_CATEGORY = 'screenshots' OR $COL_TAGS LIKE '%screenshot%')")
                GalleryCategory.DOCUMENTS -> conditions.add("($COL_CATEGORY = 'documents' OR $COL_TAGS LIKE '%document%' OR $COL_OCR_TEXT != '')")
                GalleryCategory.CAMERA -> conditions.add("($COL_CATEGORY = 'camera' OR $COL_TAGS LIKE '%camera%')")
                GalleryCategory.DOWNLOADS -> conditions.add("($COL_CATEGORY = 'downloads' OR $COL_TAGS LIKE '%download%')")
                GalleryCategory.FAVORITES -> conditions.add("$COL_IS_FAVORITE = 1")
                else -> {}
            }
        }

        if (parsed.requiredTags.isNotEmpty()) {
            val tagConditions = parsed.requiredTags.map { tag ->
                args.add("%$tag%")
                args.add("%$tag%")
                "($COL_TAGS LIKE ? OR $COL_NAME LIKE ?)"
            }
            conditions.add("(" + tagConditions.joinToString(" OR ") + ")")
        }

        if (parsed.minDateMs != null) {
            conditions.add("$COL_DATE_TAKEN >= ?")
            args.add(parsed.minDateMs.toString())
        }
        if (parsed.maxDateMs != null) {
            conditions.add("$COL_DATE_TAKEN <= ?")
            args.add(parsed.maxDateMs.toString())
        }

        if (parsed.textSearch != null) {
            conditions.add("($COL_NAME LIKE ? OR $COL_TAGS LIKE ? OR $COL_OCR_TEXT LIKE ? OR $COL_DESC LIKE ?)")
            val term = "%${parsed.textSearch}%"
            args.add(term)
            args.add(term)
            args.add(term)
            args.add(term)
        }

        val whereClause = if (conditions.isNotEmpty()) "WHERE " + conditions.joinToString(" AND ") else ""
        val orderBy = if (parsed.isBestPhotosOnly) "ORDER BY $COL_AESTHETIC_SCORE DESC" else "ORDER BY $COL_DATE_TAKEN DESC"

        val query = "SELECT * FROM $TABLE_MEDIA $whereClause $orderBy LIMIT $limit"
        val cursor = db.rawQuery(query, if (args.isEmpty()) null else args.toTypedArray())
        return cursorToItems(cursor)
    }

    fun getDuplicateItems(): List<AiMediaItem> {
        val all = getAllItems(2000)
        val duplicateList = mutableListOf<AiMediaItem>()
        val groupedByHash = mutableMapOf<Long, MutableList<AiMediaItem>>()

        for (item in all) {
            if (item.phash != 0L) {
                groupedByHash.getOrPut(item.phash) { mutableListOf() }.add(item)
            }
        }

        val accountedPaths = mutableSetOf<String>()
        for ((_, group) in groupedByHash) {
            if (group.size > 1) {
                duplicateList.addAll(group)
                group.forEach { accountedPaths.add(it.path) }
            }
        }

        for (i in all.indices) {
            val itemA = all[i]
            if (itemA.phash == 0L || accountedPaths.contains(itemA.path)) continue
            val nearGroup = mutableListOf<AiMediaItem>()
            for (j in i + 1 until all.size) {
                val itemB = all[j]
                if (itemB.phash == 0L || accountedPaths.contains(itemB.path)) continue
                val dist = ImageFeatureExtractor.hammingDistance(itemA.phash, itemB.phash)
                if (dist in 1..3) {
                    nearGroup.add(itemB)
                }
            }
            if (nearGroup.isNotEmpty()) {
                duplicateList.add(itemA)
                duplicateList.addAll(nearGroup)
                accountedPaths.add(itemA.path)
                nearGroup.forEach { accountedPaths.add(it.path) }
            }
        }

        return duplicateList
    }

    fun getSimilarItems(source: AiMediaItem, limit: Int = 30): List<AiMediaItem> {
        val all = getAllItems(1500)
        return all.filter { it.path != source.path && it.phash != 0L }
            .map { item ->
                val dist = ImageFeatureExtractor.hammingDistance(source.phash, item.phash)
                val colorMatch = if (source.dominantColor == item.dominantColor) 2 else 0
                val tagMatches = source.detectedTags.intersect(item.detectedTags.toSet()).size
                val similarityScore = (64 - dist) + (colorMatch * 5) + (tagMatches * 8)
                item to similarityScore
            }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    fun toggleFavorite(path: String): Boolean {
        val db = writableDatabase
        var newState = false
        val cursor = db.rawQuery("SELECT $COL_IS_FAVORITE FROM $TABLE_MEDIA WHERE $COL_PATH = ?", arrayOf(path))
        cursor.use {
            if (it.moveToFirst()) {
                val cur = it.getInt(0)
                newState = cur == 0
                val cv = ContentValues().apply {
                    put(COL_IS_FAVORITE, if (newState) 1 else 0)
                }
                db.update(TABLE_MEDIA, cv, "$COL_PATH = ?", arrayOf(path))
            }
        }
        return newState
    }

    fun deleteItem(path: String) {
        val db = writableDatabase
        db.delete(TABLE_MEDIA, "$COL_PATH = ?", arrayOf(path))
    }

    fun getTotalCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_MEDIA", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    private fun cursorToItems(cursor: android.database.Cursor): List<AiMediaItem> {
        val list = mutableListOf<AiMediaItem>()
        cursor.use { c ->
            val pathIdx = c.getColumnIndex(COL_PATH)
            val nameIdx = c.getColumnIndex(COL_NAME)
            val sizeIdx = c.getColumnIndex(COL_SIZE)
            val modIdx = c.getColumnIndex(COL_DATE_MOD)
            val takenIdx = c.getColumnIndex(COL_DATE_TAKEN)
            val widthIdx = c.getColumnIndex(COL_WIDTH)
            val heightIdx = c.getColumnIndex(COL_HEIGHT)
            val mimeIdx = c.getColumnIndex(COL_MIME)
            val isVidIdx = c.getColumnIndex(COL_IS_VIDEO)
            val durIdx = c.getColumnIndex(COL_DURATION)
            val catIdx = c.getColumnIndex(COL_CATEGORY)
            val phashIdx = c.getColumnIndex(COL_PHASH)
            val domColorIdx = c.getColumnIndex(COL_DOMINANT_COLOR)
            val hexIdx = c.getColumnIndex(COL_COLOR_HEX)
            val blurIdx = c.getColumnIndex(COL_BLUR_SCORE)
            val isBlurIdx = c.getColumnIndex(COL_IS_BLURRY)
            val aesIdx = c.getColumnIndex(COL_AESTHETIC_SCORE)
            val tagsIdx = c.getColumnIndex(COL_TAGS)
            val ocrIdx = c.getColumnIndex(COL_OCR_TEXT)
            val descIdx = c.getColumnIndex(COL_DESC)
            val favIdx = c.getColumnIndex(COL_IS_FAVORITE)

            while (c.moveToNext()) {
                val p = c.getString(pathIdx)
                val file = File(p)
                if (!file.exists()) continue

                val tagsRaw = c.getString(tagsIdx) ?: ""
                val tagsList = if (tagsRaw.isNotBlank()) tagsRaw.split(",") else emptyList()
                val cat = c.getString(catIdx) ?: "photos"
                val isVideo = c.getInt(isVidIdx) == 1
                val isAudio = cat == "audio"
                val isDoc = cat == "documents"

                list.add(
                    AiMediaItem(
                        file = file,
                        name = c.getString(nameIdx),
                        path = p,
                        size = c.getLong(sizeIdx),
                        dateModified = c.getLong(modIdx),
                        dateTaken = c.getLong(takenIdx),
                        width = c.getInt(widthIdx),
                        height = c.getInt(heightIdx),
                        mimeType = c.getString(mimeIdx),
                        isVideo = isVideo,
                        isAudio = isAudio,
                        isDocument = isDoc,
                        durationMs = c.getLong(durIdx),
                        category = cat,
                        phash = c.getLong(phashIdx),
                        dominantColor = c.getString(domColorIdx),
                        colorHex = c.getString(hexIdx),
                        blurScore = c.getDouble(blurIdx),
                        isBlurry = c.getInt(isBlurIdx) == 1,
                        aestheticScore = c.getDouble(aesIdx),
                        detectedTags = tagsList,
                        ocrText = c.getString(ocrIdx),
                        description = c.getString(descIdx),
                        isFavorite = c.getInt(favIdx) == 1
                    )
                )
            }
        }
        return list
    }
}
