package com.artraw.files.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.repository.FileRepository
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.PermissionsHelper
import java.io.File

/**
 * Finds empty directories (bottom-up, so nested empty chains collapse) and
 * removes them after explicit confirmation. System/media protected trees and
 * the vault are never touched.
 */
class EmptyFolderCleanerActivity : BaseActivity() {

    private lateinit var repository: FileRepository
    private lateinit var tvSummary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var list: LinearLayout
    private lateinit var btnClean: Button
    private lateinit var btnRescan: Button

    private var emptyFolders: List<File> = emptyList()
    private var scanning = false
    private var scannedOnce = false
    private var root: File? = null

    private val excludedSegments = listOf(
        "/Android/data",
        "/Android/obb",
        "/Android/media",
        "/.artraw_vault",
        "/.thumbnails"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileRepository(this)
        setContentView(R.layout.activity_empty_folder_cleaner)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerEmptyFolders)),
            bottomScrollViews = listOf(findViewById(R.id.scrollEmptyList))
        )

        tvSummary = findViewById(R.id.tvEmptySummary)
        progress = findViewById(R.id.progressEmptyScan)
        list = findViewById(R.id.layoutEmptyFolders)
        btnClean = findViewById(R.id.btnCleanEmpty)
        btnRescan = findViewById(R.id.btnRescanEmpty)

        findViewById<View>(R.id.btnEmptyFolderBack).setOnClickListener { finish() }
        btnRescan.setOnClickListener { startScan() }
        btnClean.setOnClickListener { confirmClean() }
        MotionUtils.pressScale(btnRescan, 0.97f)
        MotionUtils.pressScale(btnClean, 0.97f)
    }

    override fun onResume() {
        super.onResume()
        if (!scannedOnce && !scanning && PermissionsHelper.hasStoragePermission(this)) {
            startScan()
        }
    }

    private fun startScan() {
        if (scanning) return
        if (!PermissionsHelper.hasStoragePermission(this)) {
            PermissionsHelper.requestStoragePermission(this)
            return
        }
        scanning = true
        emptyFolders = emptyList()
        list.removeAllViews()
        btnClean.isEnabled = false
        progress.visibility = View.VISIBLE
        tvSummary.text = "Scanning shared storage…"

        val startRoot = repository.getPrimaryStorageRoot()
        root = startRoot
        Thread {
            val found = scanEmpty(startRoot)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                scanning = false
                scannedOnce = true
                progress.visibility = View.GONE
                emptyFolders = found
                render(found)
            }
        }.start()
    }

    private fun scanEmpty(start: File): List<File> {
        val found = ArrayList<File>()
        var visited = 0
        val maxVisits = 45_000
        val maxResults = 2_000

        fun isExcluded(file: File): Boolean {
            val path = file.absolutePath
            return excludedSegments.any { path.contains(it, ignoreCase = true) }
        }

        fun visit(dir: File, depth: Int): Boolean {
            if (depth > 32 || visited++ > maxVisits || found.size >= maxResults) return false
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                null
            } ?: return false

            var hasContent = false
            for (child in children) {
                if (isExcluded(child)) {
                    hasContent = true
                    continue
                }
                if (child.isDirectory) {
                    if (visit(child, depth + 1)) {
                        if (found.size < maxResults) found.add(child)
                    } else {
                        hasContent = true
                    }
                } else {
                    hasContent = true
                }
            }
            return !hasContent
        }

        try {
            visit(start, 0)
        } catch (_: Throwable) {
        }
        return found
    }

    private fun render(found: List<File>) {
        list.removeAllViews()
        if (found.isEmpty()) {
            tvSummary.text = "No empty folders found"
            btnClean.isEnabled = false
            val empty = TextView(this).apply {
                text = "Your folders are tidy. Nothing to clean."
                setTextColor(getColor(R.color.aurora_text_secondary))
                textSize = 13f
                setPadding(dp(10), dp(20), dp(10), dp(20))
                gravity = android.view.Gravity.CENTER
            }
            list.addView(empty)
            return
        }

        tvSummary.text = "${found.size} empty folder${if (found.size == 1) "" else "s"} found"
        btnClean.isEnabled = true
        btnClean.text = "Remove ${found.size}"

        val rootPath = root?.absolutePath.orEmpty()
        found.take(400).forEachIndexed { index, dir ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(8), dp(9))
            }
            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(getColor(R.color.cyber_amber))
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            }
            val label = TextView(this).apply {
                text = dir.absolutePath.removePrefix(rootPath).trimStart('/').ifBlank { dir.name }
                setTextColor(getColor(R.color.aurora_text_primary))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                setPadding(dp(12), 0, 0, 0)
            }
            row.addView(icon)
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            MotionUtils.fadeUp(row, (index.coerceAtMost(12) * 18).toLong(), 5f)
            list.addView(row)

            if (index != minOf(found.size, 400) - 1) {
                list.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.aurora_divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { marginStart = dp(40) }
                })
            }
        }
        if (found.size > 400) {
            list.addView(TextView(this).apply {
                text = "… and ${found.size - 400} more"
                setTextColor(getColor(R.color.aurora_text_tertiary))
                textSize = 12f
                setPadding(dp(10), dp(10), dp(10), dp(10))
            })
        }
    }

    private fun confirmClean() {
        if (emptyFolders.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Remove ${emptyFolders.size} empty folders?")
            .setMessage("Only folders that are still empty will be deleted. This cannot be undone.")
            .setPositiveButton("Remove") { _, _ -> cleanNow() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun cleanNow() {
        val toDelete = emptyFolders
        progress.visibility = View.VISIBLE
        btnClean.isEnabled = false
        tvSummary.text = "Cleaning…"
        Thread {
            var removed = 0
            var failed = 0
            for (dir in toDelete) {
                try {
                    val children = dir.listFiles()
                    if (children != null && children.isEmpty() && dir.delete()) {
                        removed++
                    }
                } catch (_: Throwable) {
                    failed++
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Removed $removed folder(s)${if (failed > 0) ", $failed skipped" else ""}",
                    Toast.LENGTH_LONG
                ).show()
                startScan()
            }
        }.start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
