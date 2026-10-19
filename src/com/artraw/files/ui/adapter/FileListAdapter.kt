package com.artraw.files.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.util.Formatters
import com.artraw.files.util.ThumbnailLoader

class FileListAdapter(
    private val context: Context,
    private var items: List<FileItem>,
    private val isGrid: Boolean = false,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : BaseAdapter() {

    private val selectedPaths = mutableSetOf<String>()
    var isSelectionMode: Boolean = false
        private set
    var gridColumns: Int = 3

    fun updateData(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun startSelectionMode() {
        isSelectionMode = true
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    fun startSelection(initialItem: FileItem) {
        isSelectionMode = true
        selectedPaths.add(initialItem.path)
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    fun toggleSelection(item: FileItem) {
        if (selectedPaths.contains(item.path)) {
            selectedPaths.remove(item.path)
            if (selectedPaths.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedPaths.add(item.path)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    fun selectAll() {
        selectedPaths.clear()
        for (item in items) {
            selectedPaths.add(item.path)
        }
        isSelectionMode = true
        notifyDataSetChanged()
        onSelectionChanged(selectedPaths.size)
    }

    fun clearSelection() {
        selectedPaths.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedItems(): List<FileItem> {
        return items.filter { selectedPaths.contains(it.path) }
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): FileItem = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            val layoutId = if (isGrid) R.layout.item_file_grid else R.layout.item_file_list
            view = LayoutInflater.from(context).inflate(layoutId, parent, false)
            holder = ViewHolder(
                icon = view.findViewById(if (isGrid) R.id.imgGridIcon else R.id.imgFileIcon),
                name = view.findViewById(if (isGrid) R.id.tvGridFileName else R.id.tvFileName),
                meta = view.findViewById(if (isGrid) R.id.tvGridFileMeta else R.id.tvFileMeta),
                checkbox = if (!isGrid) view.findViewById(R.id.cbSelect) else null,
                selectBadge = if (isGrid) view.findViewById(R.id.imgGridSelectBadge) else view.findViewById(R.id.imgListSelectBadge),
                encryptionBadge = if (isGrid) view.findViewById(R.id.imgGridEncryptionBadge) else view.findViewById(R.id.imgListEncryptionBadge),
                root = view
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        // Grid item height calculation for Material 3 cards
        if (isGrid) {
            val dm = context.resources.displayMetrics
            val cols = gridColumns.coerceIn(1, 5)
            val paddingTotal = (16 * dm.density).toInt()
            val colWidth = ((dm.widthPixels - paddingTotal) / cols).coerceAtLeast(60)
            val itemHeight = when (cols) {
                1 -> (colWidth * 0.58f).toInt()
                2 -> (colWidth * 0.90f).toInt()
                3 -> (colWidth * 0.96f).toInt()
                4 -> (colWidth * 1.00f).toInt()
                else -> (colWidth * 1.05f).toInt()
            }
            view.layoutParams = android.widget.AbsListView.LayoutParams(
                android.widget.AbsListView.LayoutParams.MATCH_PARENT, itemHeight
            )

            val thumbFrame = view.findViewById<View>(R.id.frameGridThumbnail)
            thumbFrame?.layoutParams?.let { lp ->
                val ratio = if (cols == 1) 0.72f else 0.68f
                lp.height = (itemHeight * ratio).toInt()
                thumbFrame.layoutParams = lp
            }
        }

        val item = items[position]
        val isSelected = selectedPaths.contains(item.path)

        holder.name.text = item.name

        // Meta string: size / children count and date
        val metaBuilder = StringBuilder()
        if (item.isDirectory) {
            val countStr = item.childCount?.let { "$it items" } ?: "Folder"
            val dateStr = Formatters.formatShortDate(item.lastModified)
            metaBuilder.append("$countStr • $dateStr")
        } else {
            val sizeStr = Formatters.formatFileSize(item.size)
            val dateStr = Formatters.formatShortDate(item.lastModified)
            metaBuilder.append("$sizeStr • $dateStr")
        }

        // Encryption badge indicator
        if (item.isEncrypted) {
            metaBuilder.append("  🔒")
            holder.encryptionBadge?.visibility = View.VISIBLE
        } else {
            holder.encryptionBadge?.visibility = View.GONE
        }
        holder.meta.text = metaBuilder.toString()

        val isApk = item.isApk || item.category == FileCategory.APKS || item.extension.equals("apk", ignoreCase = true)
        val iconRes = when {
            item.isDirectory -> R.drawable.ic_folder
            isApk -> R.drawable.ic_apk
            item.category == FileCategory.IMAGES -> R.drawable.ic_image
            item.category == FileCategory.VIDEOS -> R.drawable.ic_video
            item.category == FileCategory.AUDIO -> R.drawable.ic_audio
            item.category == FileCategory.DOCUMENTS -> R.drawable.ic_document
            item.category == FileCategory.ARCHIVES -> R.drawable.ic_archive
            else -> R.drawable.ic_file
        }

        val targetThumb = if (isGrid) {
            when (gridColumns) {
                1 -> 400
                2 -> 280
                3 -> 200
                else -> 120
            }
        } else 120

        ThumbnailLoader.loadThumbnail(
            imageView = holder.icon,
            item = item,
            targetSize = targetThumb,
            defaultIconRes = iconRes
        )

        // Multi-select Checkbox (List Mode)
        if (holder.checkbox != null) {
            if (isSelectionMode) {
                holder.checkbox.visibility = View.VISIBLE
                holder.checkbox.isChecked = isSelected
            } else {
                holder.checkbox.visibility = View.GONE
            }
        }

        // Multi-select Check Badge (Grid Mode - Gallery Style)
        if (holder.selectBadge != null) {
            if (isSelectionMode) {
                holder.selectBadge.visibility = View.VISIBLE
                if (isSelected) {
                    holder.selectBadge.setImageResource(R.drawable.ic_check_circle)
                } else {
                    holder.selectBadge.setImageResource(R.drawable.ic_check_circle_outline)
                }
            } else {
                holder.selectBadge.visibility = View.GONE
            }
        }

        // Material 3 Selection highlight using ripple background
        if (isSelected) {
            holder.root.setBackgroundResource(R.drawable.bg_selection)
        } else {
            // Background is already ripple_material from XML, no need to reset
        }

        // Cut Pending visual dimming
        holder.root.alpha = if (item.isCutPending) 0.5f else 1.0f

        return view
    }

    private class ViewHolder(
        val icon: ImageView,
        val name: TextView,
        val meta: TextView,
        val checkbox: CheckBox?,
        val selectBadge: ImageView?,
        val encryptionBadge: ImageView?,
        val root: View
    )
}
