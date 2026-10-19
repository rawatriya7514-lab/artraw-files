package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
    private val fullResCache = object : android.util.LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = 1
    }
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector

    private var currentScale = 1.0f
    private val minScale = 1.0f
    private val maxScale = 5.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

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

        findViewById<ImageView>(R.id.btnEditImage).setOnClickListener {
            currentFile?.let { f ->
                val intent = Intent(this, ImageEditorActivity::class.java)
                intent.putExtra("image_path", f.absolutePath)
                startActivity(intent)
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
                            if (currentIndex in imageList.indices) {
                                imageList.removeAt(currentIndex)
                            }
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

        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                val targetScale = (currentScale * factor).coerceIn(minScale, maxScale)
                val delta = targetScale / currentScale
                currentScale = targetScale

                imgFull.scaleX = currentScale
                imgFull.scaleY = currentScale

                val focusX = detector.focusX - imgFull.width / 2f
                val focusY = detector.focusY - imgFull.height / 2f
                imgFull.translationX += (1 - delta) * (focusX - imgFull.translationX)
                imgFull.translationY += (1 - delta) * (focusY - imgFull.translationY)
                clampTranslation()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleBars()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentScale > 1.2f) {
                    resetZoom(animate = true)
                } else {
                    currentScale = 2.5f
                    val focusX = e.x - imgFull.width / 2f
                    val focusY = e.y - imgFull.height / 2f
                    val targetTransX = -focusX * (currentScale - 1f)
                    val targetTransY = -focusY * (currentScale - 1f)
                    val maxTransX = ((imgFull.width * currentScale - imgFull.width) / 2f).coerceAtLeast(0f)
                    val maxTransY = ((imgFull.height * currentScale - imgFull.height) / 2f).coerceAtLeast(0f)

                    imgFull.animate()
                        .scaleX(currentScale)
                        .scaleY(currentScale)
                        .translationX(targetTransX.coerceIn(-maxTransX, maxTransX))
                        .translationY(targetTransY.coerceIn(-maxTransY, maxTransY))
                        .setDuration(250)
                        .start()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (currentScale > 1.05f) return false
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
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress && currentScale > 1.05f) {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        imgFull.translationX += dx
                        imgFull.translationY += dy
                        clampTranslation()
                    }
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (currentScale <= 1.02f) {
                        resetZoom(animate = true)
                    }
                }
            }
            true
        }
    }

    private fun clampTranslation() {
        val maxTransX = ((imgFull.width * currentScale - imgFull.width) / 2f).coerceAtLeast(0f)
        val maxTransY = ((imgFull.height * currentScale - imgFull.height) / 2f).coerceAtLeast(0f)
        imgFull.translationX = imgFull.translationX.coerceIn(-maxTransX, maxTransX)
        imgFull.translationY = imgFull.translationY.coerceIn(-maxTransY, maxTransY)
    }

    private fun resetZoom(animate: Boolean = true) {
        currentScale = 1.0f
        if (animate) {
            imgFull.animate()
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(200)
                .start()
        } else {
            imgFull.scaleX = 1.0f
            imgFull.scaleY = 1.0f
            imgFull.translationX = 0f
            imgFull.translationY = 0f
        }
    }

    private fun buildImageList(file: File) {
        val parent = file.parentFile
        val list = if (parent != null && parent.exists() && parent.isDirectory) {
            parent.listFiles { f ->
                !f.isDirectory && MimeTypes.getCategory(f) == com.artraw.files.model.FileCategory.IMAGES
            }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
        } else {
            emptyList()
        }

        imageList.clear()
        imageList.addAll(list)
        if (imageList.none { it.absolutePath == file.absolutePath }) {
            imageList.add(file)
        }
        currentIndex = imageList.indexOfFirst { it.absolutePath == file.absolutePath }.coerceAtLeast(0)
    }

    private fun decodeHighResBitmap(file: File): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var w = bounds.outWidth
            var h = bounds.outHeight

            if (w <= 0 || h <= 0) {
                try {
                    java.io.FileInputStream(file).use { fis ->
                        BitmapFactory.decodeStream(fis, null, bounds)
                        w = bounds.outWidth
                        h = bounds.outHeight
                    }
                } catch (_: Throwable) {}
            }

            var sample = 1
            while (w / sample > 2048 || h / sample > 2048) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp: Bitmap? = try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) {
                try {
                    java.io.FileInputStream(file).use { fis ->
                        bmp = BitmapFactory.decodeStream(fis, null, opts)
                    }
                } catch (_: Throwable) {}
            }
            return bmp
        } catch (_: Throwable) {
            return null
        }
    }

    private fun preloadNeighbors(idx: Int) {
        if (imageList.size <= 1) return
        executor.execute {
            val nextIdx = (idx + 1) % imageList.size
            val prevIdx = if (idx - 1 < 0) imageList.size - 1 else idx - 1
            listOf(nextIdx, prevIdx).forEach { i ->
                if (i in imageList.indices && i != idx) {
                    val f = imageList[i]
                    if (fullResCache.get(f.absolutePath) == null) {
                        decodeHighResBitmap(f)?.let { bmp ->
                            fullResCache.put(f.absolutePath, bmp)
                        }
                    }
                }
            }
        }
    }

    private fun loadImage(file: File) {
        currentFile = file
        tvTitle.text = file.name
        val total = imageList.size.coerceAtLeast(1)
        val pos = (currentIndex + 1).coerceIn(1, total)
        tvIndex.text = "$pos of $total"

        // Step 1: Check full-res memory cache (instant display, 0ms)
        val cachedFull = fullResCache.get(file.absolutePath)
        if (cachedFull != null && !cachedFull.isRecycled) {
            resetZoom(animate = false)
            imgFull.setImageBitmap(cachedFull)
            tvInfo.text = "${cachedFull.width}x${cachedFull.height} • ${Formatters.formatFileSize(file.length())}"
            preloadNeighbors(currentIndex)
            return
        }

        // Step 2: Instant 0ms preview from ThumbnailLoader memory cache
        val cachedThumb = com.artraw.files.util.ThumbnailLoader.getCachedBitmap(file.absolutePath)
        if (cachedThumb != null && !cachedThumb.isRecycled) {
            resetZoom(animate = false)
            imgFull.setImageBitmap(cachedThumb)
            tvInfo.text = Formatters.formatFileSize(file.length())
        }

        // Step 3: Decode full resolution bitmap asynchronously in background
        executor.execute {
            try {
                val bmp = decodeHighResBitmap(file)
                if (bmp != null) {
                    fullResCache.put(file.absolutePath, bmp)
                }

                val sizeStr = Formatters.formatFileSize(file.length())
                val infoStr = if (bmp != null) "${bmp.width}x${bmp.height} • $sizeStr" else sizeStr

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    // Only update if current file hasn't changed while decoding
                    if (currentFile?.absolutePath == file.absolutePath) {
                        resetZoom(animate = false)
                        if (bmp != null) {
                            imgFull.setImageBitmap(bmp)
                        } else if (cachedThumb == null) {
                            imgFull.setImageResource(R.drawable.ic_image)
                        }
                        tvInfo.text = infoStr
                    }
                }

                preloadNeighbors(currentIndex)
            } catch (_: Throwable) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (currentFile?.absolutePath == file.absolutePath && cachedThumb == null) {
                        imgFull.setImageResource(R.drawable.ic_image)
                        tvInfo.text = Formatters.formatFileSize(file.length())
                    }
                }
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
