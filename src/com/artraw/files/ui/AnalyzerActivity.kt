package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.analysis.DuplicateDetector
import com.artraw.files.analysis.DuplicateGroup
import com.artraw.files.model.FileItem
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.ui.adapter.FileListAdapter
import com.artraw.files.util.Formatters
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class AnalyzerActivity : Activity() {

    private lateinit var repository: FileRepository
    private val duplicateDetector = DuplicateDetector()
    private val executor = Executors.newSingleThreadExecutor()
    private val cancelFlag = AtomicBoolean(false)

    private lateinit var tabLargeFiles: TextView
    private lateinit var tabDuplicates: TextView
    private lateinit var layoutLargeFilesView: LinearLayout
    private lateinit var layoutDuplicatesView: LinearLayout
    private lateinit var listViewLargeFiles: ListView
    private lateinit var listViewDuplicates: ListView
    private lateinit var btnStartDuplicateScan: Button
    private lateinit var tvDuplicateProgress: TextView
    private lateinit var progressAnalyzer: ProgressBar

    private lateinit var largeFilesAdapter: FileListAdapter
    private var duplicateGroups: List<DuplicateGroup> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)
        setContentView(R.layout.activity_analyzer)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topHeaders = listOf(findViewById(R.id.headerAnalyzer)),
            bottomScrollViews = listOf(findViewById(R.id.listViewLargeFiles), findViewById(R.id.listViewDuplicates))
        )

        initViews()
        setupListeners()

        val initialTab = intent.getStringExtra("tab") ?: "large"
        if (initialTab == "duplicates") {
            selectTab(isDuplicates = true)
        } else {
            selectTab(isDuplicates = false)
            loadLargeFiles()
        }
    }

    private fun initViews() {
        tabLargeFiles = findViewById(R.id.tabLargeFiles)
        tabDuplicates = findViewById(R.id.tabDuplicates)
        layoutLargeFilesView = findViewById(R.id.layoutLargeFilesView)
        layoutDuplicatesView = findViewById(R.id.layoutDuplicatesView)
        listViewLargeFiles = findViewById(R.id.listViewLargeFiles)
        listViewDuplicates = findViewById(R.id.listViewDuplicates)
        btnStartDuplicateScan = findViewById(R.id.btnStartDuplicateScan)
        tvDuplicateProgress = findViewById(R.id.tvDuplicateProgress)
        progressAnalyzer = findViewById(R.id.progressAnalyzer)

        findViewById<View>(R.id.btnAnalyzerBack).setOnClickListener {
            finish()
        }

        largeFilesAdapter = FileListAdapter(this, emptyList(), isGrid = false) { }
        listViewLargeFiles.adapter = largeFilesAdapter

        listViewLargeFiles.setOnItemClickListener { _, _, position, _ ->
            val item = largeFilesAdapter.getItem(position)
            FileViewerHelper.openFile(this, item)
        }
    }

    private fun setupListeners() {
        tabLargeFiles.setOnClickListener {
            selectTab(isDuplicates = false)
            loadLargeFiles()
        }

        tabDuplicates.setOnClickListener {
            selectTab(isDuplicates = true)
        }

        btnStartDuplicateScan.setOnClickListener {
            startCryptographicDuplicateScan()
        }
    }

    private fun selectTab(isDuplicates: Boolean) {
        if (isDuplicates) {
            tabDuplicates.setTextColor(Color.parseColor("#FF5252"))
            tabLargeFiles.setTextColor(Color.parseColor("#9AA0A6"))
            layoutDuplicatesView.visibility = View.VISIBLE
            layoutLargeFilesView.visibility = View.GONE
        } else {
            tabLargeFiles.setTextColor(Color.parseColor("#FF5252"))
            tabDuplicates.setTextColor(Color.parseColor("#9AA0A6"))
            layoutLargeFilesView.visibility = View.VISIBLE
            layoutDuplicatesView.visibility = View.GONE
        }
    }

    private fun loadLargeFiles() {
        progressAnalyzer.visibility = View.VISIBLE
        executor.execute {
            val largeFiles = repository.getLargeFiles(thresholdBytes = 25L * 1024 * 1024, limit = 50)
            runOnUiThread {
                progressAnalyzer.visibility = View.GONE
                largeFilesAdapter.updateData(largeFiles)
            }
        }
    }

    private fun startCryptographicDuplicateScan() {
        btnStartDuplicateScan.isEnabled = false
        tvDuplicateProgress.visibility = View.VISIBLE
        tvDuplicateProgress.text = "Phase 1: Grouping files by exact size…"
        progressAnalyzer.visibility = View.VISIBLE
        cancelFlag.set(false)

        executor.execute {
            val root = repository.getPrimaryStorageRoot()
            val sizeCandidates = duplicateDetector.findPotentialDuplicatesBySize(root, minSizeBytes = 10240L, cancelFlag = cancelFlag)

            runOnUiThread {
                val totalCandidates = sizeCandidates.values.sumOf { it.size }
                tvDuplicateProgress.text = "Phase 2: Calculating SHA-256 cryptographic hashes for $totalCandidates candidate files…"
            }

            val verified = duplicateDetector.verifyDuplicatesWithHash(sizeCandidates, cancelFlag) { scanned, total ->
                runOnUiThread {
                    tvDuplicateProgress.text = "Verifying hashes: $scanned of $total candidate files…"
                }
            }

            runOnUiThread {
                btnStartDuplicateScan.isEnabled = true
                progressAnalyzer.visibility = View.GONE
                tvDuplicateProgress.text = "Found ${verified.size} duplicate file group(s)."
                duplicateGroups = verified
                listViewDuplicates.adapter = DuplicateAdapter(verified)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelFlag.set(true)
    }

    private inner class DuplicateAdapter(val groups: List<DuplicateGroup>) : BaseAdapter() {
        override fun getCount(): Int = groups.size
        override fun getItem(position: Int): DuplicateGroup = groups[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LinearLayout(this@AnalyzerActivity).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card)
                setPadding(24, 24, 24, 24)
            }

            val container = view as LinearLayout
            container.removeAllViews()

            val group = groups[position]
            val header = TextView(this@AnalyzerActivity).apply {
                text = "Duplicate Set (${group.items.size} copies • ${Formatters.formatFileSize(group.size)} each)"
                setTextColor(Color.parseColor("#E53935"))
                textSize = 14f
                setPadding(0, 0, 0, 8)
            }
            container.addView(header)

            for (item in group.items) {
                val itemRow = LinearLayout(this@AnalyzerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6, 0, 6)
                }

                val pathTv = TextView(this@AnalyzerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = item.file.absolutePath
                    setTextColor(Color.parseColor("#F0F2F5"))
                    textSize = 12f
                    setOnClickListener {
                        FileViewerHelper.openFile(this@AnalyzerActivity, item)
                    }
                }

                val deleteBtn = TextView(this@AnalyzerActivity).apply {
                    text = "Delete"
                    setTextColor(Color.parseColor("#FF5252"))
                    textSize = 12f
                    setPadding(12, 4, 12, 4)
                    setOnClickListener {
                        confirmDeleteSingle(item)
                    }
                }

                itemRow.addView(pathTv)
                itemRow.addView(deleteBtn)
                container.addView(itemRow)
            }

            return view
        }

        private fun confirmDeleteSingle(item: FileItem) {
            AlertDialog.Builder(this@AnalyzerActivity)
                .setTitle("Delete Duplicate Copy")
                .setMessage("Delete duplicate file at:\n${item.file.absolutePath}?\n\nOther copies will remain intact.")
                .setPositiveButton("Delete") { _, _ ->
                    if (item.file.delete()) {
                        Toast.makeText(this@AnalyzerActivity, "Duplicate copy removed.", Toast.LENGTH_SHORT).show()
                        startCryptographicDuplicateScan()
                    } else {
                        Toast.makeText(this@AnalyzerActivity, "Failed to delete copy.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
