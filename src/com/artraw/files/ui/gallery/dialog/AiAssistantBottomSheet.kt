package com.artraw.files.ui.gallery.dialog

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.gallery.ai.AiAssistantEngine
import com.artraw.files.gallery.ai.AssistantResult
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.ui.ImageViewerActivity
import com.artraw.files.ui.VideoPlayerActivity
import com.artraw.files.util.ThumbnailLoader

object AiAssistantBottomSheet {

    fun show(
        context: Context,
        initialQuery: String? = null,
        onViewInGrid: ((List<AiMediaItem>, String) -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_assistant, null)
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
            .setView(view)
            .create()

        val btnClose = view.findViewById<ImageView>(R.id.btnAssistantClose)
        val etInput = view.findViewById<EditText>(R.id.etAssistantInput)
        val btnSend = view.findViewById<ImageView>(R.id.btnAssistantSend)

        val cardWelcome = view.findViewById<LinearLayout>(R.id.cardAssistantWelcome)
        val layoutResults = view.findViewById<LinearLayout>(R.id.layoutAssistantResults)
        val tvUserQuery = view.findViewById<TextView>(R.id.tvAssistantUserQuery)
        val tvAnswer = view.findViewById<TextView>(R.id.tvAssistantAnswer)
        val layoutThumbs = view.findViewById<LinearLayout>(R.id.layoutAssistantThumbs)
        val btnViewInGrid = view.findViewById<Button>(R.id.btnAssistantViewInGrid)

        btnClose.setOnClickListener { dialog.dismiss() }

        var currentResult: AssistantResult? = null

        fun executeQuery(query: String) {
            val q = query.trim()
            if (q.isEmpty()) return

            cardWelcome.visibility = View.GONE
            layoutResults.visibility = View.VISIBLE
            tvUserQuery.text = q

            val result = AiAssistantEngine.ask(context, q)
            currentResult = result
            tvAnswer.text = result.answer

            layoutThumbs.removeAllViews()
            val inflater = LayoutInflater.from(context)
            for (item in result.items.take(15)) {
                val thumbView = inflater.inflate(R.layout.item_gallery_photo, layoutThumbs, false)
                val params = LinearLayout.LayoutParams(160, 160)
                params.setMargins(6, 4, 6, 4)
                thumbView.layoutParams = params

                val iv = thumbView.findViewById<ImageView>(R.id.ivGalleryThumbnail)
                ThumbnailLoader.loadThumbnail(
                    imageView = iv,
                    item = item.toFileItem(),
                    targetSize = 160,
                    defaultIconRes = if (item.isVideo) R.drawable.ic_video else R.drawable.ic_image
                )

                thumbView.setOnClickListener {
                    try {
                        if (item.isVideo) {
                            val intent = Intent(context, VideoPlayerActivity::class.java).apply {
                                putExtra(VideoPlayerActivity.EXTRA_FILE_PATH, item.path)
                            }
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, ImageViewerActivity::class.java).apply {
                                putExtra(ImageViewerActivity.EXTRA_FILE_PATH, item.path)
                            }
                            context.startActivity(intent)
                        }
                    } catch (e: Throwable) {
                        android.widget.Toast.makeText(context, "Could not open media: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                layoutThumbs.addView(thumbView)
            }

            btnViewInGrid.visibility = if (result.items.isNotEmpty()) View.VISIBLE else View.GONE
            btnViewInGrid.setOnClickListener {
                dialog.dismiss()
                onViewInGrid?.invoke(result.items, q)
            }
        }

        btnSend.setOnClickListener {
            val q = etInput.text.toString()
            etInput.setText("")
            executeQuery(q)
        }

        etInput.setOnEditorActionListener { _, _, _ ->
            val q = etInput.text.toString()
            etInput.setText("")
            executeQuery(q)
            true
        }

        // Chip prompts
        val chips = mapOf(
            R.id.chipPromptBest to "Find my best photos",
            R.id.chipPromptMountains to "Show my photos with mountains",
            R.id.chipPromptDog to "Find pictures of my dog",
            R.id.chipPromptRedCar to "Show images with a red car",
            R.id.chipPromptBlurry to "Find blurry pictures",
            R.id.chipPromptDuplicates to "Find duplicate photos",
            R.id.chipPromptDiwali to "Show all photos from Diwali",
            R.id.chipPromptDocuments to "Find photos containing documents",
            R.id.chipPromptScreenshots to "Show screenshots from last month"
        )

        for ((id, text) in chips) {
            view.findViewById<View>(id)?.setOnClickListener {
                executeQuery(text)
            }
        }

        if (!initialQuery.isNullOrBlank()) {
            executeQuery(initialQuery)
        }

        dialog.show()
    }
}
