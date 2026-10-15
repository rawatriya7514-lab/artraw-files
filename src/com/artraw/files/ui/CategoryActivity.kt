package com.artraw.files.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.ui.adapter.FileListAdapter
import java.util.concurrent.Executors

class CategoryActivity : Activity() {

    private lateinit var repository: FileRepository
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tvCatTitle: TextView
    private lateinit var tvCatItemCount: TextView
    private lateinit var layoutArchiveBanner: LinearLayout
    private lateinit var listViewCatFiles: ListView
    private lateinit var layoutCatEmpty: LinearLayout
    private lateinit var progressCatLoading: ProgressBar
    private lateinit var adapter: FileListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)
        setContentView(R.layout.activity_category)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topHeaders = listOf(findViewById(R.id.headerCategory)),
            bottomScrollViews = listOf(findViewById(R.id.listViewCatFiles))
        )

        val catId = intent.getStringExtra("category_id") ?: FileCategory.DOCUMENTS.id

        initViews()
        setupCategory(catId)
    }

    private fun initViews() {
        tvCatTitle = findViewById(R.id.tvCatTitle)
        tvCatItemCount = findViewById(R.id.tvCatItemCount)
        layoutArchiveBanner = findViewById(R.id.layoutArchiveBanner)
        listViewCatFiles = findViewById(R.id.listViewCatFiles)
        layoutCatEmpty = findViewById(R.id.layoutCatEmpty)
        progressCatLoading = findViewById(R.id.progressCatLoading)

        findViewById<View>(R.id.btnCatBack).setOnClickListener {
            finish()
        }

        adapter = FileListAdapter(this, emptyList(), isGrid = false) { }
        listViewCatFiles.adapter = adapter

        listViewCatFiles.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position)
            FileViewerHelper.openFile(this, item)
        }
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
            val items: List<FileItem> = if (catId == "recent") {
                repository.getRecentFiles(50)
            } else {
                val cat = FileCategory.fromId(catId)
                repository.getCategoryFiles(cat, maxDepth = 4, maxResults = 300)
            }

            runOnUiThread {
                progressCatLoading.visibility = View.GONE
                adapter.updateData(items)
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
