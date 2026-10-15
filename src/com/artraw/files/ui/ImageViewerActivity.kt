package com.artraw.files.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.MimeTypes
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class ImageViewerActivity : Activity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"
    }

    private lateinit var imgFull: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvIndex: TextView
    private lateinit var headerBar: RelativeLayout
    private lateinit var bottomBar: LinearLayout

    private var currentFile: File? = null
    private val imageList = mutableListOf<File>()
    private var currentIndex = 0
    private var barsVisible = true
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        if (path == null) {
            Toast.makeText(this, "Image not found.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentFile = file
        buildImageList(file)

        initViews()
        setupListeners()
        loadImage(file)
    }

    private fun initViews() {
        imgFull = findViewById(R.id.imgFullPhoto)
        tvTitle = findViewById(R.id.tvImageTitle)
        tvInfo = findViewById(R.id.tvImageInfo)
        tvIndex = findViewById(R.id.tvImageIndex)
        headerBar = findViewById(R.id.headerImage)
        bottomBar = findViewById(R.id.bottomImageBar)
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBackImage).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.btnShareImage).setOnClickListener {
            currentFile?.let { f ->
                val item = com.artraw.files.model.FileItem(
                    file = f,
                    name = f.name,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    extension = MimeTypes.getExtension(f.name),
                    mimeType = MimeTypes.getMimeType(f),
                    category = com.artraw.files.model.FileCategory.IMAGES
                )
                FileViewerHelper.shareFiles(this, listOf(item))
            }
        }

        findViewById<ImageView>(R.id.btnDeleteImage).setOnClickListener {
            currentFile?.let { f ->
                android.app.AlertDialog.Builder(this)
                    .setTitle("Delete Image")
                    .setMessage("Permanently delete ${f.name}?")
                    .setPositiveButton("Delete") { _, _ ->
                        if (f.delete()) {
                            Toast.makeText(this, "Deleted ${f.name}", Toast.LENGTH_SHORT).show()
                            imageList.removeAt(currentIndex)
                            if (imageList.isEmpty()) {
                                finish()
                            } else {
                                if (currentIndex >= imageList.size) currentIndex = imageList.size - 1
                                loadImage(imageList[currentIndex])
                            }
                        }
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleBars()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (abs(diffX) > abs(diffY) && abs(diffX) > 100 && abs(velocityX) > 100) {
                    if (diffX > 0) {
                        showPreviousImage()
                    } else {
                        showNextImage()
                    }
                    return true
                }
                return false
            }
        })

        imgFull.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun buildImageList(file: File) {
        val parent = file.parentFile ?: return
        val list = parent.listFiles { f ->
            !f.isDirectory && MimeTypes.getCategory(f) == com.artraw.files.model.FileCategory.IMAGES
        }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()

        imageList.clear()
        imageList.addAll(list)
        currentIndex = imageList.indexOfFirst { it.absolutePath == file.absolutePath }
        if (currentIndex == -1 && imageList.isNotEmpty()) currentIndex = 0
    }

    private fun loadImage(file: File) {
        currentFile = file
        tvTitle.text = file.name
        val pos = currentIndex + 1
        tvIndex.text = "$pos of ${imageList.size}"

        executor.execute {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight

            var sample = 1
            while (w / sample > 2048 || h / sample > 2048) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)

            val sizeStr = Formatters.formatFileSize(file.length())
            val infoStr = if (w > 0 && h > 0) "${w}x${h} • $sizeStr" else sizeStr

            runOnUiThread {
                if (bmp != null) {
                    imgFull.setImageBitmap(bmp)
                } else {
                    imgFull.setImageResource(R.drawable.ic_image)
                }
                tvInfo.text = infoStr
            }
        }
    }

    private fun showNextImage() {
        if (imageList.size <= 1) return
        currentIndex = (currentIndex + 1) % imageList.size
        loadImage(imageList[currentIndex])
    }

    private fun showPreviousImage() {
        if (imageList.size <= 1) return
        currentIndex = if (currentIndex - 1 < 0) imageList.size - 1 else currentIndex - 1
        loadImage(imageList[currentIndex])
    }

    private fun toggleBars() {
        barsVisible = !barsVisible
        val vis = if (barsVisible) View.VISIBLE else View.GONE
        headerBar.visibility = vis
        bottomBar.visibility = vis
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
