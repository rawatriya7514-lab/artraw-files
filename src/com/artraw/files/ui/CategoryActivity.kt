package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import com.artraw.files.R
import com.artraw.files.ad.AdMobManager
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.operations.ClipboardMode
import com.artraw.files.operations.FileOperationManager
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.ui.adapter.FileListAdapter
import com.artraw.files.util.Formatters
import com.google.android.gms.ads.AdView
import java.util.concurrent.Executors

class CategoryActivity : Activity() {

    private lateinit var repository: FileRepository
    private val operationManager = FileOperationManager()
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tvCatTitle: TextView
    private lateinit var tvCatItemCount: TextView
    private lateinit var layoutArchiveBanner: LinearLayout
    private lateinit var listViewCatFiles: ListView
    private lateinit var gridViewCatFiles: GridView
    private lateinit var layoutCatEmpty: LinearLayout
    private lateinit var progressCatLoading: ProgressBar
    private lateinit var btnCatViewToggle: ImageView

    private lateinit var toolbarCatNormal: RelativeLayout
    private lateinit var toolbarCatSelection: RelativeLayout
    private lateinit var tvCatSelectionCount: TextView
    private lateinit var adViewContainerCategory: FrameLayout
    private var adViewCategory: AdView? = null

    private lateinit var listAdapter: FileListAdapter
    private lateinit var gridAdapter: FileListAdapter
    private var isGridMode: Boolean = false
    private var allItems: List<FileItem> = emptyList()

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var gridColumns: Int = 3
    private var lastScaleTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)
        setContentView(R.layout.activity_category)

        try {
            com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
                activity = this,
                topSpacers = listOfNotNull(findViewById(R.id.statusBarSpacerCategory)),
                bottomScrollViews = listOfNotNull(findViewById(R.id.listViewCatFiles), findViewById(R.id.gridViewCatFiles)),
                bottomStickyViews = listOfNotNull(findViewById(R.id.adViewContainerCategory))
            )
        } catch (_: Throwable) {}

        adViewContainerCategory = findViewById(R.id.adViewContainerCategory)
        try {
            adViewCategory = AdMobManager.loadBanner(this, adViewContainerCategory)
        } catch (_: Throwable) {}

        val catId = intent.getStringExtra("category_id") ?: FileCategory.DOCUMENTS.id
        // Default to Gallery Grid view for Images and Videos
        isGridMode = (catId == FileCategory.IMAGES.id || catId == FileCategory.VIDEOS.id)

        initViews()
        setupScaleDetector()
        setupListeners()
        setupCategory(catId)
    }

    override fun onResume() {
        super.onResume()
        try {
            adViewCategory?.resume()
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try {
            adViewCategory?.pause()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroy() {
        try {
            adViewCategory?.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun setupScaleDetector() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val now = System.currentTimeMillis()
                if (now - lastScaleTime < 320) return false

                if (factor > 1.15f && gridColumns > 1) {
                    // Pinch OUT (Zoom IN) -> fewer columns, larger frames
                    gridColumns--
                    applyGridColumns(gridColumns)
                    lastScaleTime = now
                    return true
                } else if (factor < 0.85f && gridColumns < 5) {
                    // Pinch IN (Zoom OUT) -> more columns, smaller frames to see more items
                    gridColumns++
                    applyGridColumns(gridColumns)
                    lastScaleTime = now
                    return true
                }
                return false
            }
        })
    }

    private fun applyGridColumns(cols: Int) {
        gridColumns = cols.coerceIn(1, 5)
        gridViewCatFiles.numColumns = gridColumns
        val density = resources.displayMetrics.density
        val spacing = when (gridColumns) {
            1 -> (2 * density).toInt()
            2 -> (4 * density).toInt()
            3 -> (3 * density).toInt()
            4 -> (2 * density).toInt()
            else -> (2 * density).toInt()
        }
        gridViewCatFiles.horizontalSpacing = spacing
        gridViewCatFiles.verticalSpacing = spacing
        gridAdapter.gridColumns = gridColumns
        gridAdapter.notifyDataSetChanged()

        val label = when (gridColumns) {
            1 -> "1 Column (Single Large)"
            2 -> "2 Columns (Large Preview)"
            3 -> "3 Columns (Standard Grid)"
            4 -> "4 Columns (Compact Grid)"
            else -> "5 Columns (Mini Gallery)"
        }
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isGridMode && ::scaleGestureDetector.isInitialized && ev.pointerCount >= 2) {
            try {
                scaleGestureDetector.onTouchEvent(ev)
                if (scaleGestureDetector.isInProgress) {
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    gridViewCatFiles.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                    return true
                }
            } catch (_: Throwable) {}
        }
        return try {
            super.dispatchTouchEvent(ev)
        } catch (_: Throwable) {
            false
        }
    }

    private fun initViews() {
        tvCatTitle = findViewById(R.id.tvCatTitle)
        tvCatItemCount = findViewById(R.id.tvCatItemCount)
        layoutArchiveBanner = findViewById(R.id.layoutArchiveBanner)
        listViewCatFiles = findViewById(R.id.listViewCatFiles)
        gridViewCatFiles = findViewById(R.id.gridViewCatFiles)
        layoutCatEmpty = findViewById(R.id.layoutCatEmpty)
        progressCatLoading = findViewById(R.id.progressCatLoading)
        btnCatViewToggle = findViewById(R.id.btnCatViewToggle)

        toolbarCatNormal = findViewById(R.id.toolbarCatNormal)
        toolbarCatSelection = findViewById(R.id.toolbarCatSelection)
        tvCatSelectionCount = findViewById(R.id.tvCatSelectionCount)

        findViewById<View>(R.id.btnCatBack).setOnClickListener {
            finish()
        }

        listAdapter = FileListAdapter(this, emptyList(), isGrid = false) { count ->
            updateSelectionUi(count)
        }
        gridAdapter = FileListAdapter(this, emptyList(), isGrid = true) { count ->
            updateSelectionUi(count)
        }

        listViewCatFiles.adapter = listAdapter
        gridViewCatFiles.adapter = gridAdapter

        updateViewMode()
    }

    private fun activeAdapter(): FileListAdapter = if (isGridMode) gridAdapter else listAdapter

    private fun updateViewMode() {
        if (isGridMode) {
            listViewCatFiles.visibility = View.GONE
            gridViewCatFiles.visibility = View.VISIBLE
            btnCatViewToggle.setImageResource(R.drawable.ic_list)
        } else {
            listViewCatFiles.visibility = View.VISIBLE
            gridViewCatFiles.visibility = View.GONE
            btnCatViewToggle.setImageResource(R.drawable.ic_grid)
        }
    }

    private fun setupListeners() {
        btnCatViewToggle.setOnClickListener {
            isGridMode = !isGridMode
            updateViewMode()
        }

        findViewById<View>(R.id.btnCatSelectMode).setOnClickListener {
            val adapter = activeAdapter()
            if (adapter.count > 0) {
                adapter.startSelectionMode()
                updateSelectionUi(adapter.getSelectedItems().size)
            } else {
                Toast.makeText(this, "No files to select", Toast.LENGTH_SHORT).show()
            }
        }

        val onItemClick = { position: Int ->
            val adapter = activeAdapter()
            if (position in 0 until adapter.count) {
                val item = adapter.getItem(position)
                if (adapter.isSelectionMode) {
                    adapter.toggleSelection(item)
                } else {
                    FileViewerHelper.openFile(this, item)
                }
            }
        }

        listViewCatFiles.setOnItemClickListener { _, _, position, _ -> onItemClick(position) }
        gridViewCatFiles.setOnItemClickListener { _, _, position, _ -> onItemClick(position) }

        val onItemLongClick = { position: Int ->
            val adapter = activeAdapter()
            if (position in 0 until adapter.count) {
                val item = adapter.getItem(position)
                if (!adapter.isSelectionMode) {
                    adapter.startSelection(item)
                } else {
                    adapter.toggleSelection(item)
                }
            }
            true
        }

        listViewCatFiles.setOnItemLongClickListener { _, _, position, _ -> onItemLongClick(position) }
        gridViewCatFiles.setOnItemLongClickListener { _, _, position, _ -> onItemLongClick(position) }

        findViewById<View>(R.id.btnCatCloseSelection).setOnClickListener {
            activeAdapter().clearSelection()
        }

        findViewById<View>(R.id.btnCatActionSelectAll).setOnClickListener {
            activeAdapter().selectAll()
        }

        findViewById<View>(R.id.btnCatActionCopy).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            if (selected.isNotEmpty()) {
                operationManager.setClipboard(selected, ClipboardMode.COPY)
                activeAdapter().clearSelection()
                Toast.makeText(this, "${selected.size} items copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnCatActionCut).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            if (selected.isNotEmpty()) {
                operationManager.setClipboard(selected, ClipboardMode.CUT)
                activeAdapter().clearSelection()
                Toast.makeText(this, "${selected.size} items cut to clipboard (Ready to Move)", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btnCatActionShare).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            if (selected.isNotEmpty()) {
                FileViewerHelper.shareFiles(this, selected)
                activeAdapter().clearSelection()
            }
        }

        findViewById<View>(R.id.btnCatActionDelete).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            if (selected.isNotEmpty()) {
                confirmDelete(selected)
            }
        }
    }

    private fun updateSelectionUi(selectedCount: Int) {
        val adapter = activeAdapter()
        if (adapter.isSelectionMode) {
            toolbarCatNormal.visibility = View.GONE
            toolbarCatSelection.visibility = View.VISIBLE
            if (selectedCount > 0) {
                val totalBytes = adapter.getSelectedItems().sumOf { it.size }
                tvCatSelectionCount.text = "$selectedCount selected (${Formatters.formatFileSize(totalBytes)})"
            } else {
                tvCatSelectionCount.text = "Select files"
            }
        } else {
            toolbarCatSelection.visibility = View.GONE
            toolbarCatNormal.visibility = View.VISIBLE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeAdapter().isSelectionMode) {
            activeAdapter().clearSelection()
            return
        }
        super.onBackPressed()
    }

    private fun confirmDelete(items: List<FileItem>) {
        val totalSize = items.sumOf { it.size }
        val sizeStr = Formatters.formatFileSize(totalSize)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, items.size, sizeStr))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                executor.execute {
                    operationManager.deleteFiles(items)
                    runOnUiThread {
                        activeAdapter().clearSelection()
                        val catId = intent.getStringExtra("category_id") ?: FileCategory.DOCUMENTS.id
                        loadCategoryData(catId)
                        Toast.makeText(this, "${items.size} items deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun setupCategory(catId: String) {
        val title = when (catId) {
            "recent" -> getString(R.string.action_recent)
            FileCategory.IMAGES.id -> getString(R.string.cat_images)
            FileCategory.VIDEOS.id -> getString(R.string.cat_videos)
            FileCategory.AUDIO.id -> getString(R.string.cat_audio)
            FileCategory.DOCUMENTS.id -> getString(R.string.cat_documents)
            FileCategory.DOWNLOADS.id -> getString(R.string.cat_downloads)
            FileCategory.ARCHIVES.id -> getString(R.string.cat_archives)
            FileCategory.APKS.id -> getString(R.string.cat_apks)
            else -> getString(R.string.cat_other)
        }
        tvCatTitle.text = title

        if (catId == FileCategory.ARCHIVES.id) {
            layoutArchiveBanner.visibility = View.VISIBLE
        } else {
            layoutArchiveBanner.visibility = View.GONE
        }

        loadCategoryData(catId)
    }

    private fun loadCategoryData(catId: String) {
        progressCatLoading.visibility = View.VISIBLE
        layoutCatEmpty.visibility = View.GONE

        executor.execute {
            val items: List<FileItem> = try {
                if (catId == "recent") {
                    repository.getRecentFiles(50)
                } else {
                    val cat = FileCategory.fromId(catId)
                    repository.getCategoryFiles(cat, maxDepth = 6, maxResults = 1000)
                }
            } catch (t: Throwable) {
                emptyList()
            }

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progressCatLoading.visibility = View.GONE
                allItems = items
                listAdapter.updateData(items)
                gridAdapter.updateData(items)
                tvCatItemCount.text = "${items.size} files"
                if (items.isEmpty()) {
                    layoutCatEmpty.visibility = View.VISIBLE
                } else {
                    layoutCatEmpty.visibility = View.GONE
                }
            }
        }
    }
}
