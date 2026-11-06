package com.artraw.files.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.MotionUtils
import java.io.FileOutputStream
import java.security.SecureRandom

/**
 * Secure Shredder for document-provider URIs.
 *
 * Overwrites the raw bytes through the provider's file descriptor, forces a
 * sync, then deletes the document. Falls back to deletion when a provider is
 * read-only and reports exactly how many files could not be overwritten.
 */
class ShredderActivity : BaseActivity() {

    private data class Entry(val uri: Uri, val name: String, val size: Long)

    private val entries = mutableListOf<Entry>()

    private lateinit var list: LinearLayout
    private lateinit var btnAdd: Button
    private lateinit var btnShred: Button
    private lateinit var emptyHint: TextView
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shredder)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerShredder)),
            bottomScrollViews = listOf(findViewById(R.id.scrollShredFiles))
        )

        list = findViewById(R.id.layoutShredFiles)
        btnAdd = findViewById(R.id.btnAddShredFiles)
        btnShred = findViewById(R.id.btnShredNow)

        emptyHint = TextView(this).apply {
            text = "No files selected\nChoose files from any folder or cloud provider."
            setTextColor(getColor(R.color.aurora_text_secondary))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(12), dp(28), dp(12), dp(28))
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        findViewById<View>(R.id.btnShredderBack).setOnClickListener { finish() }
        btnAdd.setOnClickListener { pickFiles() }
        btnShred.setOnClickListener { confirmShred() }
        MotionUtils.pressScale(findViewById(R.id.cardShredderWarning), 0.99f)
        MotionUtils.pressScale(btnAdd, 0.96f)
        MotionUtils.pressScale(btnShred, 0.97f)

        render()
    }

    private fun pickFiles() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_PICK)
        } catch (e: Exception) {
            Toast.makeText(this, "No file picker available", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }
        data.data?.let { uris.add(it) }

        for (uri in uris.distinct()) {
            if (entries.size >= 50) {
                Toast.makeText(this, "Up to 50 files at a time", Toast.LENGTH_SHORT).show()
                break
            }
            if (entries.any { it.uri == uri }) continue
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            entries.add(queryEntry(uri))
        }
        render()
    }

    private fun queryEntry(uri: Uri): Entry {
        var name = uri.lastPathSegment ?: "File"
        var size = -1L
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Throwable) {
        }
        return Entry(uri, name, size)
    }

    private fun render() {
        list.removeAllViews()
        btnShred.isEnabled = entries.isNotEmpty() && !busy
        btnAdd.isEnabled = !busy
        btnShred.text = when {
            busy -> "Shredding…"
            entries.isEmpty() -> "Select files to shred"
            else -> "Shred ${entries.size} file${if (entries.size == 1) "" else "s"} permanently"
        }

        if (entries.isEmpty()) {
            list.addView(emptyHint)
            return
        }

        entries.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(9), dp(4), dp(9))
            }
            val icon = ImageView(this).apply {
                setImageResource(R.drawable.ic_file)
                setColorFilter(getColor(R.color.aurora_text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
            }
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
            }
            val nameView = TextView(this).apply {
                text = entry.name
                setTextColor(getColor(R.color.aurora_text_primary))
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            }
            val metaView = TextView(this).apply {
                text = if (entry.size >= 0) Formatters.formatFileSize(entry.size) else "Size unknown"
                setTextColor(getColor(R.color.aurora_text_tertiary))
                textSize = 11f
            }
            column.addView(nameView)
            column.addView(metaView)
            val remove = ImageView(this).apply {
                setImageResource(R.drawable.ic_delete)
                setColorFilter(getColor(R.color.aurora_error))
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    if (!busy) {
                        entries.removeAt(index)
                        render()
                    }
                }
            }
            row.addView(icon)
            row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(remove, LinearLayout.LayoutParams(dp(40), dp(40)))
            MotionUtils.fadeUp(row, (index.coerceAtMost(10) * 20).toLong(), 5f)
            list.addView(row)

            if (index != entries.lastIndex) {
                list.addView(View(this).apply {
                    setBackgroundColor(getColor(R.color.aurora_divider))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
                    ).apply { marginStart = dp(42) }
                })
            }
        }
    }

    private fun confirmShred() {
        if (entries.isEmpty() || busy) return
        AlertDialog.Builder(this)
            .setTitle("Shred ${entries.size} file(s)?")
            .setMessage("Contents will be overwritten and the files deleted. This action cannot be undone.")
            .setPositiveButton("Shred permanently") { _, _ -> shredNow() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun shredNow() {
        if (busy) return
        busy = true
        val snapshot = entries.toList()
        val overwritten = intArrayOf(0)
        val deletedOnly = intArrayOf(0)
        val failed = intArrayOf(0)
        render()

        Thread {
            snapshot.forEachIndexed { index, entry ->
                runOnUiThread {
                    if (!isFinishing) btnShred.text = "Shredding ${index + 1} of ${snapshot.size}…"
                }
                val result = shred(entry.uri)
                when (result) {
                    Result.OVERWRITTEN -> overwritten[0]++
                    Result.DELETED_ONLY -> deletedOnly[0]++
                    Result.FAILED -> failed[0]++
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                entries.clear()
                render()
                val message = buildString {
                    append("Shredded ${overwritten[0]}")
                    if (deletedOnly[0] > 0) append(", deleted ${deletedOnly[0]} without overwrite")
                    if (failed[0] > 0) append(", ${failed[0]} failed")
                }
                AlertDialog.Builder(this)
                    .setTitle("Shredding complete")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private enum class Result { OVERWRITTEN, DELETED_ONLY, FAILED }

    private fun shred(uri: Uri): Result {
        var overwritten = false
        var descriptor: android.os.ParcelFileDescriptor? = null
        try {
            descriptor = contentResolver.openFileDescriptor(uri, "rw")
            if (descriptor != null) {
                val size = descriptor.statSize
                if (size > 0) {
                    FileOutputStream(descriptor.fileDescriptor).use { out ->
                        val random = SecureRandom()
                        val buffer = ByteArray(64 * 1024)
                        var remaining = size
                        while (remaining > 0) {
                            random.nextBytes(buffer)
                            val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                            out.write(buffer, 0, chunk)
                            remaining -= chunk
                        }
                        out.flush()
                        descriptor.fileDescriptor.sync()
                    }
                    overwritten = true
                }
            }
        } catch (_: Throwable) {
            overwritten = false
        } finally {
            try {
                descriptor?.close()
            } catch (_: Throwable) {
            }
        }

        val deleted = try {
            contentResolver.delete(uri, null, null) > 0
        } catch (_: Throwable) {
            false
        }

        return when {
            deleted && overwritten -> Result.OVERWRITTEN
            deleted -> Result.DELETED_ONLY
            else -> Result.FAILED
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PICK = 4101
    }
}
