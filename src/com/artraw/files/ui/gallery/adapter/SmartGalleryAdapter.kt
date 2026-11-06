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
import com.artraw.files.util.MotionUtils
import com.artraw.files.util.ThumbnailLoader

class SmartGalleryAdapter(
    private val context: Context,
    private var items: List<AiMediaItem> = emptyList(),
    private val onItemClick: (position: Int, item: AiMediaItem) -> Unit,
    private val onItemLongClick: (position: Int, item: AiMediaItem) -> Boolean
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    var isSelectionMode: Boolean = false
        private set

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

        // Load thumbnail with fallback
        ThumbnailLoader.loadThumbnail(
            imageView = holder.ivThumbnail,
            item = item.toFileItem(),
            targetSize = 260,
            defaultIconRes = if (item.isVideo) R.drawable.ic_video else R.drawable.ic_image
        )

        // Video Duration
        if (item.isVideo) {
            holder.badgeVideo.visibility = View.VISIBLE
            holder.tvDuration.text = item.formattedDuration()
        } else {
            holder.badgeVideo.visibility = View.GONE
        }

        // Favorite Badge
        holder.ivFavBadge.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

        // AI Tag / Quality pill
        if (item.aestheticScore >= 80.0 && !item.isVideo) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "★ ${item.aestheticScore.toInt()}"
        } else if (item.detectedTags.contains("screenshot")) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "Shot"
        } else if (item.detectedTags.contains("document")) {
            holder.tvAiBadge.visibility = View.VISIBLE
            holder.tvAiBadge.text = "Doc"
        } else {
            holder.tvAiBadge.visibility = View.GONE
        }

        // Selection State & Highlighting
        if (isSelectionMode) {
            holder.ivSelectionCheck.visibility = View.VISIBLE
            if (item.isSelected) {
                holder.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle)
                holder.ivSelectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.aurora_primary)
                )
                holder.root.setBackgroundResource(R.drawable.bg_gallery_card_selected)
            } else {
                holder.ivSelectionCheck.setImageResource(R.drawable.ic_check_circle_outline)
                holder.ivSelectionCheck.imageTintList = android.content.res.ColorStateList.valueOf(
                    context.getColor(R.color.aurora_text_secondary)
                )
                holder.root.setBackgroundResource(R.drawable.bg_gallery_card)
            }
        } else {
            holder.ivSelectionCheck.visibility = View.GONE
            holder.root.setBackgroundResource(R.drawable.bg_gallery_card)
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
                isSelectionMode = true
                toggleSelection(position)
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
