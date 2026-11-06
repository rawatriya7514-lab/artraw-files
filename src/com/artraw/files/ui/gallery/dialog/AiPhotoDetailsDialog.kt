package com.artraw.files.ui.gallery.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.gallery.db.AiGalleryDatabase
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.util.FavoritesManager
import com.artraw.files.util.Formatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiPhotoDetailsDialog {

    fun show(
        context: Context,
        item: AiMediaItem,
        onFindSimilar: ((AiMediaItem) -> Unit)? = null,
        onFavoriteToggled: ((Boolean) -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_photo_details, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        val tvFileName = view.findViewById<TextView>(R.id.tvDetailsFileName)
        val tvMeta = view.findViewById<TextView>(R.id.tvDetailsMeta)
        val tvDescription = view.findViewById<TextView>(R.id.tvDetailsDescription)
        val tvAesthetic = view.findViewById<TextView>(R.id.tvDetailsAesthetic)
        val tvSharpness = view.findViewById<TextView>(R.id.tvDetailsSharpness)
        val viewColorSwatch = view.findViewById<View>(R.id.viewColorSwatch)
        val tvDominantColor = view.findViewById<TextView>(R.id.tvDetailsDominantColor)
        val tvTags = view.findViewById<TextView>(R.id.tvDetailsTags)
        val layoutOcr = view.findViewById<LinearLayout>(R.id.layoutDetailsOcr)
        val tvOcrText = view.findViewById<TextView>(R.id.tvDetailsOcrText)
        val btnClose = view.findViewById<ImageView>(R.id.btnDetailsClose)
        val btnCopyPath = view.findViewById<Button>(R.id.btnDetailsCopyPath)
        val btnSimilar = view.findViewById<Button>(R.id.btnDetailsSimilar)
        val btnFav = view.findViewById<Button>(R.id.btnDetailsFav)

        btnCopyPath?.setOnClickListener {
            com.artraw.files.util.ClipboardHelper.copyPath(context, item.file, it)
        }

        tvFileName.text = item.name
        val dateFormatted = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()).format(Date(item.dateTaken))
        val dimenText = if (item.width > 0 && item.height > 0) "${item.width} × ${item.height} • " else ""
        tvMeta.text = "$dimenText${Formatters.formatFileSize(item.size)} • $dateFormatted"

        tvDescription.text = item.description.ifEmpty { "Media item: ${item.name}" }

        val aestheticScore = item.aestheticScore.toInt()
        val aestheticLabel = when {
            aestheticScore >= 80 -> "$aestheticScore / 100 • Excellent"
            aestheticScore >= 60 -> "$aestheticScore / 100 • Good Quality"
            else -> "$aestheticScore / 100 • Standard"
        }
        tvAesthetic.text = aestheticLabel

        val sharpnessLabel = if (item.isBlurry) {
            "Motion Blur Detected (${item.blurScore.toInt()})"
        } else {
            "Sharp Focus (${item.blurScore.toInt()})"
        }
        tvSharpness.text = sharpnessLabel

        // Dominant Color
        try {
            viewColorSwatch.setBackgroundColor(Color.parseColor(item.colorHex))
        } catch (_: Throwable) {
            viewColorSwatch.setBackgroundColor(Color.GRAY)
        }
        tvDominantColor.text = "${item.dominantColor} (${item.colorHex})"

        // Detected Tags
        tvTags.text = if (item.detectedTags.isNotEmpty()) {
            item.detectedTags.joinToString(", ")
        } else {
            "General photo"
        }

        // OCR Text
        if (item.ocrText.isNotBlank()) {
            layoutOcr.visibility = View.VISIBLE
            tvOcrText.text = item.ocrText
        } else {
            layoutOcr.visibility = View.GONE
        }

        // Buttons
        btnClose.setOnClickListener { dialog.dismiss() }

        btnSimilar.setOnClickListener {
            dialog.dismiss()
            onFindSimilar?.invoke(item)
        }

        fun updateFavButton(isFav: Boolean) {
            btnFav.text = if (isFav) "Favorited ★" else "Favorite ☆"
        }
        updateFavButton(item.isFavorite)

        btnFav.setOnClickListener {
            val db = AiGalleryDatabase.getInstance(context)
            val newState = db.toggleFavorite(item.path)
            item.isFavorite = newState
            FavoritesManager.toggleFavorite(item.toFileItem())
            updateFavButton(newState)
            onFavoriteToggled?.invoke(newState)
            Toast.makeText(context, if (newState) "Added to Favorites" else "Removed from Favorites", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
