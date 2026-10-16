package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.analysis.DuplicateDetector
import com.artraw.files.analysis.DuplicateGroup
import com.artraw.files.model.AppStorageInfo
import com.artraw.files.model.FileItem
import com.artraw.files.repository.FileRepository
import com.artraw.files.ui.adapter.FileListAdapter
import com.artraw.files.util.Formatters
import java.io.File
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

    private lateinit var layoutDuplicateSelectionActions: LinearLayout
    private lateinit var btnAutoSelectDuplicates: Button
    private lateinit var btnSelectAllDuplicates: Button
    private lateinit var btnDeselectAllDuplicates: Button
    private lateinit var layoutDuplicateDeleteBar: LinearLayout
    private lateinit var tvDuplicateSelectedSummary: TextView
    private lateinit var btnDeleteSelectedDuplicates: Button

    // App Storage Analysis
    private lateinit var tabApps: TextView
    private lateinit var layoutAppsView: LinearLayout
    private lateinit var listViewApps: ListView
    private var appsAdapter: AppStorageAdapter? = null

    // WiFi Transfer
    private lateinit var tabWifi: TextView
    private lateinit var layoutWifiView: LinearLayout

    private lateinit var largeFilesAdapter: FileListAdapter
    private var duplicateGroups: List<DuplicateGroup> = emptyList()
    private var duplicateAdapter: DuplicateAdapter? = null
    private val selectedDuplicatePaths = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)
        setContentView(R.layout.activity_analyzer)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerAnalyzer)),
            bottomScrollViews = listOf(findViewById(R.id.listViewLargeFiles), findViewById(R.id.listViewDuplicates)),
            bottomStickyViews = listOf(findViewById(R.id.layoutDuplicateDeleteBar))
        )

        initViews()
        setupListeners()

        val initialTab = intent.getStringExtra("tab") ?: "large"
        if (initialTab == "duplicates") {
            selectTab(isDuplicates = true)
        } else if (initialTab == "apps") {
            selectTab(isDuplicates = false, isApps = true)
            loadApps()
        } else {
            selectTab(isDuplicates = false)
            loadLargeFiles()
        }
    }

    private fun initViews() {
        tabLargeFiles = findViewById(R.id.tabLargeFiles)
        tabDuplicates = findViewById(R.id.tabDuplicates)
        tabApps = findViewById(R.id.tabApps)
        tabWifi = findViewById(R.id.tabWifi)
        layoutLargeFilesView = findViewById(R.id.layoutLargeFilesView)
        layoutDuplicatesView = findViewById(R.id.layoutDuplicatesView)
        layoutAppsView = findViewById(R.id.layoutAppsView)
        layoutWifiView = findViewById(R.id.layoutWifiView)
        listViewApps = findViewById(R.id.listViewApps)
        listViewLargeFiles = findViewById(R.id.listViewLargeFiles)
        listViewDuplicates = findViewById(R.id.listViewDuplicates)
        btnStartDuplicateScan = findViewById(R.id.btnStartDuplicateScan)
        tvDuplicateProgress = findViewById(R.id.tvDuplicateProgress)
        progressAnalyzer = findViewById(R.id.progressAnalyzer)

        layoutDuplicateSelectionActions = findViewById(R.id.layoutDuplicateSelectionActions)
        btnAutoSelectDuplicates = findViewById(R.id.btnAutoSelectDuplicates)
        btnSelectAllDuplicates = findViewById(R.id.btnSelectAllDuplicates)
        btnDeselectAllDuplicates = findViewById(R.id.btnDeselectAllDuplicates)
        layoutDuplicateDeleteBar = findViewById(R.id.layoutDuplicateDeleteBar)
        tvDuplicateSelectedSummary = findViewById(R.id.tvDuplicateSelectedSummary)
        btnDeleteSelectedDuplicates = findViewById(R.id.btnDeleteSelectedDuplicates)

        findViewById<View>(R.id.btnAnalyzerBack).setOnClickListener {
            finish()
        }

        tabApps.setOnClickListener {
            selectTab(isDuplicates = false, isApps = true)
            loadApps()
        }

        tabWifi.setOnClickListener {
            selectTab(isDuplicates = false, isApps = false, isWifi = true)
        }

        findViewById<View>(R.id.btnOpenWifiTransfer).setOnClickListener {
            startWifiTransfer()
        }

        largeFilesAdapter = FileListAdapter(this, emptyList(), isGrid = false) { }
        listViewLargeFiles.adapter = largeFilesAdapter

        listViewLargeFiles.setOnItemClickListener { _, _, position, _ ->
            val item = largeFilesAdapter.getItem(position)
            com.artraw.files.ui.dialog.FileActionDialog.show(this, item)
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

        btnAutoSelectDuplicates.setOnClickListener {
            autoSelectDuplicateCopies()
        }

        btnSelectAllDuplicates.setOnClickListener {
            selectAllDuplicates()
        }

        btnDeselectAllDuplicates.setOnClickListener {
            deselectAllDuplicates()
        }

        btnDeleteSelectedDuplicates.setOnClickListener {
            confirmDeleteSelectedDuplicates()
        }
    }

    private fun selectTab(isDuplicates: Boolean, isApps: Boolean = false, isWifi: Boolean = false) {
        if (isWifi) {
            tabWifi.setTextColor(Color.parseColor("#4ECDC4"))
            tabLargeFiles.setTextColor(Color.parseColor("#9AA5B1"))
            tabDuplicates.setTextColor(Color.parseColor("#9AA5B1"))
            tabApps.setTextColor(Color.parseColor("#9AA5B1"))
            layoutWifiView.visibility = View.VISIBLE
            layoutLargeFilesView.visibility = View.GONE
            layoutDuplicatesView.visibility = View.GONE
            layoutAppsView.visibility = View.GONE
        } else if (isApps) {
            tabApps.setTextColor(Color.parseColor("#4ECDC4"))
            tabLargeFiles.setTextColor(Color.parseColor("#9AA5B1"))
            tabDuplicates.setTextColor(Color.parseColor("#9AA5B1"))
            tabWifi.setTextColor(Color.parseColor("#9AA5B1"))
            layoutAppsView.visibility = View.VISIBLE
            layoutLargeFilesView.visibility = View.GONE
            layoutDuplicatesView.visibility = View.GONE
            layoutWifiView.visibility = View.GONE
        } else if (isDuplicates) {
            tabDuplicates.setTextColor(Color.parseColor("#8338EC"))
            tabLargeFiles.setTextColor(Color.parseColor("#9AA5B1"))
            tabApps.setTextColor(Color.parseColor("#9AA5B1"))
            tabWifi.setTextColor(Color.parseColor("#9AA5B1"))
            layoutDuplicatesView.visibility = View.VISIBLE
            layoutLargeFilesView.visibility = View.GONE
            layoutAppsView.visibility = View.GONE
            layoutWifiView.visibility = View.GONE
        } else {
            tabLargeFiles.setTextColor(Color.parseColor("#4ECDC4"))
            tabDuplicates.setTextColor(Color.parseColor("#9AA5B1"))
            tabApps.setTextColor(Color.parseColor("#9AA5B1"))
            tabWifi.setTextColor(Color.parseColor("#9AA5B1"))
            layoutLargeFilesView.visibility = View.VISIBLE
            layoutDuplicatesView.visibility = View.GONE
            layoutAppsView.visibility = View.GONE
            layoutWifiView.visibility = View.GONE
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

    private fun loadApps() {
        progressAnalyzer.visibility = View.VISIBLE
        executor.execute {
            val apps = repository.getInstalledAppsWithStorage()
            runOnUiThread {
                progressAnalyzer.visibility = View.GONE
                appsAdapter = AppStorageAdapter(apps)
                listViewApps.adapter = appsAdapter
            }
        }
    }

    private fun startWifiTransfer() {
        val intent = Intent(this, WiFiTransferActivity::class.java)
        startActivity(intent)
    }

    private fun startCryptographicDuplicateScan() {
        btnStartDuplicateScan.isEnabled = false
        tvDuplicateProgress.visibility = View.VISIBLE
        tvDuplicateProgress.text = "Phase 1: Grouping files by exact size…"
        progressAnalyzer.visibility = View.VISIBLE
        cancelFlag.set(false)
        selectedDuplicatePaths.clear()
        updateSelectionSummary()

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

                if (verified.isNotEmpty()) {
                    layoutDuplicateSelectionActions.visibility = View.VISIBLE
                    val adapter = DuplicateAdapter(verified)
                    duplicateAdapter = adapter
                    listViewDuplicates.adapter = adapter
                    autoSelectDuplicateCopies()
                } else {
                    layoutDuplicateSelectionActions.visibility = View.GONE
                    layoutDuplicateDeleteBar.visibility = View.GONE
                    listViewDuplicates.adapter = null
                }
            }
        }
    }

    private fun autoSelectDuplicateCopies() {
        selectedDuplicatePaths.clear()
        for (group in duplicateGroups) {
            for (i in 1 until group.items.size) {
                selectedDuplicatePaths.add(group.items[i].file.absolutePath)
            }
        }
        duplicateAdapter?.notifyDataSetChanged()
        updateSelectionSummary()
    }

    private fun selectAllDuplicates() {
        selectedDuplicatePaths.clear()
        for (group in duplicateGroups) {
            for (item in group.items) {
                selectedDuplicatePaths.add(item.file.absolutePath)
            }
        }
        duplicateAdapter?.notifyDataSetChanged()
        updateSelectionSummary()
    }

    private fun deselectAllDuplicates() {
        selectedDuplicatePaths.clear()
        duplicateAdapter?.notifyDataSetChanged()
        updateSelectionSummary()
    }

    private fun updateSelectionSummary() {
        val count = selectedDuplicatePaths.size
        if (count > 0) {
            layoutDuplicateDeleteBar.visibility = View.VISIBLE
            var totalBytes = 0L
            for (group in duplicateGroups) {
                for (item in group.items) {
                    if (selectedDuplicatePaths.contains(item.file.absolutePath)) {
                        totalBytes += item.size
                    }
                }
            }
            val sizeStr = Formatters.formatFileSize(totalBytes)
            tvDuplicateSelectedSummary.text = "$count file(s) selected ($sizeStr)"
            btnDeleteSelectedDuplicates.text = "Delete ($sizeStr)"
        } else {
            layoutDuplicateDeleteBar.visibility = View.GONE
        }
    }

    private fun confirmDeleteSelectedDuplicates() {
        val toDelete = mutableListOf<FileItem>()
        var totalBytes = 0L
        for (group in duplicateGroups) {
            for (item in group.items) {
                if (selectedDuplicatePaths.contains(item.file.absolutePath)) {
                    toDelete.add(item)
                    totalBytes += item.size
                }
            }
        }

        if (toDelete.isEmpty()) return
        val sizeStr = Formatters.formatFileSize(totalBytes)

        AlertDialog.Builder(this)
            .setTitle("Confirm Duplicate Deletion")
            .setMessage("Are you sure you want to delete ${toDelete.size} selected duplicate file(s)?\n\nThis will free up $sizeStr of storage. Original copies will be preserved.")
            .setPositiveButton("Delete Permanently") { _, _ ->
                progressAnalyzer.visibility = View.VISIBLE
                executor.execute {
                    var deletedCount = 0
                    for (item in toDelete) {
                        if (item.file.delete()) {
                            deletedCount++
                        }
                    }

                    runOnUiThread {
                        progressAnalyzer.visibility = View.GONE
                        Toast.makeText(this@AnalyzerActivity, "Cleaned $deletedCount duplicate(s). Reclaimed $sizeStr!", Toast.LENGTH_LONG).show()
                        startCryptographicDuplicateScan()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                setPadding(24, 20, 24, 20)
                val params = AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT
                )
                layoutParams = params
            }

            val container = view as LinearLayout
            container.removeAllViews()

            val group = groups[position]
            val fileName = group.items.firstOrNull()?.name ?: "File"
            val copyCount = group.items.size
            val sizeStr = Formatters.formatFileSize(group.size)

            val header = TextView(this@AnalyzerActivity).apply {
                text = "$fileName ($copyCount copies • $sizeStr each)"
                setTextColor(Color.parseColor("#E53935"))
                textSize = 14f
                setPadding(0, 0, 0, 8)
            }
            container.addView(header)

            for ((idx, item) in group.items.withIndex()) {
                val isSelected = selectedDuplicatePaths.contains(item.file.absolutePath)
                val itemRow = LinearLayout(this@AnalyzerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 8, 0, 8)
                }

                val cb = CheckBox(this@AnalyzerActivity).apply {
                    isChecked = isSelected
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) {
                            selectedDuplicatePaths.add(item.file.absolutePath)
                        } else {
                            selectedDuplicatePaths.remove(item.file.absolutePath)
                        }
                        updateSelectionSummary()
                    }
                }

                val labelBadge = TextView(this@AnalyzerActivity).apply {
                    text = if (idx == 0) "ORIGINAL" else "COPY"
                    setTextColor(if (idx == 0) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800"))
                    textSize = 10f
                    setPadding(8, 2, 8, 2)
                    setBackgroundResource(R.drawable.bg_badge)
                }

                val pathTv = TextView(this@AnalyzerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                    }
                    text = item.file.parent ?: item.file.absolutePath
                    setTextColor(Color.parseColor("#F0F2F5"))
                    textSize = 12f
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    setOnClickListener {
                        com.artraw.files.ui.dialog.FileActionDialog.show(this@AnalyzerActivity, item)
                    }
                }

                itemRow.addView(cb)
                itemRow.addView(labelBadge)
                itemRow.addView(pathTv)
                container.addView(itemRow)
            }

            return view
        }
    }

    // ========================================================================
    // App Storage Adapter — Shows installed apps with storage usage
    // ========================================================================
    private inner class AppStorageAdapter(private val apps: List<AppStorageInfo>) : BaseAdapter() {

        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): AppStorageInfo = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: AppStorageViewHolder

            if (convertView == null) {
                view = LinearLayout(this@AnalyzerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundResource(R.drawable.bg_aurora_card)
                    val params = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = params
                }
                val container = view as LinearLayout

                val icon = ImageView(this@AnalyzerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 48)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                container.addView(icon)

                val info = LinearLayout(this@AnalyzerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                    }
                }
                container.addView(info)

                val nameTv = TextView(this@AnalyzerActivity).apply {
                    textSize = 15f
                    setTextColor(Color.parseColor("#F0F4F8"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(nameTv)

                val metaTv = TextView(this@AnalyzerActivity).apply {
                    textSize = 12f
                    setTextColor(Color.parseColor("#9AA5B1"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(metaTv)

                val sizeTv = TextView(this@AnalyzerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    textSize = 14f
                    setTextColor(Color.parseColor("#4ECDC4"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                container.addView(sizeTv)

                holder = AppStorageViewHolder(icon = icon, nameTv = nameTv, metaTv = metaTv, sizeTv = sizeTv)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as AppStorageViewHolder
            }

            val app = apps[position]

            // Load app icon
            try {
                val drawable = this@AnalyzerActivity.packageManager.getApplicationIcon(app.packageName)
                holder.icon.setImageDrawable(drawable)
            } catch (e: Exception) {
                holder.icon.setImageResource(R.drawable.ic_apk)
            }

            holder.nameTv.text = app.appName
            holder.metaTv.text = "${app.packageName} • v${app.versionName}"
            holder.sizeTv.text = app.formattedSize

            return view
        }

    }
}

// ========================================================================
// Standalone ViewHolder for AppStorageAdapter
// ========================================================================
class AppStorageViewHolder(
    val icon: ImageView,
    val nameTv: TextView,
    val metaTv: TextView,
    val sizeTv: TextView
)
