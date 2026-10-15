package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.ad.AdMobManager
import com.artraw.files.model.FileCategory
import com.google.android.gms.ads.AdView
import com.artraw.files.model.FileItem
import com.artraw.files.model.SortDirection
import com.artraw.files.model.SortField
import com.artraw.files.model.SortOption
import com.artraw.files.operations.ClipboardMode
import com.artraw.files.operations.ConflictMode
import com.artraw.files.operations.FileOperationManager
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.ui.adapter.FileListAdapter
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.PathSecurity
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BrowserActivity : Activity() {

    private lateinit var repository: FileRepository
    private lateinit var settingsManager: SettingsManager
    private val operationManager = FileOperationManager()
    private val executor = Executors.newSingleThreadExecutor()

    private var currentDirectory: File = File("/storage/emulated/0")
    private var allItems: List<FileItem> = emptyList()
    private var displayedItems: List<FileItem> = emptyList()

    private lateinit var toolbarNormal: RelativeLayout
    private lateinit var toolbarSelection: RelativeLayout
    private lateinit var tvCurrentFolderName: TextView
    private lateinit var tvSelectionCount: TextView
    private lateinit var layoutSearchBar: LinearLayout
    private lateinit var etSearchQuery: EditText
    private lateinit var layoutBreadcrumbs: LinearLayout
    private lateinit var layoutPasteBar: RelativeLayout
    private lateinit var tvPasteStatus: TextView
    private lateinit var btnExecutePaste: Button
    private lateinit var btnCancelPaste: Button

    private lateinit var listViewFiles: ListView
    private lateinit var gridViewFiles: GridView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var progressLoading: ProgressBar

    private lateinit var listAdapter: FileListAdapter
    private lateinit var gridAdapter: FileListAdapter

    private lateinit var tvPcPathDisplay: TextView
    private var isGridMode: Boolean = false
    private var adViewContainerBrowser: FrameLayout? = null
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        repository = FileRepository(this)
        isGridMode = settingsManager.isGridView

        setContentView(R.layout.activity_browser)

        adViewContainerBrowser = findViewById(R.id.adViewContainerBrowser)

        val stickyViews = mutableListOf<View>(
            findViewById(R.id.layoutPasteBar),
            findViewById(R.id.fabNewFolder)
        )
        adViewContainerBrowser?.let { stickyViews.add(it) }

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerBrowser)),
            bottomScrollViews = listOf(findViewById(R.id.listViewFiles), findViewById(R.id.gridViewFiles)),
            bottomStickyViews = stickyViews
        )

        val pathArg = intent.getStringExtra("initial_path")
        currentDirectory = if (!pathArg.isNullOrBlank()) File(pathArg) else repository.getPrimaryStorageRoot()

        initViews()
        setupListeners()
        loadDirectory(currentDirectory)

        adView = AdMobManager.loadBanner(this, adViewContainerBrowser)
    }

    override fun onResume() {
        super.onResume()
        try {
            adView?.resume()
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try {
            adView?.pause()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroy() {
        try {
            adView?.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun initViews() {
        toolbarNormal = findViewById(R.id.toolbarNormal)
        toolbarSelection = findViewById(R.id.toolbarSelection)
        tvCurrentFolderName = findViewById(R.id.tvCurrentFolderName)
        tvPcPathDisplay = findViewById(R.id.tvPcPathDisplay)
        tvSelectionCount = findViewById(R.id.tvSelectionCount)
        layoutSearchBar = findViewById(R.id.layoutSearchBar)
        etSearchQuery = findViewById(R.id.etSearchQuery)
        layoutBreadcrumbs = findViewById(R.id.layoutBreadcrumbs)
        layoutPasteBar = findViewById(R.id.layoutPasteBar)
        tvPasteStatus = findViewById(R.id.tvPasteStatus)
        btnExecutePaste = findViewById(R.id.btnExecutePaste)
        btnCancelPaste = findViewById(R.id.btnCancelPaste)

        listViewFiles = findViewById(R.id.listViewFiles)
        gridViewFiles = findViewById(R.id.gridViewFiles)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        progressLoading = findViewById(R.id.progressLoading)

        listAdapter = FileListAdapter(this, emptyList(), isGrid = false) { count ->
            updateSelectionUi(count)
        }
        gridAdapter = FileListAdapter(this, emptyList(), isGrid = true) { count ->
            updateSelectionUi(count)
        }

        listViewFiles.adapter = listAdapter
        gridViewFiles.adapter = gridAdapter

        updateViewMode()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack).setOnClickListener {
            handleBackNavigation()
        }

        findViewById<View>(R.id.btnCloseSelection).setOnClickListener {
            activeAdapter().clearSelection()
        }

        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            loadDirectory(currentDirectory)
        }

        findViewById<View>(R.id.btnViewToggle).setOnClickListener {
            isGridMode = !isGridMode
            settingsManager.isGridView = isGridMode
            updateViewMode()
        }

        findViewById<View>(R.id.btnSort).setOnClickListener {
            showSortDialog()
        }

        findViewById<View>(R.id.btnSearch).setOnClickListener {
            if (layoutSearchBar.visibility == View.VISIBLE) {
                layoutSearchBar.visibility = View.GONE
                etSearchQuery.setText("")
            } else {
                layoutSearchBar.visibility = View.VISIBLE
                etSearchQuery.requestFocus()
            }
        }

        findViewById<View>(R.id.btnClearSearch).setOnClickListener {
            etSearchQuery.setText("")
            layoutSearchBar.visibility = View.GONE
        }

        findViewById<View>(R.id.layoutPcAddressBar).setOnClickListener {
            showTypePathDialog()
        }

        etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterItems(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // File Item Click: Provide Open and Copy Path options
        val onItemClick = { position: Int ->
            val adapter = activeAdapter()
            if (adapter.isSelectionMode) {
                adapter.toggleSelection(adapter.getItem(position))
            } else {
                val item = adapter.getItem(position)
                com.artraw.files.ui.dialog.FileActionDialog.show(this, item, onOpen = {
                    if (item.isDirectory) {
                        if (PathSecurity.isAndroidRestrictedStorage(item.file)) {
                            Toast.makeText(this, R.string.perm_restricted_notice, Toast.LENGTH_LONG).show()
                        } else {
                            loadDirectory(item.file)
                        }
                    } else {
                        FileViewerHelper.openFile(this, item)
                    }
                })
            }
        }

        listViewFiles.setOnItemClickListener { _, _, position, _ -> onItemClick(position) }
        gridViewFiles.setOnItemClickListener { _, _, position, _ -> onItemClick(position) }

        // Long Press to start multi-select
        val onItemLongClick = { position: Int ->
            val adapter = activeAdapter()
            val item = adapter.getItem(position)
            if (!adapter.isSelectionMode) {
                adapter.startSelection(item)
            } else {
                adapter.toggleSelection(item)
            }
            true
        }

        listViewFiles.setOnItemLongClickListener { _, _, position, _ -> onItemLongClick(position) }
        gridViewFiles.setOnItemLongClickListener { _, _, position, _ -> onItemLongClick(position) }

        // Contextual Actions
        findViewById<View>(R.id.btnActionCopy).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            operationManager.setClipboard(selected, ClipboardMode.COPY)
            activeAdapter().clearSelection()
            updatePasteBar()
            Toast.makeText(this, "${selected.size} items copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnActionCut).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            operationManager.setClipboard(selected, ClipboardMode.CUT)
            activeAdapter().clearSelection()
            updatePasteBar()
            Toast.makeText(this, "${selected.size} items cut to clipboard", Toast.LENGTH_SHORT).show()
        }

        findViewById<View>(R.id.btnActionShare).setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            FileViewerHelper.shareFiles(this, selected)
            activeAdapter().clearSelection()
        }

        findViewById<View>(R.id.btnActionCompress)?.setOnClickListener {
            val selected = activeAdapter().getSelectedItems()
            if (selected.isNotEmpty()) {
                showCompressDialog(selected.map { it.file })
            }
        }

        findViewById<View>(R.id.btnActionDelete).setOnClickListener {
            confirmDelete(activeAdapter().getSelectedItems())
        }

        findViewById<View>(R.id.btnActionMore).setOnClickListener {
            showMoreMenu(activeAdapter().getSelectedItems())
        }

        btnExecutePaste.setOnClickListener {
            promptPasteConflictResolution()
        }

        btnCancelPaste.setOnClickListener {
            operationManager.clearClipboard()
            updatePasteBar()
        }

        findViewById<View>(R.id.btnNewFolder)?.setOnClickListener {
            showNewFolderDialog()
        }

        findViewById<View>(R.id.fabNewFolder)?.setOnClickListener {
            showNewFolderDialog()
        }

        findViewById<View>(R.id.btnActionSelectAll)?.setOnClickListener {
            activeAdapter().selectAll()
        }

        findViewById<View>(R.id.btnTypePath).setOnClickListener {
            showTypePathDialog()
        }

        findViewById<View>(R.id.btnCopyCurrentPath).setOnClickListener {
            copyToClipboard("Path", currentDirectory.absolutePath)
            Toast.makeText(this, "Current path copied: ${currentDirectory.absolutePath}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun activeAdapter(): FileListAdapter = if (isGridMode) gridAdapter else listAdapter

    private fun updateViewMode() {
        val icon = findViewById<ImageView>(R.id.btnViewToggle)
        if (isGridMode) {
            listViewFiles.visibility = View.GONE
            gridViewFiles.visibility = View.VISIBLE
            icon.setImageResource(R.drawable.ic_list)
        } else {
            listViewFiles.visibility = View.VISIBLE
            gridViewFiles.visibility = View.GONE
            icon.setImageResource(R.drawable.ic_grid)
        }
    }

    private fun loadDirectory(dir: File) {
        currentDirectory = dir
        tvCurrentFolderName.text = if (dir.name.isEmpty()) "Storage" else dir.name
        tvPcPathDisplay.text = dir.absolutePath
        updateBreadcrumbs(dir)
        progressLoading.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE

        executor.execute {
            val items = repository.listFiles(
                directory = dir,
                showHidden = settingsManager.showHiddenFiles,
                sortOption = settingsManager.getSortOption()
            )
            runOnUiThread {
                progressLoading.visibility = View.GONE
                allItems = items
                displayedItems = items
                listAdapter.updateData(items)
                gridAdapter.updateData(items)

                if (items.isEmpty()) {
                    layoutEmptyState.visibility = View.VISIBLE
                } else {
                    layoutEmptyState.visibility = View.GONE
                }
                updatePasteBar()
            }
        }
    }

    private fun filterItems(query: String) {
        displayedItems = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { it.name.contains(query, ignoreCase = true) }
        }
        listAdapter.updateData(displayedItems)
        gridAdapter.updateData(displayedItems)
        layoutEmptyState.visibility = if (displayedItems.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateBreadcrumbs(dir: File) {
        layoutBreadcrumbs.removeAllViews()
        val segments = mutableListOf<File>()
        var current: File? = dir
        while (current != null) {
            segments.add(0, current)
            if (current.absolutePath == "/storage/emulated/0" || current.parentFile == null) break
            current = current.parentFile
        }

        for ((index, segment) in segments.withIndex()) {
            val tv = TextView(this)
            tv.text = if (segment.absolutePath == "/storage/emulated/0") "Internal" else segment.name
            tv.setTextColor(if (index == segments.size - 1) Color.parseColor("#E53935") else Color.parseColor("#9AA0A6"))
            tv.textSize = 13f
            tv.setPadding(8, 4, 8, 4)
            tv.setOnClickListener {
                loadDirectory(segment)
            }
            layoutBreadcrumbs.addView(tv)

            if (index < segments.size - 1) {
                val sep = TextView(this)
                sep.text = "›"
                sep.setTextColor(Color.parseColor("#5F6368"))
                sep.setPadding(2, 4, 2, 4)
                layoutBreadcrumbs.addView(sep)
            }
        }
    }

    private fun updateSelectionUi(selectedCount: Int) {
        val fab = findViewById<View>(R.id.fabNewFolder)
        if (selectedCount > 0) {
            toolbarNormal.visibility = View.GONE
            toolbarSelection.visibility = View.VISIBLE
            fab?.visibility = View.GONE
            val totalBytes = activeAdapter().getSelectedItems().sumOf { it.size }
            tvSelectionCount.text = getString(R.string.selected_items_count, selectedCount, Formatters.formatFileSize(totalBytes))
        } else {
            toolbarSelection.visibility = View.GONE
            toolbarNormal.visibility = View.VISIBLE
            fab?.visibility = View.VISIBLE
        }
    }

    private fun updatePasteBar() {
        val clip = operationManager.getClipboard()
        if (clip != null) {
            layoutPasteBar.visibility = View.VISIBLE
            val count = clip.first.size
            val isCut = (clip.second == ClipboardMode.CUT)
            val modeStr = if (isCut) "move" else "copy"
            tvPasteStatus.text = "$count item(s) ready to $modeStr into this folder"
            btnExecutePaste.text = if (isCut) "Move Here" else "Paste Here"
        } else {
            layoutPasteBar.visibility = View.GONE
        }
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name"
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#9AA0A6"))
            background = getDrawable(R.drawable.bg_search_bar)
            setPadding(36, 24, 36, 24)
        }

        val container = FrameLayout(this).apply {
            setPadding(48, 24, 48, 12)
            addView(input)
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("New Folder")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (name.contains("/") || name.contains("\\") || name.contains(":") || name.contains("*") || name.contains("?")) {
                    Toast.makeText(this, "Invalid characters in folder name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val newDir = File(currentDirectory, name)
                if (newDir.exists()) {
                    Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val created = newDir.mkdirs()
                if (created) {
                    Toast.makeText(this, "Folder created: $name", Toast.LENGTH_SHORT).show()
                    loadDirectory(currentDirectory)
                } else {
                    Toast.makeText(this, "Failed to create folder", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmDelete(items: List<FileItem>) {
        if (items.isEmpty()) return
        val totalSize = items.sumOf { it.size }
        val sizeStr = Formatters.formatFileSize(totalSize)

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_msg, items.size, sizeStr))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val result = operationManager.deleteFiles(items)
                activeAdapter().clearSelection()
                loadDirectory(currentDirectory)
                if (result.failedItems.isNotEmpty()) {
                    Toast.makeText(this, "Failed to delete ${result.failedItems.size} items.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Deleted ${result.successfulItems.size} items.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun promptPasteConflictResolution() {
        val options = arrayOf("Replace Existing Files", "Keep Both (Rename Duplicates)", "Skip Conflicts")
        AlertDialog.Builder(this)
            .setTitle("Conflict Handling")
            .setItems(options) { _, which ->
                val mode = when (which) {
                    0 -> ConflictMode.REPLACE
                    1 -> ConflictMode.KEEP_BOTH
                    else -> ConflictMode.SKIP
                }
                executePasteOperation(mode)
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun executePasteOperation(conflictMode: ConflictMode) {
        val cancelFlag = AtomicBoolean(false)
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Processing Files")
            setMessage("Preparing operation…")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            setButton(ProgressDialog.BUTTON_NEGATIVE, getString(R.string.btn_cancel)) { _, _ ->
                cancelFlag.set(true)
            }
            show()
        }

        executor.execute {
            val result = operationManager.executePaste(
                destinationDir = currentDirectory,
                conflictMode = conflictMode,
                cancelFlag = cancelFlag
            ) { progress ->
                runOnUiThread {
                    progressDialog.setMessage("Copying: ${progress.currentFileName} (${progress.currentFileIndex}/${progress.totalFiles})")
                    if (progress.totalBytes > 0) {
                        progressDialog.progress = ((progress.bytesTransferred * 100) / progress.totalBytes).toInt()
                    }
                }
            }

            runOnUiThread {
                progressDialog.dismiss()
                loadDirectory(currentDirectory)
                updatePasteBar()

                if (result.canUndo && result.undoAction != null) {
                    AlertDialog.Builder(this)
                        .setTitle("Operation Finished")
                        .setMessage("Moved ${result.successfulItems.size} files.")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Undo Move") { _, _ ->
                            result.undoAction.invoke()
                            loadDirectory(currentDirectory)
                            Toast.makeText(this, "Move undone.", Toast.LENGTH_SHORT).show()
                        }
                        .show()
                } else {
                    if (result.failedItems.isNotEmpty()) {
                        val firstError = result.failedItems.values.first()
                        Toast.makeText(this, "Errors occurred: $firstError", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Operation completed successfully.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun showMoreMenu(items: List<FileItem>) {
        if (items.isEmpty()) return
        val options = if (items.size == 1) {
            arrayOf("Copy Path", "Rename", "Details", "Open With", "Select All")
        } else {
            arrayOf("Copy Paths", "Details", "Select All")
        }

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Copy Path", "Copy Paths" -> {
                        val paths = items.joinToString("\n") { it.path }
                        copyToClipboard("Path", paths)
                        activeAdapter().clearSelection()
                        Toast.makeText(this, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    "Rename" -> showRenameDialog(items[0])
                    "Details" -> showDetailsDialog(items)
                    "Open With" -> {
                        FileViewerHelper.openFile(this, items[0])
                        activeAdapter().clearSelection()
                    }
                    "Select All" -> activeAdapter().selectAll()
                }
            }
            .show()
    }

    private fun showRenameDialog(item: FileItem) {
        val input = EditText(this).apply {
            setText(item.name)
            setSelection(item.name.lastIndexOf('.').coerceAtLeast(0))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(input)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                val newName = input.text.toString().trim()
                val (ok, error) = operationManager.renameFile(item, newName)
                if (ok) {
                    activeAdapter().clearSelection()
                    loadDirectory(currentDirectory)
                } else {
                    Toast.makeText(this, error ?: "Rename failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun showDetailsDialog(items: List<FileItem>) {
        if (items.size == 1) {
            val item = items[0]
            val msg = buildString {
                append("Name: ${item.name}\n\n")
                append("Path: ${item.path}\n\n")
                append("Type: ${if (item.isDirectory) "Folder" else item.mimeType}\n\n")
                append("Size: ${Formatters.formatFileSize(item.size)}\n\n")
                append("Modified: ${Formatters.formatDate(item.lastModified)}\n")
                if (item.isApk) {
                    append("\nPackage: ${item.apkPackageName ?: "Unknown"}\n")
                    append("Version: ${item.apkVersionName ?: "Unknown"}\n")
                }
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.action_details)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_ok, null)
                .setNeutralButton("Copy Path") { _, _ ->
                    copyToClipboard("Path", item.path)
                    Toast.makeText(this, "Path copied: ${item.path}", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            val totalSize = items.sumOf { it.size }
            val folders = items.count { it.isDirectory }
            val files = items.size - folders
            val msg = "Selected: ${items.size} items\n- Files: $files\n- Folders: $folders\nTotal Size: ${Formatters.formatFileSize(totalSize)}"
            AlertDialog.Builder(this)
                .setTitle(R.string.action_details)
                .setMessage(msg)
                .setPositiveButton(R.string.btn_ok, null)
                .setNeutralButton("Copy Paths") { _, _ ->
                    val paths = items.joinToString("\n") { it.path }
                    copyToClipboard("Paths", paths)
                    Toast.makeText(this, "Paths copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun showTypePathDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 12)
        }
        val input = EditText(this).apply {
            setText(currentDirectory.absolutePath)
            setSelection(text.length)
            hint = "e.g. /storage/emulated/0/Download"
        }
        val btnPaste = Button(this).apply {
            text = "Paste from Clipboard"
            setBackgroundResource(R.drawable.bg_button_outlined)
            setTextColor(Color.parseColor("#E53935"))
            setOnClickListener {
                val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipMgr.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val pasted = clipData.getItemAt(0).text?.toString()?.trim()
                    if (!pasted.isNullOrBlank()) {
                        input.setText(pasted)
                        input.setSelection(pasted.length)
                    }
                }
            }
        }
        layout.addView(input)
        layout.addView(btnPaste)

        AlertDialog.Builder(this)
            .setTitle("Go to Path")
            .setView(layout)
            .setPositiveButton("Go") { _, _ ->
                val typed = input.text.toString().trim()
                if (typed.isNotEmpty()) {
                    val target = File(typed)
                    if (target.exists()) {
                        if (target.isDirectory) {
                            if (PathSecurity.isAndroidRestrictedStorage(target)) {
                                Toast.makeText(this, R.string.perm_restricted_notice, Toast.LENGTH_LONG).show()
                            } else {
                                loadDirectory(target)
                            }
                        } else {
                            target.parentFile?.let { loadDirectory(it) }
                            FileViewerHelper.openFile(this, FileItem(file = target))
                        }
                    } else {
                        Toast.makeText(this, "Path does not exist: $typed", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipMgr.setPrimaryClip(clip)
    }

    private fun showSortDialog() {
        val fields = arrayOf("Name (A to Z)", "Name (Z to A)", "Date (Newest first)", "Date (Oldest first)", "Size (Largest first)", "Size (Smallest first)", "File Type")
        AlertDialog.Builder(this)
            .setTitle(R.string.action_sort)
            .setItems(fields) { _, which ->
                when (which) {
                    0 -> { settingsManager.sortField = SortField.NAME; settingsManager.sortDirection = SortDirection.ASCENDING }
                    1 -> { settingsManager.sortField = SortField.NAME; settingsManager.sortDirection = SortDirection.DESCENDING }
                    2 -> { settingsManager.sortField = SortField.DATE; settingsManager.sortDirection = SortDirection.DESCENDING }
                    3 -> { settingsManager.sortField = SortField.DATE; settingsManager.sortDirection = SortDirection.ASCENDING }
                    4 -> { settingsManager.sortField = SortField.SIZE; settingsManager.sortDirection = SortDirection.DESCENDING }
                    5 -> { settingsManager.sortField = SortField.SIZE; settingsManager.sortDirection = SortDirection.ASCENDING }
                    6 -> { settingsManager.sortField = SortField.TYPE; settingsManager.sortDirection = SortDirection.ASCENDING }
                }
                loadDirectory(currentDirectory)
            }
            .show()
    }

    private fun handleBackNavigation() {
        if (activeAdapter().isSelectionMode) {
            activeAdapter().clearSelection()
            return
        }
        val parent = currentDirectory.parentFile
        if (parent != null && parent.canRead() && currentDirectory.absolutePath != "/storage/emulated/0") {
            loadDirectory(parent)
        } else {
            finish()
        }
    }

    fun refreshCurrentDirectory() {
        loadDirectory(currentDirectory)
    }

    private fun showCompressDialog(files: List<File>) {
        val input = android.widget.EditText(this).apply {
            val defaultName = if (files.size == 1) "${files[0].nameWithoutExtension}.zip" else "Archive.zip"
            setText(defaultName)
            setSelection(defaultName.indexOf('.'))
            setTextColor(getColor(R.color.text_primary_dark))
            setHintTextColor(getColor(R.color.text_tertiary_dark))
        }

        AlertDialog.Builder(this)
            .setTitle("Compress to ZIP")
            .setMessage("Enter archive name:")
            .setView(input)
            .setPositiveButton("Compress") { _, _ ->
                val rawName = input.text.toString().trim()
                val name = if (rawName.endsWith(".zip", ignoreCase = true)) rawName else "$rawName.zip"
                val destZip = File(currentDirectory, name)
                val progressDialog = android.app.ProgressDialog(this).apply {
                    setTitle("Compressing to ZIP")
                    setMessage("Creating $name...")
                    setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
                    max = 100
                    setCancelable(false)
                    show()
                }

                executor.execute {
                    val ok = com.artraw.files.archive.ArchiveManager.compress(files, destZip) { pct, _ ->
                        runOnUiThread { progressDialog.progress = pct }
                    }
                    runOnUiThread {
                        progressDialog.dismiss()
                        if (ok) {
                            Toast.makeText(this@BrowserActivity, "Created: $name", Toast.LENGTH_LONG).show()
                            activeAdapter().clearSelection()
                            loadDirectory(currentDirectory)
                        } else {
                            Toast.makeText(this@BrowserActivity, "Compression failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onBackPressed() {
        handleBackNavigation()
    }
}
