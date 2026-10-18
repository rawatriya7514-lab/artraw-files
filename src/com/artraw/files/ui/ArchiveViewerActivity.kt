package com.artraw.files.ui

import android.app.Activity
import android.app.ProgressDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.archive.ArchiveEntryItem
import com.artraw.files.archive.ArchiveManager
import com.artraw.files.util.Formatters
import com.artraw.files.util.MimeTypes
import java.io.File
import java.util.concurrent.Executors

class ArchiveViewerActivity : Activity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var tvArchiveName: TextView
    private lateinit var tvArchiveSummary: TextView
    private lateinit var listEntries: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var btnExtractAll: Button

    private var archiveFile: File? = null
    private val entries = mutableListOf<ArchiveEntryItem>()
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive_viewer)

        val root = findViewById<View>(R.id.rootArchiveViewer)
        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerArchive)),
            bottomScrollViews = listOf(root)
        )

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, "Archive path not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        archiveFile = file
        initViews()
        setupListeners()
        loadArchiveEntries(file)
    }

    private fun initViews() {
        tvArchiveName = findViewById(R.id.tvArchiveName)
        tvArchiveSummary = findViewById(R.id.tvArchiveSummary)
        listEntries = findViewById(R.id.listArchiveEntries)
        tvEmpty = findViewById(R.id.tvArchiveEmpty)
        btnExtractAll = findViewById(R.id.btnExtractAll)

        tvArchiveName.text = archiveFile?.name ?: "Archive"
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBackArchive).setOnClickListener {
            finish()
        }

        btnExtractAll.setOnClickListener {
            archiveFile?.let { arc ->
                extractEntireArchive(arc)
            }
        }
    }

    private fun loadArchiveEntries(file: File) {
        tvEmpty.visibility = View.VISIBLE
        tvEmpty.text = "Inspecting archive contents..."
        executor.execute {
            val list = ArchiveManager.listEntries(file)
            var totalUncompressed = 0L
            for (e in list) {
                if (e.size > 0) totalUncompressed += e.size
            }

            runOnUiThread {
                entries.clear()
                entries.addAll(list)
                if (entries.isEmpty()) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Archive is empty or format unsupported."
                } else {
                    tvEmpty.visibility = View.GONE
                    val count = entries.size
                    val totalSizeStr = Formatters.formatFileSize(totalUncompressed)
                    tvArchiveSummary.text = "$count items • $totalSizeStr uncompressed"
                    listEntries.adapter = ArchiveEntryAdapter(entries)
                }
            }
        }
    }

    private fun extractEntireArchive(file: File) {
        val targetName = file.nameWithoutExtension
        val destDir = File(file.parentFile, targetName)

        val dialog = ProgressDialog(this).apply {
            setTitle("Extracting Archive")
            setMessage("Extracting to ${destDir.name}...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            setCancelable(false)
            show()
        }

        executor.execute {
            val success = ArchiveManager.extract(file, destDir) { percent, currentName ->
                runOnUiThread {
                    dialog.progress = percent
                    dialog.setMessage("Extracting: $currentName")
                }
            }
            runOnUiThread {
                dialog.dismiss()
                if (success) {
                    Toast.makeText(this, "Successfully extracted to ${destDir.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Extraction failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class ArchiveEntryAdapter(private val items: List<ArchiveEntryItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@ArchiveViewerActivity)
                .inflate(R.layout.item_archive_entry, parent, false)

            val item = items[position]
            val imgIcon = view.findViewById<ImageView>(R.id.imgEntryIcon)
            val tvName = view.findViewById<TextView>(R.id.tvEntryName)
            val tvDetails = view.findViewById<TextView>(R.id.tvEntryDetails)
            val btnExtract = view.findViewById<ImageView>(R.id.btnExtractEntry)

            tvName.text = item.name

            if (item.isDirectory) {
                imgIcon.setImageResource(R.drawable.ic_folder)
                tvDetails.text = "Folder"
            } else {
                val ext = MimeTypes.getExtension(item.name)
                val cat = MimeTypes.getCategory(File(item.name))
                val iconRes = when (cat) {
                    com.artraw.files.model.FileCategory.IMAGES -> R.drawable.ic_image
                    com.artraw.files.model.FileCategory.VIDEOS -> R.drawable.ic_video
                    com.artraw.files.model.FileCategory.AUDIO -> R.drawable.ic_audio
                    com.artraw.files.model.FileCategory.DOCUMENTS -> R.drawable.ic_document
                    com.artraw.files.model.FileCategory.APKS -> R.drawable.ic_apk
                    com.artraw.files.model.FileCategory.ARCHIVES -> R.drawable.ic_archive
                    else -> R.drawable.ic_file
                }
                imgIcon.setImageResource(iconRes)
                val sizeStr = Formatters.formatFileSize(item.size)
                val compStr = Formatters.formatFileSize(item.compressedSize)
                tvDetails.text = "$sizeStr (Compressed: $compStr)"
            }

            btnExtract.setOnClickListener {
                archiveFile?.let { arc ->
                    extractEntireArchive(arc)
                }
            }

            return view
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
