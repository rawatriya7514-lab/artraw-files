package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ChecksumActivity : Activity() {

    private lateinit var tvFilePath: TextView
    private lateinit var tvMd5: TextView
    private lateinit var tvSha1: TextView
    private lateinit var tvSha256: TextView
    private lateinit var btnCopyMd5: Button
    private lateinit var btnCopySha256: Button

    private var selectedFile: File? = null
    private var md5Hash = ""
    private var sha1Hash = ""
    private var sha256Hash = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checksum)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerChecksum)),
            bottomStickyViews = listOf(findViewById(R.id.bottomNavigationChecksum))
        )

        initViews()
        setupListeners()
    }

    private fun initViews() {
        tvFilePath = findViewById(R.id.tvChecksumFilePath)
        tvMd5 = findViewById(R.id.tvMd5Hash)
        tvSha1 = findViewById(R.id.tvSha1Hash)
        tvSha256 = findViewById(R.id.tvSha256Hash)
        btnCopyMd5 = findViewById(R.id.btnCopyMd5)
        btnCopySha256 = findViewById(R.id.btnCopySha256)

        findViewById<View>(R.id.btnChecksumBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnChooseFile).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                putExtra("from_checksum", true)
                putExtra("initial_path", selectedFile?.parentFile?.absolutePath ?: "/storage/emulated/0")
            })
        }

        findViewById<View>(R.id.btnNavHomeCs).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavBrowseCs).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavSettingsCs).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnCopyMd5.setOnClickListener { copyToClipboard("MD5", md5Hash) }
        btnCopySha256.setOnClickListener { copyToClipboard("SHA-256", sha256Hash) }

        findViewById<View>(R.id.rowMd5).setOnClickListener { copyToClipboard("MD5", md5Hash) }
        findViewById<View>(R.id.rowSha1).setOnClickListener { copyToClipboard("SHA-1", sha1Hash) }
        findViewById<View>(R.id.rowSha256).setOnClickListener { copyToClipboard("SHA-256", sha256Hash) }
    }

    private fun setupListeners() {
    }

    override fun onResume() {
        super.onResume()
        val path = intent.getStringExtra("checksum_file_path")
        if (path != null && path.isNotEmpty()) {
            selectedFile = File(path)
            tvFilePath.text = selectedFile?.absolutePath
            computeHashes(selectedFile!!)
        }
        // Clear the extra so it doesn't recompute
        intent.removeExtra("checksum_file_path")
    }

    private fun computeHashes(file: File) {
        tvMd5.text = "Calculating..."
        tvSha1.text = "Calculating..."
        tvSha256.text = "Calculating..."

        val thread = Thread({
            try {
                md5Hash = getFileHash(file, "MD5")
                sha1Hash = getFileHash(file, "SHA-1")
                sha256Hash = getFileHash(file, "SHA-256")

                runOnUiThread {
                    tvMd5.text = md5Hash
                    tvSha1.text = sha1Hash
                    tvSha256.text = sha256Hash
                    Toast.makeText(this, "Hash computed for ${file.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvMd5.text = "Error"
                    tvSha1.text = "Error"
                    tvSha256.text = "Error"
                    Toast.makeText(this, "Hash failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
        thread.start()
    }

    private fun getFileHash(file: File, algorithm: String): String {
        val digest: MessageDigest = MessageDigest.getInstance(algorithm)
        val fis = FileInputStream(file)
        val buffer = ByteArray(8192)
        var read: Int
        while (fis.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        fis.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyToClipboard(label: String, text: String) {
        if (text.isBlank() || text == "—" || text.startsWith("Calculating") || text == "Error") return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }
}
