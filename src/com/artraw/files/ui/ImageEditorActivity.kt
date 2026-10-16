package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EditState
import com.artraw.files.util.FilterType
import com.artraw.files.util.ImageProcessor
import java.io.File

/**
 * ImageEditorActivity — Lightroom-style image editor.
 * Features: crop, rotate, flip, brightness, contrast, saturation, hue, temperature,
 * shadows, highlights, clarity, vignette, filters (vivid, moody, warm, cool, BW, sepia),
 * text overlay, and save.
 */
class ImageEditorActivity : Activity() {

    private lateinit var imageView: ImageView
    private lateinit var originalBitmap: Bitmap
    private lateinit var editedBitmap: Bitmap
    private var editState = EditState()
    private var imagePath: String = ""

    private lateinit var tvBrightness: TextView
    private lateinit var sbBrightness: SeekBar
    private lateinit var tvContrast: TextView
    private lateinit var sbContrast: SeekBar
    private lateinit var tvSaturation: TextView
    private lateinit var sbSaturation: SeekBar
    private lateinit var tvHue: TextView
    private lateinit var sbHue: SeekBar
    private lateinit var tvTemperature: TextView
    private lateinit var sbTemperature: SeekBar
    private lateinit var tvShadows: TextView
    private lateinit var sbShadows: SeekBar
    private lateinit var tvHighlights: TextView
    private lateinit var sbHighlights: SeekBar
    private lateinit var tvClarity: TextView
    private lateinit var sbClarity: SeekBar
    private lateinit var tvVignette: TextView
    private lateinit var sbVignette: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_editor)

        imagePath = intent.getStringExtra("image_path") ?: ""
        if (imagePath.isEmpty()) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(this, "Image not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        originalBitmap = BitmapFactory.decodeFile(imagePath)
        if (originalBitmap == null) {
            Toast.makeText(this, "Cannot decode image", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Resize if too large for editing
        val maxEditSize = 1600
        if (originalBitmap.width > maxEditSize || originalBitmap.height > maxEditSize) {
            originalBitmap = ImageProcessor.resizeBitmap(originalBitmap, maxEditSize, maxEditSize)
        }

        editedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        initViews()
        renderImage()
    }

    private fun initViews() {
        imageView = findViewById(R.id.editorImageView)

        tvBrightness = findViewById(R.id.tvBrightnessVal)
        sbBrightness = findViewById(R.id.sbBrightness)
        tvContrast = findViewById(R.id.tvContrastVal)
        sbContrast = findViewById(R.id.sbContrast)
        tvSaturation = findViewById(R.id.tvSaturationVal)
        sbSaturation = findViewById(R.id.sbSaturation)
        tvHue = findViewById(R.id.tvHueVal)
        sbHue = findViewById(R.id.sbHue)
        tvTemperature = findViewById(R.id.tvTemperatureVal)
        sbTemperature = findViewById(R.id.sbTemperature)
        tvShadows = findViewById(R.id.tvShadowsVal)
        sbShadows = findViewById(R.id.sbShadows)
        tvHighlights = findViewById(R.id.tvHighlightsVal)
        sbHighlights = findViewById(R.id.sbHighlights)
        tvClarity = findViewById(R.id.tvClarityVal)
        sbClarity = findViewById(R.id.sbClarity)
        tvVignette = findViewById(R.id.tvVignetteVal)
        sbVignette = findViewById(R.id.sbVignette)

        // Back button
        findViewById<View>(R.id.btnEditorBack).setOnClickListener {
            confirmDiscard()
        }

        // Action buttons
        findViewById<View>(R.id.btnRotateLeft).setOnClickListener {
            editState.rotation = (editState.rotation - 90f) % 360f
            applyEdits()
        }
        findViewById<View>(R.id.btnRotateRight).setOnClickListener {
            editState.rotation = (editState.rotation + 90f) % 360f
            applyEdits()
        }
        findViewById<View>(R.id.btnFlipH).setOnClickListener {
            editState.flipHorizontal = !editState.flipHorizontal
            applyEdits()
        }
        findViewById<View>(R.id.btnFlipV).setOnClickListener {
            editState.flipVertical = !editState.flipVertical
            applyEdits()
        }
        findViewById<View>(R.id.btnCrop).setOnClickListener {
            showCropDialog()
        }
        findViewById<View>(R.id.btnFilters).setOnClickListener {
            showFilterDialog()
        }
        findViewById<View>(R.id.btnText).setOnClickListener {
            showTextDialog()
        }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            resetEdits()
        }
        findViewById<View>(R.id.btnSave).setOnClickListener {
            saveImage()
        }

        // Setup seek bars
        setupSeekBar(sbBrightness, tvBrightness, -100, 100, 0, { v -> editState.brightness = v / 100f; applyEdits() })
        setupSeekBar(sbContrast, tvContrast, -100, 100, 0, { v -> editState.contrast = v / 100f; applyEdits() })
        setupSeekBar(sbSaturation, tvSaturation, -100, 100, 0, { v -> editState.saturation = v / 100f; applyEdits() })
        setupSeekBar(sbHue, tvHue, -180, 180, 0, { v -> editState.hue = v.toFloat(); applyEdits() })
        setupSeekBar(sbTemperature, tvTemperature, -1000, 1000, 0, { v -> editState.temperature = v.toFloat(); applyEdits() })
        setupSeekBar(sbShadows, tvShadows, -100, 100, 0, { v -> editState.shadows = v / 100f; applyEdits() })
        setupSeekBar(sbHighlights, tvHighlights, -100, 100, 0, { v -> editState.highlights = v / 100f; applyEdits() })
        setupSeekBar(sbClarity, tvClarity, -100, 100, 0, { v -> editState.clarity = v / 100f; applyEdits() })
        setupSeekBar(sbVignette, tvVignette, 0, 100, 0, { v -> editState.vignette = v / 100f; applyEdits() })
    }

    private fun setupSeekBar(
        sb: SeekBar,
        tv: TextView,
        min: Int,
        max: Int,
        default: Int,
        onChange: (Int) -> Unit
    ) {
        sb.max = max - min
        sb.setProgress(default - min)
        tv.text = default.toString()
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val actual = progress + min
                tv.text = actual.toString()
                if (fromUser) onChange(actual)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun renderImage() {
        imageView.setImageBitmap(editedBitmap)
    }

    private fun applyEdits() {
        var bmp = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        // Rotation & flip
        if (editState.rotation != 0f || editState.flipHorizontal || editState.flipVertical) {
            bmp = ImageProcessor.rotateBitmap(bmp, editState.rotation, editState.flipHorizontal, editState.flipVertical)
        }

        // Color adjustments
        bmp = ImageProcessor.adjustColors(
            bmp,
            brightness = editState.brightness,
            contrast = editState.contrast,
            saturation = editState.saturation,
            hue = editState.hue,
            temperature = editState.temperature
        )

        // Shadows/Highlights
        if (editState.shadows != 0f || editState.highlights != 0f) {
            bmp = ImageProcessor.adjustShadowsHighlights(bmp, editState.shadows, editState.highlights)
        }

        // Clarity
        if (editState.clarity != 0f) {
            bmp = ImageProcessor.adjustClarity(bmp, editState.clarity)
        }

        // Filter
        if (editState.filterType != FilterType.NONE) {
            bmp = ImageProcessor.applyFilter(bmp, editState.filterType)
        }

        // Vignette
        if (editState.vignette > 0f) {
            bmp = ImageProcessor.applyVignette(bmp, editState.vignette)
        }

        editedBitmap = bmp
        renderImage()
    }

    private fun resetEdits() {
        editState = EditState()
        sbBrightness.setProgress(0)
        sbContrast.setProgress(0)
        sbSaturation.setProgress(0)
        sbHue.setProgress(0)
        sbTemperature.setProgress(0)
        sbShadows.setProgress(0)
        sbHighlights.setProgress(0)
        sbClarity.setProgress(0)
        sbVignette.setProgress(0)
        editedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        renderImage()
    }

    private fun showCropDialog() {
        val dialog = AlertDialog.Builder(this)
        dialog.setTitle("Crop Image")
        dialog.setMessage("Enter crop region (percent 0-100):")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(24, 24, 24, 24)

        val etLeft = android.widget.EditText(this).apply { hint = "Left (%)"; setText("0") }
        val etTop = android.widget.EditText(this).apply { hint = "Top (%)"; setText("0") }
        val etRight = android.widget.EditText(this).apply { hint = "Right (%)"; setText("100") }
        val etBottom = android.widget.EditText(this).apply { hint = "Bottom (%)"; setText("100") }

        layout.addView(etLeft)
        layout.addView(etTop)
        layout.addView(etRight)
        layout.addView(etBottom)
        layout.setPadding(24, 24, 24, 24)
        dialog.setView(layout)

        dialog.setPositiveButton("Crop") { _, _ ->
            try {
                val l = (etLeft.text.toString().toFloat() / 100f * editedBitmap.width).toInt()
                val t = (etTop.text.toString().toFloat() / 100f * editedBitmap.height).toInt()
                val r = (etRight.text.toString().toFloat() / 100f * editedBitmap.width).toInt()
                val b = (etBottom.text.toString().toFloat() / 100f * editedBitmap.height).toInt()
                editedBitmap = ImageProcessor.cropBitmap(editedBitmap, l, t, r, b)
                originalBitmap = editedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                resetEdits()
                Toast.makeText(this, "Cropped", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Invalid crop values", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.setNegativeButton("Cancel", null)
        dialog.show()
    }

    private fun showFilterDialog() {
        val filters = arrayOf("None", "Vivid", "Moody", "Warm", "Cool", "Black & White", "Sepia")
        AlertDialog.Builder(this)
            .setTitle("Select Filter")
            .setItems(filters) { _, which ->
                editState.filterType = FilterType.values()[which]
                applyEdits()
            }
            .show()
    }

    private fun showTextDialog() {
        val input = android.widget.EditText(this)
        input.hint = "Enter text"
        AlertDialog.Builder(this)
            .setTitle("Add Text Overlay")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val text = input.text.toString()
                if (text.isNotEmpty()) {
                    editedBitmap = ImageProcessor.addTextOverlay(
                        editedBitmap,
                        text,
                        textSize = 48f,
                        x = editedBitmap.width / 4f,
                        y = editedBitmap.height / 4f
                    )
                    renderImage()
                    Toast.makeText(this, "Text added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveImage() {
        val file = File(imagePath)
        val parent = file.parentFile
        val name = file.nameWithoutExtension
        val ext = file.extension
        val savePath = "${parent?.absolutePath ?: imagePath}/${name}_edited.$ext"

        try {
            java.io.FileOutputStream(savePath).use { out ->
                editedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            Toast.makeText(this, "Saved: $savePath", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK, Intent().putExtra("saved_path", savePath))
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("Discard Changes?")
            .setMessage("Unsaved edits will be lost.")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Continue Editing", null)
            .show()
    }

    override fun onBackPressed() {
        confirmDiscard()
    }

    override fun onDestroy() {
        originalBitmap.recycle()
        editedBitmap.recycle()
        super.onDestroy()
    }
}
