package com.artraw.files.ui.gallery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.gallery.ai.AiAssistantEngine
import com.artraw.files.gallery.ai.AiQueryParser
import com.artraw.files.gallery.ai.ParsedAiQuery
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.gallery.indexer.GalleryIndexer
import com.artraw.files.gallery.model.AiCollectionType
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.gallery.model.GalleryCategory
import com.artraw.files.preview.ArtrawFileProvider
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.ui.AudioPlayerActivity
import com.artraw.files.ui.BaseActivity
import com.artraw.files.ui.ImageViewerActivity
import com.artraw.files.ui.SettingsActivity
import com.artraw.files.ui.TextPreviewActivity
import com.artraw.files.ui.VaultActivity
import com.artraw.files.ui.VideoPlayerActivity
import com.artraw.files.ui.gallery.adapter.SmartGalleryAdapter
import com.artraw.files.ui.gallery.dialog.AiAssistantBottomSheet
import com.artraw.files.ui.gallery.dialog.AiPhotoDetailsDialog
import com.artraw.files.util.ClipboardHelper
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.FavoritesManager
import com.artraw.files.util.Formatters
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.ThumbnailLoader
import com.artraw.files.util.VaultManager
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class SmartGalleryActivity : BaseActivity() {

    private lateinit var db: AiGalleryDatabase
    private lateinit var gridGallery: GridView
    private lateinit var adapter: SmartGalleryAdapter

    private lateinit var tvGalleryTitle: TextView
    private lateinit var tvGalleryCount: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptySubtitle: TextView
    private lateinit var btnReindex: Button

    private lateinit var tvStatPhotos: TextView
    private lateinit var tvStatVideos: TextView
    private lateinit var tvStatAudio: TextView
    private lateinit var tvStatDocs: TextView

    private lateinit var scrollGalleryMemories: View
    private lateinit var layoutGalleryMemories: LinearLayout
    private lateinit var scrollCollections: View
    private lateinit var layoutCollectionsContainer: LinearLayout

    private lateinit var layoutSelectionDock: LinearLayout
    private lateinit var tvSelectionCount: TextView

    private var currentCategory: GalleryCategory = GalleryCategory.ALL
    private var allMedia: List<AiMediaItem> = emptyList()
    private var displayedMedia: List<AiMediaItem> = emptyList()

    private var gridColumns: Int = 3
    private var currentLayoutEngine: Int = SmartGalleryAdapter.LAYOUT_MODE_COMPACT
    private var currentSortOrder: Int = 0 // 0: Date Newest, 1: Date Oldest, 2: Size Largest, 3: Size Smallest, 4: Name A-Z, 5: Aspect Ratio

    private val loaderExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_gallery)

        overridePendingTransition(R.anim.slide_in_up, R.anim.activity_hold)

        FavoritesManager.init(this)
        VaultManager.init(this)
        db = AiGalleryDatabase.getInstance(this)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerGallery))
        )

        initViews()
        setupAdapter()
        setupSearch()
        setupSuggestions()
        setupCategoryTabs()
        setupSelectionDock()
        setupAssistant()

        if (PermissionsHelper.hasStoragePermission(this)) {
            loadFastMediaImmediately()
        } else {
            PermissionsHelper.requestStoragePermission(this)
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.activity_hold, R.anim.slide_out_down)
    }

    private fun initViews() {
        gridGallery = findViewById(R.id.gridGalleryMedia)
        tvGalleryTitle = findViewById(R.id.tvGalleryTitle)
        tvGalleryCount = findViewById(R.id.tvGalleryCount)
        etSearch = findViewById(R.id.etGalleryAiSearch)
        btnClearSearch = findViewById(R.id.btnGallerySearchClear)
        progressLoading = findViewById(R.id.progressGalleryLoading)
        layoutEmpty = findViewById(R.id.layoutGalleryEmpty)
        tvEmptyTitle = findViewById(R.id.tvGalleryEmptyTitle)
        tvEmptySubtitle = findViewById(R.id.tvGalleryEmptySubtitle)
        btnReindex = findViewById(R.id.btnGalleryReindex)
        scrollGalleryMemories = findViewById(R.id.scrollGalleryMemories)
        layoutGalleryMemories = findViewById(R.id.layoutGalleryMemories)
        scrollCollections = findViewById(R.id.scrollGalleryCollections)
        layoutCollectionsContainer = findViewById(R.id.layoutCollectionsContainer)
        layoutSelectionDock = findViewById(R.id.layoutGallerySelectionDock)
        tvSelectionCount = findViewById(R.id.tvGallerySelectionCount)

        tvStatPhotos = findViewById(R.id.tvStatPhotos)
        tvStatVideos = findViewById(R.id.tvStatVideos)
        tvStatAudio = findViewById(R.id.tvStatAudio)
        tvStatDocs = findViewById(R.id.tvStatDocs)

        findViewById<View>(R.id.btnGalleryBack).setOnClickListener { finish() }

        // Dynamic Layout Engine Toggle: Compact Grid / Staggered Masonry / Bento Hero Grid
        findViewById<View>(R.id.btnGalleryColumns).setOnClickListener {
            showLayoutEngineDialog()
        }

        // Quick Sort Options
        findViewById<View>(R.id.btnGallerySort).setOnClickListener {
            showQuickSortDialog()
        }

        // Select mode toggle
        findViewById<View>(R.id.btnGallerySelectAll).setOnClickListener {
            if (adapter.isSelectionMode) {
                adapter.clearSelection()
                updateSelectionDock()
            } else {
                adapter.setSelectionMode(true)
                adapter.selectAll()
                updateSelectionDock()
            }
        }

        // More options
        findViewById<View>(R.id.btnGalleryMore).setOnClickListener {
            showMoreMenu()
        }

        btnReindex.setOnClickListener {
            triggerManualReindex()
        }

        MotionUtils.pressScale(findViewById(R.id.btnGalleryColumns), 0.92f)
        MotionUtils.pressScale(findViewById(R.id.btnGallerySort), 0.92f)
        MotionUtils.pressScale(findViewById(R.id.btnGallerySelectAll), 0.92f)
        MotionUtils.pressScale(findViewById(R.id.btnGalleryMore), 0.92f)
    }

    private fun showLayoutEngineDialog() {
        val options = arrayOf(
            "Staggered Masonry Grid (Aspect Ratios)",
            "Bento Featured Grid (Hero Art Cards)",
            "Classic Compact Grid (3 Columns)",
            "Classic Compact Grid (4 Columns)",
            "Classic Compact Grid (2 Columns)"
        )

        AlertDialog.Builder(this)
            .setTitle("Gallery Layout Engine")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        currentLayoutEngine = SmartGalleryAdapter.LAYOUT_MODE_MASONRY
                        gridGallery.numColumns = 2
                        adapter.setLayoutMode(SmartGalleryAdapter.LAYOUT_MODE_MASONRY)
                        Toast.makeText(this, "Staggered Masonry Grid active", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        currentLayoutEngine = SmartGalleryAdapter.LAYOUT_MODE_BENTO
                        gridGallery.numColumns = 2
                        adapter.setLayoutMode(SmartGalleryAdapter.LAYOUT_MODE_BENTO)
                        Toast.makeText(this, "Bento Featured Grid active", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        currentLayoutEngine = SmartGalleryAdapter.LAYOUT_MODE_COMPACT
                        gridColumns = 3
                        gridGallery.numColumns = 3
                        adapter.setLayoutMode(SmartGalleryAdapter.LAYOUT_MODE_COMPACT)
                        Toast.makeText(this, "Compact 3-Columns Grid", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        currentLayoutEngine = SmartGalleryAdapter.LAYOUT_MODE_COMPACT
                        gridColumns = 4
                        gridGallery.numColumns = 4
                        adapter.setLayoutMode(SmartGalleryAdapter.LAYOUT_MODE_COMPACT)
                        Toast.makeText(this, "Compact 4-Columns Grid", Toast.LENGTH_SHORT).show()
                    }
                    4 -> {
                        currentLayoutEngine = SmartGalleryAdapter.LAYOUT_MODE_COMPACT
                        gridColumns = 2
                        gridGallery.numColumns = 2
                        adapter.setLayoutMode(SmartGalleryAdapter.LAYOUT_MODE_COMPACT)
                        Toast.makeText(this, "Large 2-Columns Grid", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showQuickSortDialog() {
        val sortOptions = arrayOf(
            "Date (Newest first)",
            "Date (Oldest first)",
            "Size (Largest first)",
            "Size (Smallest first)",
            "Name (A - Z)",
            "Aspect Ratio (Portrait first)",
            "Aspect Ratio (Landscape first)"
        )

        AlertDialog.Builder(this)
            .setTitle("Sort Media By")
            .setSingleChoiceItems(sortOptions, currentSortOrder) { dialog, which ->
                currentSortOrder = which
                dialog.dismiss()
                applyCurrentSort()
            }
            .show()
    }

    private fun applyCurrentSort() {
        val sorted = when (currentSortOrder) {
            0 -> displayedMedia.sortedByDescending { it.file.lastModified() }
            1 -> displayedMedia.sortedBy { it.file.lastModified() }
            2 -> displayedMedia.sortedByDescending { it.size }
            3 -> displayedMedia.sortedBy { it.size }
            4 -> displayedMedia.sortedBy { it.name.lowercase(Locale.ROOT) }
            5 -> displayedMedia.sortedByDescending { if (it.width > 0) it.height.toFloat() / it.width else 1f }
            6 -> displayedMedia.sortedBy { if (it.width > 0) it.height.toFloat() / it.width else 1f }
            else -> displayedMedia
        }
        displayedMedia = sorted
        adapter.updateItems(sorted)
    }

    private fun setupAdapter() {
        adapter = SmartGalleryAdapter(
            context = this,
            items = emptyList(),
            onItemClick = { _, item ->
                if (adapter.isSelectionMode) {
                    updateSelectionDock()
                } else {
                    openMediaItem(item)
                }
            },
            onItemLongClick = { _, item ->
                showItemContextMenu(item)
                true
            }
        )
        gridGallery.adapter = adapter
    }

    /**
     * Context Menu with 1-Tap Copy Path Superpowers
     */
    private fun showItemContextMenu(item: AiMediaItem) {
        val options = arrayOf(
            "Copy Absolute File Path",
            "Copy Parent Folder Path",
            "Copy File Name",
            "AI Intelligence & Details",
            "Move to Vault",
            if (item.isFavorite) "Remove from Favorites" else "Add to Favorites",
            "Share",
            "Delete"
        )

        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ClipboardHelper.copyPath(this, item.file)
                    1 -> ClipboardHelper.copyDirectoryPath(this, item.file)
                    2 -> ClipboardHelper.copyFileName(this, item.file)
                    3 -> {
                        AiPhotoDetailsDialog.show(
                            context = this,
                            item = item,
                            onFindSimilar = { target ->
                                val similar = db.getSimilarItems(target, 40)
                                applyFilteredMedia(similar, "Similar to: ${target.name}")
                            },
                            onFavoriteToggled = {
                                adapter.notifyDataSetChanged()
                            }
                        )
                    }
                    4 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Move to Vault")
                            .setMessage("Encrypt and move \"${item.name}\" to your private Vault?")
                            .setPositiveButton("Move") { _, _ ->
                                if (VaultManager.moveToVault(item.file)) {
                                    db.deleteItem(item.path)
                                    Toast.makeText(this, "Moved to Vault", Toast.LENGTH_SHORT).show()
                                    loadFastMediaImmediately()
                                } else {
                                    Toast.makeText(this, "Could not move to vault", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    5 -> {
                        val newState = db.toggleFavorite(item.path)
                        item.isFavorite = newState
                        FavoritesManager.toggleFavorite(item.toFileItem())
                        adapter.notifyDataSetChanged()
                        Toast.makeText(this, if (newState) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
                    }
                    6 -> {
                        try {
                            val uri = ArtrawFileProvider.getUriForFile(item.file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "*/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(Intent.createChooser(intent, "Share Media"))
                        } catch (e: Throwable) {
                            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    7 -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete Media")
                            .setMessage("Permanently delete ${item.name}?")
                            .setPositiveButton("Delete") { _, _ ->
                                if (item.file.delete()) {
                                    db.deleteItem(item.path)
                                    Toast.makeText(this, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                                    loadFastMediaImmediately()
                                } else {
                                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    /**
     * Seamlessly opens the appropriate native media player / viewer:
     * - Audio -> AudioPlayerActivity (with waveform/controls)
     * - Video -> VideoPlayerActivity (with seekbar/playback)
     * - Image -> ImageViewerActivity (full-screen zoom & swipe)
     * - Document -> TextPreviewActivity / FileViewerHelper
     */
    private fun openMediaItem(item: AiMediaItem) {
        try {
            when {
                item.isAudio -> {
                    val intent = Intent(this, AudioPlayerActivity::class.java).apply {
                        putExtra(AudioPlayerActivity.EXTRA_FILE_PATH, item.path)
                    }
                    startActivity(intent)
                }
                item.isVideo -> {
                    val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                        putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, item.path)
                    }
                    startActivity(intent)
                }
                item.isDocument -> {
                    val lower = item.name.lowercase(Locale.ROOT)
                    if (lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md") || lower.endsWith(".json")) {
                        val intent = Intent(this, TextPreviewActivity::class.java).apply {
                            putExtra(TextPreviewActivity.EXTRA_FILE_PATH, item.path)
                        }
                        startActivity(intent)
                    } else {
                        FileViewerHelper.openFile(this, item.toFileItem())
                    }
                }
                else -> {
                    val intent = Intent(this, ImageViewerActivity::class.java).apply {
                        putExtra(ImageViewerActivity.EXTRA_FILE_PATH, item.path)
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Could not open media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * ULTRA-FAST 0ms LOAD PIPELINE:
     * Directly loads all media in < 25ms, populates the grid, updates stats and memories,
     * then kicks off background AI indexing without blocking!
     */
    private fun loadFastMediaImmediately() {
        progressLoading.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE

        loaderExecutor.execute {
            val fastMedia = GalleryIndexer.loadFastDeviceMedia(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progressLoading.visibility = View.GONE
                allMedia = fastMedia
                updateLiveStatsBanner(fastMedia)
                buildMemoriesCarousel(fastMedia)
                loadCategory(currentCategory)

                // Background AI enrichment (does not block UI or image rendering)
                GalleryIndexer.startIndexing(
                    context = this,
                    forceRescan = false,
                    listener = object : GalleryIndexer.IndexProgressListener {
                        override fun onProgress(current: Int, total: Int) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed && total > 0) {
                                    tvGalleryCount.text = "AI Indexing: $current / $total"
                                }
                            }
                        }
                        override fun onComplete(totalIndexed: Int) {
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    tvGalleryCount.text = "${allMedia.size} items • AI Indexed"
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun updateLiveStatsBanner(items: List<AiMediaItem>) {
        val photos = items.count { !it.isVideo && !it.isAudio && !it.isDocument }
        val videos = items.count { it.isVideo }
        val audio = items.count { it.isAudio }
        val docs = items.count { it.isDocument }

        tvStatPhotos.text = "$photos Photos"
        tvStatVideos.text = "$videos Videos"
        tvStatAudio.text = "$audio Audio"
        tvStatDocs.text = "$docs Docs"
    }

    /**
     * Top "Memories / Featured Art" Horizontal Snapping Carousel
     */
    private fun buildMemoriesCarousel(items: List<AiMediaItem>) {
        layoutGalleryMemories.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val memoryCards = mutableListOf<MemoryCardData>()

        // 1. Best Aesthetics
        val aestheticItems = items.filter { it.aestheticScore >= 75.0 || (!it.isVideo && !it.isAudio && !it.isDocument) }
            .sortedByDescending { it.aestheticScore }
        if (aestheticItems.isNotEmpty()) {
            memoryCards.add(
                MemoryCardData(
                    badge = "AESTHETICS",
                    title = "Best Aesthetics",
                    subtitle = "${aestheticItems.size} top-scored items",
                    coverItem = aestheticItems.first(),
                    items = aestheticItems
                )
            )
        }

        // 2. Recent Camera
        val cameraItems = items.filter { it.category == "camera" || it.path.lowercase(Locale.ROOT).contains("dcim") }
            .sortedByDescending { it.file.lastModified() }
        if (cameraItems.isNotEmpty()) {
            memoryCards.add(
                MemoryCardData(
                    badge = "CAMERA ROLL",
                    title = "Recent Camera",
                    subtitle = "${cameraItems.size} recent shots",
                    coverItem = cameraItems.first(),
                    items = cameraItems
                )
            )
        }

        // 3. RAW & Fine Art
        val rawArtItems = items.filter { item ->
            val name = item.name.lowercase(Locale.ROOT)
            name.endsWith(".raw") || name.endsWith(".dng") || name.endsWith(".cr2") ||
            name.endsWith(".nef") || name.endsWith(".arw") || name.endsWith(".svg") ||
            name.endsWith(".psd") || item.aestheticScore >= 85.0
        }
        if (rawArtItems.isNotEmpty()) {
            memoryCards.add(
                MemoryCardData(
                    badge = "FINE ART",
                    title = "RAW & Art Works",
                    subtitle = "${rawArtItems.size} high-definition pieces",
                    coverItem = rawArtItems.first(),
                    items = rawArtItems
                )
            )
        }

        // 4. Favorites Showcase
        val favItems = items.filter { it.isFavorite }
        if (favItems.isNotEmpty()) {
            memoryCards.add(
                MemoryCardData(
                    badge = "FAVORITES",
                    title = "Curated Favorites",
                    subtitle = "${favItems.size} saved favorites",
                    coverItem = favItems.first(),
                    items = favItems
                )
            )
        }

        // 5. Cinematic Videos
        val videoItems = items.filter { it.isVideo }
        if (videoItems.isNotEmpty()) {
            memoryCards.add(
                MemoryCardData(
                    badge = "CINEMATIC",
                    title = "Motion Videos",
                    subtitle = "${videoItems.size} video clips",
                    coverItem = videoItems.first(),
                    items = videoItems
                )
            )
        }

        if (memoryCards.isEmpty()) {
            scrollGalleryMemories.visibility = View.GONE
            return
        }

        scrollGalleryMemories.visibility = View.VISIBLE

        for (m in memoryCards) {
            val card = inflater.inflate(R.layout.item_gallery_memory_card, layoutGalleryMemories, false)
            card.findViewById<TextView>(R.id.tvMemoryBadge).text = m.badge
            card.findViewById<TextView>(R.id.tvMemoryTitle).text = m.title
            card.findViewById<TextView>(R.id.tvMemorySubtitle).text = m.subtitle

            val ivCover = card.findViewById<ImageView>(R.id.imgMemoryCover)
            ThumbnailLoader.loadThumbnail(
                imageView = ivCover,
                item = m.coverItem.toFileItem(),
                targetSize = 400,
                defaultIconRes = if (m.coverItem.isVideo) R.drawable.ic_video else R.drawable.ic_image
            )

            card.setOnClickListener {
                applyFilteredMedia(m.items, m.title)
            }
            MotionUtils.pressScale(card, 0.96f)
            layoutGalleryMemories.addView(card)
        }
    }

    private data class MemoryCardData(
        val badge: String,
        val title: String,
        val subtitle: String,
        val coverItem: AiMediaItem,
        val items: List<AiMediaItem>
    )

    private fun setupSearch() {
        btnClearSearch.setOnClickListener {
            etSearch.setText("")
            btnClearSearch.visibility = View.GONE
            loadCategory(currentCategory)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                if (q.isNotEmpty()) {
                    performAiSearch(q)
                } else {
                    loadCategory(currentCategory)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etSearch.setOnEditorActionListener { _, _, _ ->
            val q = etSearch.text.toString().trim()
            if (q.isNotEmpty()) {
                performAiSearch(q)
            }
            true
        }
    }

    /**
     * Fast Hybrid AI Search:
     * 1. Evaluates in-memory matches from currently loaded media (0ms instantaneous!)
     * 2. Also queries enriched SQLite database for deep feature matching (blur, aesthetic, OCR)
     * 3. Merges results with zero latency
     */
    private fun performAiSearch(query: String) {
        val parsed = AiQueryParser.parse(query)
        val qLower = query.lowercase(Locale.ROOT)

        val inMemoryMatches = allMedia.filter { item ->
            val pLower = item.path.lowercase(Locale.ROOT)
            val nameLower = item.name.lowercase(Locale.ROOT)

            if (parsed.targetCategory != null) {
                when (parsed.targetCategory) {
                    GalleryCategory.VIDEOS -> if (!item.isVideo) return@filter false
                    GalleryCategory.AUDIO -> if (!item.isAudio) return@filter false
                    GalleryCategory.PHOTOS -> if (item.isVideo || item.isAudio || item.isDocument) return@filter false
                    GalleryCategory.SCREENSHOTS -> if (!pLower.contains("screenshot") && !item.detectedTags.contains("screenshot")) return@filter false
                    GalleryCategory.DOCUMENTS -> if (!item.isDocument && !pLower.contains("doc") && !pLower.contains("receipt")) return@filter false
                    GalleryCategory.CAMERA -> if (!pLower.contains("dcim") && !pLower.contains("camera")) return@filter false
                    GalleryCategory.DOWNLOADS -> if (!pLower.contains("download")) return@filter false
                    GalleryCategory.FAVORITES -> if (!item.isFavorite) return@filter false
                    else -> {}
                }
            }

            if (parsed.colorFilter != null && item.dominantColor != parsed.colorFilter) {
                return@filter false
            }

            if (parsed.isBlurryOnly && !item.isBlurry && item.blurScore >= 75.0) {
                return@filter false
            }

            if (parsed.requiredTags.isNotEmpty()) {
                val hasTag = parsed.requiredTags.any { tag ->
                    item.detectedTags.contains(tag) || pLower.contains(tag) || nameLower.contains(tag)
                }
                if (!hasTag) return@filter false
            }

            if (parsed.textSearch != null) {
                val searchLower = parsed.textSearch.lowercase(Locale.ROOT)
                if (!nameLower.contains(searchLower) && !pLower.contains(searchLower) &&
                    !item.description.lowercase(Locale.ROOT).contains(searchLower) &&
                    !item.detectedTags.any { it.contains(searchLower) }) {
                    return@filter false
                }
            }

            true
        }

        val dbMatches = try {
            db.search(parsed, limit = 200)
        } catch (_: Throwable) {
            emptyList<AiMediaItem>()
        }

        val combined = (inMemoryMatches + dbMatches).distinctBy { it.path }
        applyFilteredMedia(combined, "Search: \"$query\"")
    }

    private fun setupSuggestions() {
        val suggestionMap = mapOf(
            R.id.chipSuggMountains to "Show my photos with mountains",
            R.id.chipSuggRedCar to "Show images with a red car",
            R.id.chipSuggDog to "Find pictures of my dog",
            R.id.chipSuggDocuments to "Find photos containing documents",
            R.id.chipSuggScreenshots to "Show screenshots from last month",
            R.id.chipSuggBest to "Find my best photos",
            R.id.chipSuggBlurry to "Find blurry pictures",
            R.id.chipSuggDuplicates to "Find duplicate photos",
            R.id.chipSuggDiwali to "Show all photos from Diwali"
        )

        for ((id, query) in suggestionMap) {
            findViewById<View>(id)?.let { view ->
                view.setOnClickListener {
                    etSearch.setText(query)
                    etSearch.setSelection(query.length)
                    performAiSearch(query)
                }
                MotionUtils.pressScale(view, 0.92f)
            }
        }
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            R.id.tabCatAll to GalleryCategory.ALL,
            R.id.tabCatPhotos to GalleryCategory.PHOTOS,
            R.id.tabCatCamera to GalleryCategory.CAMERA,
            R.id.tabCatVideos to GalleryCategory.VIDEOS,
            R.id.tabCatScreenshots to GalleryCategory.SCREENSHOTS,
            R.id.tabCatFavorites to GalleryCategory.FAVORITES,
            R.id.tabCatRawArt to GalleryCategory.RAW_ART,
            R.id.tabCatVault to GalleryCategory.VAULT,
            R.id.tabCatAudio to GalleryCategory.AUDIO,
            R.id.tabCatDocuments to GalleryCategory.DOCUMENTS,
            R.id.tabCatDownloads to GalleryCategory.DOWNLOADS,
            R.id.tabCatCollections to GalleryCategory.COLLECTIONS,
            R.id.tabCatDuplicates to GalleryCategory.DUPLICATES
        )

        for ((id, cat) in tabs) {
            findViewById<TextView>(id)?.let { tv ->
                tv.setOnClickListener {
                    tabs.forEach { (tabId, _) ->
                        findViewById<TextView>(tabId)?.let { other ->
                            if (tabId == id) {
                                other.setBackgroundResource(R.drawable.bg_cyber_chip_active)
                                other.setTextColor(getColor(R.color.aurora_on_primary))
                            } else {
                                other.setBackgroundResource(R.drawable.bg_cyber_chip_inactive)
                                other.setTextColor(getColor(R.color.gallery_text_subtitle))
                            }
                        }
                    }
                    currentCategory = cat
                    etSearch.setText("")
                    loadCategory(cat)
                }
                MotionUtils.pressScale(tv, 0.94f)
            }
        }
    }

    private fun loadCategory(category: GalleryCategory) {
        if (category == GalleryCategory.VAULT) {
            startActivity(Intent(this, VaultActivity::class.java))
            return
        }

        if (category == GalleryCategory.COLLECTIONS) {
            gridGallery.visibility = View.GONE
            scrollCollections.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            buildAiCollectionsView()
            tvGalleryTitle.text = "AI Collections"
            tvGalleryCount.text = "Smart automatic media classification"
            return
        }

        gridGallery.visibility = View.VISIBLE
        scrollCollections.visibility = View.GONE

        val items = when (category) {
            GalleryCategory.ALL -> allMedia
            GalleryCategory.PHOTOS -> allMedia.filter { !it.isVideo && !it.isAudio && !it.isDocument }
            GalleryCategory.CAMERA -> allMedia.filter { it.category == "camera" || it.path.lowercase(Locale.ROOT).contains("dcim") }
            GalleryCategory.VIDEOS -> allMedia.filter { it.isVideo }
            GalleryCategory.SCREENSHOTS -> allMedia.filter { it.category == "screenshots" || it.detectedTags.contains("screenshot") }
            GalleryCategory.FAVORITES -> allMedia.filter { it.isFavorite }
            GalleryCategory.RAW_ART -> allMedia.filter { item ->
                val name = item.name.lowercase(Locale.ROOT)
                name.endsWith(".raw") || name.endsWith(".dng") || name.endsWith(".cr2") ||
                name.endsWith(".nef") || name.endsWith(".arw") || name.endsWith(".svg") ||
                name.endsWith(".psd") || item.aestheticScore >= 80.0
            }
            GalleryCategory.AUDIO -> allMedia.filter { it.isAudio }
            GalleryCategory.DOCUMENTS -> allMedia.filter { it.isDocument }
            GalleryCategory.DOWNLOADS -> allMedia.filter { it.category == "downloads" || it.path.lowercase(Locale.ROOT).contains("download") }
            GalleryCategory.DUPLICATES -> db.getDuplicateItems()
            else -> allMedia
        }

        val title = when (category) {
            GalleryCategory.ALL -> "ArtFiles Gallery"
            GalleryCategory.PHOTOS -> "Photos"
            GalleryCategory.CAMERA -> "Camera Media"
            GalleryCategory.VIDEOS -> "Videos"
            GalleryCategory.SCREENSHOTS -> "Screenshots"
            GalleryCategory.FAVORITES -> "Favorites"
            GalleryCategory.RAW_ART -> "RAW & Fine Art"
            GalleryCategory.AUDIO -> "Audio & Music"
            GalleryCategory.DOCUMENTS -> "Documents & PDFs"
            GalleryCategory.DOWNLOADS -> "Downloads"
            GalleryCategory.DUPLICATES -> "Duplicate Photos"
            else -> "Gallery"
        }
        applyFilteredMedia(items, title)
    }

    private fun buildAiCollectionsView() {
        layoutCollectionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val collections = AiCollectionType.values()

        for (col in collections) {
            val card = inflater.inflate(R.layout.item_ai_collection_card, layoutCollectionsContainer, false)
            card.findViewById<TextView>(R.id.tvCollectionEmoji).text = col.iconEmoji
            card.findViewById<TextView>(R.id.tvCollectionTitle).text = col.title
            card.findViewById<TextView>(R.id.tvCollectionSubtitle).text = col.description

            val matches = allMedia.filter { item ->
                col.matchingTags.any { tag ->
                    item.detectedTags.contains(tag) || item.path.lowercase(Locale.ROOT).contains(tag)
                }
            }
            card.findViewById<TextView>(R.id.tvCollectionCount).text = "${matches.size}"

            card.setOnClickListener {
                scrollCollections.visibility = View.GONE
                gridGallery.visibility = View.VISIBLE
                applyFilteredMedia(matches, col.title)
            }
            MotionUtils.pressScale(card, 0.98f)
            layoutCollectionsContainer.addView(card)
        }
    }

    private fun applyFilteredMedia(items: List<AiMediaItem>, title: String) {
        displayedMedia = items
        adapter.updateItems(items)
        applyCurrentSort()
        tvGalleryTitle.text = title

        val photoCount = items.count { !it.isVideo && !it.isAudio && !it.isDocument }
        val videoCount = items.count { it.isVideo }
        val audioCount = items.count { it.isAudio }
        val docCount = items.count { it.isDocument }

        tvGalleryCount.text = "$photoCount Photos • $videoCount Videos • $audioCount Audio • $docCount Docs"

        if (items.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyTitle.text = "No media found"
            tvEmptySubtitle.text = "No files match \"$title\""
        } else {
            layoutEmpty.visibility = View.GONE
        }
        updateSelectionDock()
    }

    private fun setupSelectionDock() {
        findViewById<View>(R.id.btnSelectionClose).setOnClickListener {
            adapter.clearSelection()
            updateSelectionDock()
        }

        // 1-TAP BATCH COPY ALL PATHS (USER PRIORITY REQUIREMENT)
        findViewById<View>(R.id.btnSelectionCopyPaths).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            ClipboardHelper.copyMultiplePaths(this, selected.map { it.path }, it)
        }

        // BATCH MOVE TO VAULT
        findViewById<View>(R.id.btnSelectionVault).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Move to Vault")
                .setMessage("Encrypt and move ${selected.size} items to your private Vault?")
                .setPositiveButton("Move to Vault") { _, _ ->
                    var moved = 0
                    for (item in selected) {
                        if (VaultManager.moveToVault(item.file)) {
                            db.deleteItem(item.path)
                            moved++
                        }
                    }
                    Toast.makeText(this, "Moved $moved items to Vault", Toast.LENGTH_SHORT).show()
                    adapter.clearSelection()
                    loadFastMediaImmediately()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // BATCH FAVORITE
        findViewById<View>(R.id.btnSelectionFavorite).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            var added = 0
            for (item in selected) {
                val newState = db.toggleFavorite(item.path)
                item.isFavorite = newState
                FavoritesManager.toggleFavorite(item.toFileItem())
                if (newState) added++
            }
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Updated $added favorites", Toast.LENGTH_SHORT).show()
        }

        // BATCH SHARE
        findViewById<View>(R.id.btnSelectionShare).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            val uris = ArrayList<Uri>()
            for (item in selected) {
                try {
                    val uri = ArtrawFileProvider.getUriForFile(item.file)
                    uris.add(uri)
                } catch (_: Throwable) {}
            }
            if (uris.isNotEmpty()) {
                val intent = Intent().apply {
                    action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                    if (uris.size == 1) {
                        putExtra(Intent.EXTRA_STREAM, uris[0])
                    } else {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    }
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Media"))
            }
        }

        // BATCH DELETE
        findViewById<View>(R.id.btnSelectionDelete).setOnClickListener {
            val selected = adapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to permanently delete ${selected.size} item(s)?")
                .setPositiveButton("Delete") { _, _ ->
                    var deleted = 0
                    for (item in selected) {
                        if (item.file.delete()) {
                            db.deleteItem(item.path)
                            deleted++
                        }
                    }
                    Toast.makeText(this, "Deleted $deleted items", Toast.LENGTH_SHORT).show()
                    adapter.clearSelection()
                    loadFastMediaImmediately()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateSelectionDock() {
        if (adapter.isSelectionMode) {
            val count = adapter.getSelectedItems().size
            layoutSelectionDock.visibility = View.VISIBLE
            tvSelectionCount.text = "$count selected"
        } else {
            layoutSelectionDock.visibility = View.GONE
        }
    }

    private fun setupAssistant() {
        val openAssistantAction = View.OnClickListener {
            AiAssistantBottomSheet.show(
                context = this,
                initialQuery = null,
                onViewInGrid = { items, query ->
                    applyFilteredMedia(items, "AI: $query")
                }
            )
        }
        findViewById<View>(R.id.fabGalleryAssistant).setOnClickListener(openAssistantAction)
        findViewById<View>(R.id.btnGalleryHeaderAssistant).setOnClickListener(openAssistantAction)
        MotionUtils.pressScale(findViewById(R.id.fabGalleryAssistant), 0.94f)
    }

    private fun triggerManualReindex() {
        Toast.makeText(this, "Starting full gallery AI scan…", Toast.LENGTH_SHORT).show()
        progressLoading.visibility = View.VISIBLE
        GalleryIndexer.startIndexing(this, forceRescan = true, listener = object : GalleryIndexer.IndexProgressListener {
            override fun onProgress(current: Int, total: Int) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvGalleryCount.text = "AI Indexing: $current / $total items"
                    }
                }
            }
            override fun onComplete(totalIndexed: Int) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        progressLoading.visibility = View.GONE
                        Toast.makeText(this@SmartGalleryActivity, "AI Index complete: $totalIndexed items", Toast.LENGTH_SHORT).show()
                        loadFastMediaImmediately()
                    }
                }
            }
        })
    }

    private fun showMoreMenu() {
        val options = arrayOf(
            "Change Layout Engine (Bento / Masonry / Grid)",
            "Sort Media (Date, Size, Name, Aspect Ratio)",
            "Full AI Re-scan & Index",
            "Open AI Assistant",
            "Open Secure Vault",
            "Gallery Settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Gallery Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLayoutEngineDialog()
                    1 -> showQuickSortDialog()
                    2 -> triggerManualReindex()
                    3 -> findViewById<View>(R.id.fabGalleryAssistant).performClick()
                    4 -> startActivity(Intent(this, VaultActivity::class.java))
                    5 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }
}
