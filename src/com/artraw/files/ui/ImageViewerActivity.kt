package com.artraw.files.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.ArtfilesApp
import com.artraw.files.R
import com.artraw.files.gallery.ai.PhotoMetadataHelper
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.preview.ArtrawFileProvider
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.util.ClipboardHelper
import com.artraw.files.util.FavoritesManager
import com.artraw.files.util.Formatters
import com.artraw.files.util.MimeTypes
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.ThumbnailLoader
import com.artraw.files.util.VaultManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Ultra-Modern Immersive Full-Screen Media Viewer.
 *
 * Features:
 *   - Ambient Dynamic Background Glow tinted with dominant photo colors
 *   - Swipe-Up Glassmorphic Info Sheet with EXIF badges & interactive Palette Swatches
 *   - 1-Tap Path Copying Superpower (Direct Button & Monospace Path card)
 *   - Floating Frosted-Glass Pill Bottom Dock (Edit, Heart Favorite, Copy Path, Vault, Share, Details, Delete)
 *   - Fast 0ms Multi-Tier Caching & Neighbor Preloading
 */
class ImageViewerActivity : BaseActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "extra_file_path"

        private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        private val cacheSize = (maxMemory / 5).coerceIn(16384, 65536)

        // Process-wide permanent in-memory cache for viewed images: instant 0ms recall on reopen
        val fullResCache = object : android.util.LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return (bitmap.byteCount / 1024).coerceAtLeast(1)
            }
        }

        @Volatile
        private var displayDiskCacheDir: File? = null

        fun initDiskCache(context: Context) {
            getDisplayDiskCacheDir(context)
        }

        fun getDisplayDiskCacheDir(context: Context? = null): File {
            val dir = displayDiskCacheDir ?: synchronized(this) {
                displayDiskCacheDir ?: run {
                    val ctx = context?.applicationContext ?: try { ArtfilesApp.instance } catch (_: Throwable) { null }
                    if (ctx != null) {
                        File(ctx.cacheDir, "display_cache").also {
                            if (!it.exists()) it.mkdirs()
                            displayDiskCacheDir = it
                        }
                    } else {
                        File("/data/data/com.artraw.files/cache/display_cache").also {
                            if (!it.exists()) it.mkdirs()
                            displayDiskCacheDir = it
                        }
                    }
                }
            }
            return dir
        }
    }

    private lateinit var imgFull: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvInfo: TextView
    private lateinit var tvIndex: TextView
    private lateinit var headerBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var viewAmbientGlow: View
    private lateinit var ivHeartImage: ImageView
    private lateinit var layoutSheetContainer: FrameLayout

    private var currentFile: File? = null
    private var currentBitmap: Bitmap? = null
    private val imageList = mutableListOf<File>()
    private var currentIndex = 0
    private var barsVisible = true
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleDetector: ScaleGestureDetector

    private val REQ_DELETE_MEDIA = 2001

    private var currentScale = 1.0f
    private val minScale = 1.0f
    private val maxScale = 5.0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        initDiskCache(this)
        VaultManager.init(this)

        try {
            com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
                activity = this,
                topSpacers = listOfNotNull(findViewById(R.id.statusBarSpacerImage)),
                bottomStickyViews = listOfNotNull(findViewById(R.id.navBarSpacerImage))
            )
        } catch (_: Throwable) {}

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
        viewAmbientGlow = findViewById(R.id.viewAmbientGlow)
        ivHeartImage = findViewById(R.id.ivHeartImage)
        layoutSheetContainer = findViewById(R.id.layoutSheetContainer)
    }

    private fun createFileItem(f: File): com.artraw.files.model.FileItem {
        return com.artraw.files.model.FileItem(
            file = f,
            name = f.name,
            size = f.length(),
            lastModified = f.lastModified(),
            isDirectory = false,
            extension = MimeTypes.getExtension(f.name),
            mimeType = MimeTypes.getMimeType(f),
            category = com.artraw.files.model.FileCategory.IMAGES
        )
    }

    private fun getMediaStoreUri(file: File): android.net.Uri? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns._ID)
        val selection = "${android.provider.MediaStore.MediaColumns.DATA} = ?"
        val selectionArgs = arrayOf(file.absolutePath)
        val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        try {
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID))
                    return android.content.ContentUris.withAppendedId(uri, id)
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun executeDeleteImage(f: File) {
        val path = f.absolutePath
        fullResCache.remove(path)

        try {
            val cacheDir = getDisplayDiskCacheDir(this)
            val cacheFile = File(cacheDir, "${getCacheKey(f)}.webp")
            if (cacheFile.exists()) cacheFile.delete()
        } catch (_: Throwable) {}

        com.artraw.files.repository.CategoryDataRepository.removeItem(path)

        var deleted = false
        try {
            deleted = f.delete()
        } catch (_: Throwable) {}

        if (!deleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uri = getMediaStoreUri(f)
            if (uri != null) {
                try {
                    val pendingIntent = android.provider.MediaStore.createDeleteRequest(
                        contentResolver,
                        listOf(uri)
                    )
                    startIntentSenderForResult(
                        pendingIntent.intentSender,
                        REQ_DELETE_MEDIA,
                        null, 0, 0, 0
                    )
                    return
                } catch (_: Throwable) {}
            }
        }

        if (deleted) {
            android.media.MediaScannerConnection.scanFile(this, arrayOf(path), null, null)
            Toast.makeText(this, "Deleted ${f.name}", Toast.LENGTH_SHORT).show()
            onImageDeleted()
        } else {
            Toast.makeText(this, "Unable to delete image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onImageDeleted() {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_DELETE_MEDIA && resultCode == RESULT_OK) {
            currentFile?.let { f ->
                fullResCache.remove(f.absolutePath)
                com.artraw.files.repository.CategoryDataRepository.removeItem(f.absolutePath)
                Toast.makeText(this, "Deleted ${f.name}", Toast.LENGTH_SHORT).show()
                onImageDeleted()
            }
        }
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBackImage).setOnClickListener {
            finish()
        }

        // Action: 1-TAP COPY PATH (User Core Feature)
        findViewById<View>(R.id.btnCopyPathImage).setOnClickListener {
            currentFile?.let { f ->
                ClipboardHelper.copyPath(this, f, it)
            }
        }

        // Action: FAVORITE
        findViewById<View>(R.id.btnFavoriteImage).setOnClickListener {
            currentFile?.let { f ->
                val db = AiGalleryDatabase.getInstance(this)
                val isFav = db.toggleFavorite(f.absolutePath)
                FavoritesManager.toggleFavorite(createFileItem(f))
                updateFavoriteUi(isFav, animate = true)
                Toast.makeText(this, if (isFav) "Added to Favorites ❤️" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
            }
        }

        // Action: MOVE TO VAULT
        findViewById<View>(R.id.btnVaultImage).setOnClickListener {
            currentFile?.let { f ->
                AlertDialog.Builder(this)
                    .setTitle("Move to Vault 🔒")
                    .setMessage("Encrypt and move \"${f.name}\" to your private Vault?")
                    .setPositiveButton("Move to Vault") { _, _ ->
                        val success = VaultManager.moveToVault(f)
                        if (success) {
                            fullResCache.remove(f.absolutePath)
                            Toast.makeText(this, "Moved to Vault 🔒", Toast.LENGTH_SHORT).show()
                            onImageDeleted()
                        } else {
                            Toast.makeText(this, "Could not move to vault", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Action: SHARE
        findViewById<View>(R.id.btnShareImage).setOnClickListener {
            currentFile?.let { f ->
                val item = createFileItem(f)
                FileViewerHelper.shareFiles(this, listOf(item))
            }
        }

        // Action: EDIT
        findViewById<View>(R.id.btnEditImage).setOnClickListener {
            currentFile?.let { f ->
                val uri = ArtrawFileProvider.getUriForFile(f)
                val editIntent = Intent(Intent.ACTION_EDIT).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                try {
                    startActivity(editIntent)
                } catch (_: Exception) {
                    val fallback = Intent(this, ImageEditorActivity::class.java).apply {
                        putExtra("image_path", f.absolutePath)
                    }
                    startActivity(fallback)
                }
            }
        }

        // Action: DELETE
        findViewById<View>(R.id.btnDeleteImage).setOnClickListener {
            currentFile?.let { f ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage("Permanently delete ${f.name}?")
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        executeDeleteImage(f)
                    }
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
            }
        }

        // Action: SWIPE-UP / INFO DETAILS
        val showSheetAction = View.OnClickListener {
            showInfoSheet()
        }
        findViewById<View>(R.id.btnInfoImage).setOnClickListener(showSheetAction)
        tvInfo.setOnClickListener(showSheetAction)

        layoutSheetContainer.setOnClickListener {
            hideInfoSheet()
        }
        findViewById<View>(R.id.cardInfoSheetRoot)?.setOnClickListener {
            // Do not close when tapping card itself
        }
        findViewById<View>(R.id.btnSheetClose)?.setOnClickListener {
            hideInfoSheet()
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
                if (layoutSheetContainer.visibility == View.VISIBLE) {
                    hideInfoSheet()
                } else {
                    toggleBars()
                }
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

                // Swipe-Up gesture reveals Glassmorphic Info Sheet!
                if (diffY < -120 && abs(diffY) > abs(diffX)) {
                    showInfoSheet()
                    return true
                }

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

        MotionUtils.pressScale(findViewById(R.id.btnCopyPathImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnFavoriteImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnVaultImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnShareImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnEditImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnInfoImage), 0.90f)
        MotionUtils.pressScale(findViewById(R.id.btnDeleteImage), 0.90f)
    }

    private fun updateFavoriteUi(isFav: Boolean, animate: Boolean = false) {
        if (isFav) {
            ivHeartImage.setImageResource(R.drawable.ic_heart_filled)
            ivHeartImage.imageTintList = android.content.res.ColorStateList.valueOf(
                getColor(R.color.gallery_accent_coral)
            )
        } else {
            ivHeartImage.setImageResource(R.drawable.ic_heart_pulse)
            ivHeartImage.imageTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#E6FFFFFF")
            )
        }
        if (animate) {
            MotionUtils.pop(ivHeartImage)
        }
    }

    private fun showInfoSheet() {
        val file = currentFile ?: return
        layoutSheetContainer.visibility = View.VISIBLE
        layoutSheetContainer.alpha = 0f
        layoutSheetContainer.animate().alpha(1f).setDuration(200).start()

        findViewById<TextView>(R.id.tvSheetTitle)?.text = file.name
        findViewById<TextView>(R.id.tvSheetPath)?.text = file.absolutePath

        findViewById<Button>(R.id.btnSheetCopyPath)?.setOnClickListener {
            ClipboardHelper.copyPath(this, file, it)
        }
        findViewById<Button>(R.id.btnSheetCopyFolder)?.setOnClickListener {
            ClipboardHelper.copyDirectoryPath(this, file, it)
        }

        val bmp = currentBitmap
        val resText = if (bmp != null) "${bmp.width} × ${bmp.height}" else "Detecting..."
        findViewById<TextView>(R.id.tvSheetResolution)?.text = resText
        findViewById<TextView>(R.id.tvSheetFileSize)?.text = Formatters.formatFileSize(file.length())

        val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
        findViewById<TextView>(R.id.tvSheetDate)?.text = sdf.format(Date(file.lastModified()))

        // EXIF Metadata Badges
        val exif = PhotoMetadataHelper.extractExif(file)
        findViewById<TextView>(R.id.badgeExifDevice)?.apply {
            text = "📷 ${exif.device ?: "Device"}"
            visibility = if (exif.device != null) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.badgeExifAperture)?.apply {
            text = "⭕ ${exif.aperture ?: "f/1.8"}"
            visibility = if (exif.aperture != null) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.badgeExifShutter)?.apply {
            text = "⚡ ${exif.shutterSpeed ?: "1/100s"}"
            visibility = if (exif.shutterSpeed != null) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.badgeExifIso)?.apply {
            text = "🎚️ ${exif.iso ?: "ISO"}"
            visibility = if (exif.iso != null) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.badgeExifFocal)?.apply {
            text = "📏 ${exif.focalLength ?: "Focal"}"
            visibility = if (exif.focalLength != null) View.VISIBLE else View.GONE
        }

        // Extracted Color Palette Swatches (Tap to copy hex)
        val palette = PhotoMetadataHelper.extractPalette(bmp)
        val layoutSwatches = findViewById<LinearLayout>(R.id.layoutSheetPaletteSwatches)
        if (layoutSwatches != null) {
            layoutSwatches.removeAllViews()
            val inflater = LayoutInflater.from(this)
            for (swatch in palette.swatches) {
                val swatchView = inflater.inflate(R.layout.item_palette_swatch, layoutSwatches, false)
                val colorView = swatchView.findViewById<View>(R.id.viewSwatchColor)
                val tvHex = swatchView.findViewById<TextView>(R.id.tvSwatchHex)

                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(swatch.colorInt)
                    setStroke(2, Color.parseColor("#4DFFFFFF"))
                }
                colorView.background = gd
                tvHex.text = swatch.hex

                swatchView.setOnClickListener {
                    ClipboardHelper.copyText(
                        context = this,
                        label = "Color Hex",
                        text = swatch.hex,
                        toastMessage = "🎨 Copied ${swatch.hex} to clipboard!",
                        anchorView = it
                    )
                }
                MotionUtils.pressScale(swatchView, 0.90f)
                layoutSwatches.addView(swatchView)
            }
        }
    }

    private fun hideInfoSheet() {
        if (layoutSheetContainer.visibility == View.VISIBLE) {
            layoutSheetContainer.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    layoutSheetContainer.visibility = View.GONE
                }
                .start()
        }
    }

    private fun updateAmbientGlow(bitmap: Bitmap?) {
        val palette = PhotoMetadataHelper.extractPalette(bitmap)
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = 450f * resources.displayMetrics.density
            colors = intArrayOf(
                palette.ambientGlowColor,
                Color.argb(18, Color.red(palette.dominantColor), Color.green(palette.dominantColor), Color.blue(palette.dominantColor)),
                Color.TRANSPARENT
            )
        }
        viewAmbientGlow.background = gd
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

    private fun getCacheKey(file: File): String {
        val raw = "${file.absolutePath}_${file.lastModified()}_${file.length()}"
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            val sb = java.lang.StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            sb.toString()
        } catch (_: Throwable) {
            raw.hashCode().toString()
        }
    }

    private fun loadFromDisplayDiskCache(file: File): Bitmap? {
        if (!file.exists()) return null
        return try {
            val cacheDir = getDisplayDiskCacheDir(this)
            val cacheFile = File(cacheDir, "${getCacheKey(file)}.webp")
            if (!cacheFile.exists() || cacheFile.length() == 0L) return null

            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(cacheFile.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveToDisplayDiskCache(file: File, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        try {
            val cacheDir = getDisplayDiskCacheDir(this)
            val cacheFile = File(cacheDir, "${getCacheKey(file)}.webp")
            val tempFile = File(cacheDir, "${cacheFile.name}.tmp")
            FileOutputStream(tempFile).use { fos ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 88, fos)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 88, fos)
                }
                fos.flush()
            }
            tempFile.renameTo(cacheFile)
        } catch (_: Throwable) {}
    }

    private fun decodeHighResBitmap(file: File): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var w = bounds.outWidth
            var h = bounds.outHeight

            if (w <= 0 || h <= 0) {
                try {
                    FileInputStream(file).use { fis ->
                        BitmapFactory.decodeStream(fis, null, bounds)
                        w = bounds.outWidth
                        h = bounds.outHeight
                    }
                } catch (_: Throwable) {}
            }
            if (w <= 0 || h <= 0) return null

            val displayMetrics = resources.displayMetrics
            val maxReqW = (displayMetrics.widthPixels * 1.5).toInt().coerceAtLeast(1080)
            val maxReqH = (displayMetrics.heightPixels * 1.5).toInt().coerceAtLeast(1920)

            var sample = 1
            while (w / sample > maxReqW || h / sample > maxReqH) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            var bmp: Bitmap? = try {
                BitmapFactory.decodeFile(file.absolutePath, opts)
            } catch (e: Throwable) {
                null
            }
            if (bmp == null) {
                try {
                    FileInputStream(file).use { fis ->
                        bmp = BitmapFactory.decodeStream(fis, null, opts)
                    }
                } catch (_: Throwable) {}
            }
            if (bmp == null) return null

            // EXIF Orientation auto-rotation
            val rotation = getExifRotation(file)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                if (rotated != bmp && !bmp.isRecycled) {
                    bmp.recycle()
                }
                bmp = rotated
            }

            return bmp
        } catch (_: Throwable) {
            return null
        }
    }

    private fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Throwable) {
            0
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
                        val diskBmp = loadFromDisplayDiskCache(f)
                        if (diskBmp != null) {
                            fullResCache.put(f.absolutePath, diskBmp)
                        } else {
                            decodeHighResBitmap(f)?.let { bmp ->
                                saveToDisplayDiskCache(f, bmp)
                                fullResCache.put(f.absolutePath, bmp)
                            }
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

        val isFav = FavoritesManager.isFavorite(file.absolutePath)
        updateFavoriteUi(isFav, animate = false)

        // Tier 1: Check process-wide memory cache (instant 0ms display on re-open)
        val cachedFull = fullResCache.get(file.absolutePath)
        if (cachedFull != null && !cachedFull.isRecycled) {
            resetZoom(animate = false)
            currentBitmap = cachedFull
            imgFull.setImageBitmap(cachedFull)
            tvInfo.text = "${cachedFull.width}x${cachedFull.height} • ${Formatters.formatFileSize(file.length())}"
            updateAmbientGlow(cachedFull)
            preloadNeighbors(currentIndex)
            return
        }

        // Tier 2: Instant sharp preview from ThumbnailLoader (0ms)
        val cachedThumb = ThumbnailLoader.getCachedBitmap(file.absolutePath)
        if (cachedThumb != null && !cachedThumb.isRecycled) {
            resetZoom(animate = false)
            currentBitmap = cachedThumb
            imgFull.setImageBitmap(cachedThumb)
            tvInfo.text = Formatters.formatFileSize(file.length())
            updateAmbientGlow(cachedThumb)
        }

        // Tier 3: Asynchronous load: Check persistent disk cache -> decode raw -> save to disk & memory
        executor.execute {
            val diskBmp = loadFromDisplayDiskCache(file)
            if (diskBmp != null) {
                fullResCache.put(file.absolutePath, diskBmp)
                val infoStr = "${diskBmp.width}x${diskBmp.height} • ${Formatters.formatFileSize(file.length())}"
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (currentFile?.absolutePath == file.absolutePath) {
                        resetZoom(animate = false)
                        currentBitmap = diskBmp
                        imgFull.setImageBitmap(diskBmp)
                        tvInfo.text = infoStr
                        updateAmbientGlow(diskBmp)
                    }
                }
                preloadNeighbors(currentIndex)
                return@execute
            }

            // Raw decode from original file
            try {
                val bmp = decodeHighResBitmap(file)
                if (bmp != null) {
                    saveToDisplayDiskCache(file, bmp)
                    fullResCache.put(file.absolutePath, bmp)
                }

                val sizeStr = Formatters.formatFileSize(file.length())
                val infoStr = if (bmp != null) "${bmp.width}x${bmp.height} • $sizeStr" else sizeStr

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (currentFile?.absolutePath == file.absolutePath) {
                        resetZoom(animate = false)
                        if (bmp != null) {
                            currentBitmap = bmp
                            imgFull.setImageBitmap(bmp)
                            updateAmbientGlow(bmp)
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
        val targetAlpha = if (barsVisible) 1.0f else 0.0f
        val duration = 180L

        if (barsVisible) {
            headerBar.visibility = View.VISIBLE
            bottomBar.visibility = View.VISIBLE
        }
        headerBar.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .withEndAction {
                if (!barsVisible) headerBar.visibility = View.GONE
            }
            .start()
        bottomBar.animate()
            .alpha(targetAlpha)
            .setDuration(duration)
            .withEndAction {
                if (!barsVisible) bottomBar.visibility = View.GONE
            }
            .start()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
