package com.artraw.files.ui.gallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.gallery.model.AiMediaItem
import com.artraw.files.util.ClipboardHelper
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.ThumbnailLoader
import java.util.Locale

class SmartGalleryAdapter(
    private val context: Context,
    private var items: List<AiMediaItem> = emptyList(),
    private val onItemClick: (position: Int, item: AiMediaItem) -> Unit,
    private val onItemLongClick: (position: Int, item: AiMediaItem) -> Boolean
) : BaseAdapter() {

    companion object {
        const val LAYOUT_MODE_COMPACT = 0
        const val LAYOUT_MODE_MASONRY = 1
        const val LAYOUT_MODE_BENTO = 2
    }

    private val inflater = LayoutInflater.from(context)
    var isSelectionMode: Boolean = false
        private set

    var currentLayoutMode: Int = LAYOUT_MODE_COMPACT
        private set

    fun setLayoutMode(mode: Int) {
        if (currentLayoutMode != mode) {
            currentLayoutMode = mode
            notifyDataSetChanged()
        }
    }

    fun updateItems(newItems: List<AiMediaItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun getItems(): List<AiMediaItem> = items

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            items.forEach { it.isSelected = false }
        }
        notifyDataSetChanged()
    }

    fun toggleSelection(position: Int) {
        if (position in items.indices) {
            items[position].isSelected = !items[position].isSelected
            val selectedCount = items.count { it.isSelected }
            if (selectedCount == 0 && isSelectionMode) {
                isSelectionMode = false
            } else if (selectedCount > 0 && !isSelectionMode) {
                isSelectionMode = true
            }
            notifyDataSetChanged()
        }
    }

    fun selectAll() {
        items.forEach { it.isSelected = true }
        isSelectionMode = true
        notifyDataSetChanged()
    }

    fun clearSelection() {
        items.forEach { it.isSelected = false }
        isSelectionMode = false
        notifyDataSetChanged()
    }

    fun getSelectedItems(): List<AiMediaItem> = items.filter { it.isSelected }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): AiMediaItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    private class ViewHolder(view: View) {
        val root: FrameLayout = view.findViewById(R.id.cardPhotoRoot)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivGalleryThumbnail)
        val ivFavBadge: ImageView = view.findViewById(R.id.ivGalleryFavBadge)
        val tvBentoBadge: TextView = view.findViewById(R.id.tvGalleryBentoBadge)
        val btnQuickCopyPath: ImageView = view.findViewById(R.id.btnQuickCopyPath)
        val badgeVideo: LinearLayout = view.findViewById(R.id.badgeGalleryVideo)
        val tvDuration: TextView = view.findViewById(R.id.tvGalleryVideoDuration)
        val tvAiBadge: TextView = view.findViewById(R.id.tvGalleryAiBadge)
        val ivSelectionCheck: ImageView = view.findViewById(R.id.ivGallerySelectionCheck)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.item_gallery_photo, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = getItem(position)
        val density = context.resources.displayMetrics.density

        // Adjust Card Height Based on Selected Layout Engine
        val params = holder.root.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (136 * density).toInt()
        )
        when (currentLayoutMode) {
            LAYOUT_MODE_MASONRY -> {
                val ratio = if (item.width > 0 && item.height > 0) {
                    item.height.toFloat() / item.width.toFloat()
                } else 1.0f
                val clamped = ratio.coerceIn(0.72f, 1.55f)
                params.height = (136 * clamped * density).toInt()
            }
            LAYOUT_MODE_BENTO -> {
                // Bento Highlights: Every 5th item or aestheticScore >= 85 gets featured 2x size
                val isBentoHero = (position % 5 == 0) || (item.aestheticScore >= 85.0)
                params.height = if (isBentoHero) (210 * density).toInt() else (128 * density).toInt()
            }
            else -> {
                // Classic Compact Grid
                params.height = (136 * density).toInt()
            }
        }
        holder.root.layoutParams = params

        val defaultIcon = when {
            item.isAudio -> R.drawable.ic_audio
            item.isVideo -> R.drawable.ic_video
            item.isDocument -> R.drawable.ic_document
            else -> R.drawable.ic_image
        }

        ThumbnailLoader.loadThumbnail(
            imageView = holder.ivThumbnail,
            item = item.toFileItem(),
            targetSize = if (currentLayoutMode == LAYOUT_MODE_BENTO) 360 else 240,
            defaultIconRes = defaultIcon
        )

        // Bento / Quality Badge
        val nameLower = item.name.lowercase(Locale.ROOT)
        val isRaw = nameLower.endsWith(".raw") || nameLower.endsWith(".dng") ||
                    nameLower.endsWith(".cr2") || nameLower.endsWith(".nef") ||
                    nameLower.endsWith(".arw") || nameLower.endsWith(".svg") ||
                    nameLower.endsWith(".psd")

        if (isRaw) {
            holder.tvBentoBadge.visibility = View.VISIBLE
            holder.tvBentoBadge.text = "RAW"
            holder.tvBentoBadge.setBackgroundResource(R.drawable.bg_gallery_exif_badge)
        } else if (currentLayoutMode == LAYOUT_MODE_BENTO && (position % 5 == 0 || item.aestheticScore >= 85.0)) {
            holder.tvBentoBadge.visibility = View.VISIBLE
            holder.tvBentoBadge.text = "★ ART"
            holder.tvBentoBadge.setBackgroundResource(R.drawable.bg_cyber_chip_active)
        } else {
            holder.tvBentoBadge.visibility = View.GONE
        }

        // Quick 1-Tap Copy Path Button
        holder.btnQuickCopyPath.setOnClickListener {
            ClipboardHelper.copyPath(context, item.file, it)
        }
        MotionUtils.pressScale(holder.btnQuickCopyPath, 0.88f)

        // Duration for Video or Audio
        if (item.isVideo || item.isAudio) {
            holder.badgeVideo.visibility = View.VISIBLE
            holder.tvDuration.text = item.formattedDuration()
        } else {
            holder.badgeVideo.visibility = View.GONE
        }

        // Favorite Badge
        holder.ivFavBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

        // AI Tag / Format pill
        if (item.isAudio) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "MP3"
        } else if (item.isDocument) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "DOC"
        } else if (item.aestheticScore >= 80.0 && !item.isVideo && holder.tvBentoBadge.visibility != View.VISIBLE) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "★ ${item.aestheticScore.toInt()}"
        } else if (item.detectedTags.contains("screenshot")) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "Shot"
        } else {
            holder.tvAiBadge.visibility = View.GONE
        }

        // Selection State & Highlighting
        if (isSelectionMode) {
            holder.btnQuickCopyPath.visibility = View.GONE
            holder.ivSelectionCheck.visibility = View.VISIBLE
            if (item.isSelected) {
                holder.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle)
                holder.ivSelectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.cyber_violet)
                )
                holder.root.setBackgroundResource(R.drawable.bg_gallery_card_selected)
            } else {
                holder.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle_outline)
                holder.ivSelectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.aurora_text_secondary)
                )
                holder.root.setBackgroundResource(R.drawable.bg_gallery_grid_item)
            }
        } else {
            holder.btnQuickCopyPath.visibility = View.VISIBLE
            holder.ivSelectionCheck.visibility = View.GONE
            holder.root.setBackgroundResource(R.drawable.bg_gallery_grid_item)
        }

        view.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                onItemClick(position, item)
            }
        }

        view.setOnLongClickListener {
            if (!isSelectionMode) {
                onItemLongClick(position, item)
            } else {
                toggleSelection(position)
            }
            true
        }

        MotionUtils.pressScale(view, 0.96f)

        return view
    }
}
