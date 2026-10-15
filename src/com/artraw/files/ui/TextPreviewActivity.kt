package com.artraw.files.ui

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.model.FileItem
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.util.Formatters
import java.io.File
import java.io.FileInputStream

class TextPreviewActivity : Activity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private var targetFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_preview)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerPreview)),
            bottomScrollViews = listOf(findViewById(R.id.scrollPreviewContent))
        )

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path.isNullOrBlank()) {
            finish()
            return
        }

        val file = File(path)
        targetFile = file
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<View>(R.id.btnPreviewBack).setOnClickListener {
            finish()
        }

        val tvTitle = findViewById<TextView>(R.id.tvPreviewTitle)
        val tvMeta = findViewById<TextView>(R.id.tvPreviewMeta)
        val tvContent = findViewById<TextView>(R.id.tvTextContent)

        tvTitle.text = file.name

        findViewById<View>(R.id.btnPreviewShare).setOnClickListener {
            val item = FileItem(file = file)
            FileViewerHelper.shareFiles(this, listOf(item))
        }

        try {
            val text = FileInputStream(file).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = text.lines().size
            tvMeta.text = "UTF-8 • $lines lines • ${Formatters.formatFileSize(file.length())}"
            tvContent.text = text
        } catch (e: Exception) {
            tvContent.text = "Unable to read file: ${e.message}"
        }
    }
}
