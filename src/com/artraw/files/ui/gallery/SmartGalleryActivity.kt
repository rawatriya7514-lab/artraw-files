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
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.gallery.indexer.GalleryIndexer
import com.artraw.files.gallery.model.AiCollectionType
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.gallery.model.GalleryCategory
import com.artraw.files.preview.ArtrawFileProvider
import com.artraw.files.ui.BaseActivity
import com.artraw.files.ui.ImageViewerActivity
import com.artraw.files.ui.SettingsActivity
import com.artraw.files.ui.VideoPlayerActivity
import com.artraw.files.ui.gallery.adapter.SmartGalleryAdapter
import com.artraw.files.ui.gallery.dialog.AiAssistantBottomSheet
import com.artraw.files.ui.gallery.dialog.AiPhotoDetailsDialog
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.FavoritesManager
import com.artraw.files.util.Formatters
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.PermissionsHelper
import java.io.File

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

    private lateinit var scrollCollections: View
    private lateinit var layoutCollectionsContainer: LinearLayout

    private lateinit var layoutSelectionDock: LinearLayout
    private lateinit var tvSelectionCount: TextView

    private var currentCategory: GalleryCategory = GalleryCategory.ALL
    private var allMedia: List<AiMediaItem> = emptyList()
    private var displayedMedia: List<AiMediaItem> = emptyList()

    private var gridColumns: Int = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_gallery)

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

        // Check storage permissions
        if (PermissionsHelper.hasStoragePermission(this)) {
            loadGalleryData()
            // Auto start background indexing if needed
            GalleryIndexer.startIndexing(this, forceRescan = false, listener = object : GalleryIndexer.IndexProgressListener {
                override fun onProgress(current: Int, total: Int) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            tvGalleryCount.text = "Indexing $current / $total items…"
                        }
                    }
                }
                override fun onComplete(totalIndexed: Int) {
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            loadGalleryData()
                        }
                    }
                }
            })
        } else {
            PermissionsHelper.requestStoragePermission(this)
        }
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
        scrollCollections = findViewById(R.id.scrollGalleryCollections)
        layoutCollectionsContainer = findViewById(R.id.layoutCollectionsContainer)
        layoutSelectionDock = findViewById(R.id.layoutGallerySelectionDock)
        tvSelectionCount = findViewById(R.id.tvGallerySelectionCount)

        findViewById<View>(R.id.btnGalleryBack).setOnClickListener { finish() }

        // Grid column density toggle
        findViewById<View>(R.id.btnGalleryColumns).setOnClickListener {
            gridColumns = when (gridColumns) {
                2 -> 3
                3 -> 4
                else -> 2
            }
            gridGallery.numColumns = gridColumns
            Toast.makeText(this, "$gridColumns columns grid", Toast.LENGTH_SHORT).show()
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
            onItemLongClick = { _, _ ->
                updateSelectionDock()
                true
            }
        )
        gridGallery.adapter = adapter
    }

    private fun openMediaItem(item: AiMediaItem) {
        if (item.isVideo) {
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, item.path)
            }
            startActivity(intent)
        } else {
            // For images: show detail dialog with option to view full screen
            AiPhotoDetailsDialog.show(
                context = this,
                item = item,
                onFindSimilar = { target ->
                    val similar = db.getSimilarItems(target, 40)
                    applyFilteredMedia(similar, "Similar to: ${target.name}")
                },
                onFavoriteToggled = { _ ->
                    adapter.notifyDataSetChanged()
                }
            )
        }
    }

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

    private fun performAiSearch(query: String) {
        val parsed = AiQueryParser.parse(query)
        val results = db.search(parsed, limit = 500)
        applyFilteredMedia(results, "Search: \"$query\"")
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
                MotionUtils.pressScale(view, 0.94f)
            }
        }
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            R.id.tabCatAll to GalleryCategory.ALL,
            R.id.tabCatPhotos to GalleryCategory.PHOTOS,
            R.id.tabCatVideos to GalleryCategory.VIDEOS,
            R.id.tabCatScreenshots to GalleryCategory.SCREENSHOTS,
            R.id.tabCatCamera to GalleryCategory.CAMERA,
            R.id.tabCatDownloads to GalleryCategory.DOWNLOADS,
            R.id.tabCatDocuments to GalleryCategory.DOCUMENTS,
            R.id.tabCatFavorites to GalleryCategory.FAVORITES,
            R.id.tabCatCollections to GalleryCategory.COLLECTIONS,
            R.id.tabCatDuplicates to GalleryCategory.DUPLICATES
        )

        for ((id, cat) in tabs) {
            findViewById<TextView>(id)?.let { tv ->
                tv.setOnClickListener {
                    tabs.forEach { (tabId, _) ->
                        findViewById<TextView>(tabId)?.let { other ->
                            if (tabId == id) {
                                other.setBackgroundResource(R.drawable.bg_chip_pill_selected)
                                other.setTextColor(getColor(R.color.aurora_on_primary))
                            } else {
                                other.setBackgroundResource(R.drawable.bg_chip_pill)
                                other.setTextColor(getColor(R.color.aurora_text_secondary))
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
        if (category == GalleryCategory.COLLECTIONS) {
            gridGallery.visibility = View.GONE
            scrollCollections.visibility = View.VISIBLE
            layoutEmpty.visibility = View.GONE
            buildAiCollectionsView()
            tvGalleryTitle.text = "AI Collections"
            tvGalleryCount.text = "Smart automatic photo categorization"
            return
        }

        gridGallery.visibility = View.VISIBLE
        scrollCollections.visibility = View.GONE

        val items = db.getByCategory(category)
        val title = when (category) {
            GalleryCategory.ALL -> "AI Smart Gallery"
            GalleryCategory.PHOTOS -> "Photos"
            GalleryCategory.VIDEOS -> "Videos"
            GalleryCategory.SCREENSHOTS -> "Screenshots"
            GalleryCategory.CAMERA -> "Camera"
            GalleryCategory.DOWNLOADS -> "Downloads"
            GalleryCategory.DOCUMENTS -> "Documents & Receipts"
            GalleryCategory.FAVORITES -> "Favorites"
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

            val parsed = AiQueryParser.parse(col.matchingTags.joinToString(" "))
            val items = db.search(parsed, limit = 500)
            card.findViewById<TextView>(R.id.tvCollectionCount).text = "${items.size}"

            card.setOnClickListener {
                scrollCollections.visibility = View.GONE
                gridGallery.visibility = View.VISIBLE
                applyFilteredMedia(items, col.title)
            }
            MotionUtils.pressScale(card, 0.98f)
            layoutCollectionsContainer.addView(card)
        }
    }

    private fun applyFilteredMedia(items: List<AiMediaItem>, title: String) {
        displayedMedia = items
        adapter.updateItems(items)
        tvGalleryTitle.text = title

        val photoCount = items.count { !it.isVideo }
        val videoCount = items.count { it.isVideo }
        tvGalleryCount.text = "$photoCount photos • $videoCount videos"

        if (items.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            tvEmptyTitle.text = "No media found"
            tvEmptySubtitle.text = "No photos match \"$title\""
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
                    type = if (selected.all { it.isVideo }) "video/*" else "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Share Media"))
            }
        }

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
                    loadCategory(currentCategory)
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
    }

    private fun loadGalleryData() {
        allMedia = db.getAllItems(1000)
        loadCategory(currentCategory)
    }

    private fun triggerManualReindex() {
        Toast.makeText(this, "Starting full gallery AI scan…", Toast.LENGTH_SHORT).show()
        progressLoading.visibility = View.VISIBLE
        GalleryIndexer.startIndexing(this, forceRescan = true, listener = object : GalleryIndexer.IndexProgressListener {
            override fun onProgress(current: Int, total: Int) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        tvGalleryCount.text = "Indexing $current / $total items…"
                    }
                }
            }
            override fun onComplete(totalIndexed: Int) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        progressLoading.visibility = View.GONE
                        Toast.makeText(this@SmartGalleryActivity, "AI Index complete: $totalIndexed items", Toast.LENGTH_SHORT).show()
                        loadGalleryData()
                    }
                }
            }
        })
    }

    private fun showMoreMenu() {
        val options = arrayOf(
            "Full AI Re-scan & Index",
            "Open AI Assistant",
            "Gallery Settings"
        )
        AlertDialog.Builder(this)
            .setTitle("Gallery Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> triggerManualReindex()
                    1 -> findViewById<View>(R.id.fabGalleryAssistant).performClick()
                    2 -> startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            .show()
    }
}
