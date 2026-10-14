package com.artraw.files.ui.adapter

import android.content.Context
import android.graphics.Color
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

class FileListAdapter(
    private val context: Context,
    private var items: List<FileItem>,
    private val isGrid: Boolean = false,
    private val onSelectionChanged: (selectedCount: Int) -> Unit
) : BaseAdapter() {

    private val selectedPaths = mutableSetOf<String>()
    var isSelectionMode: Boolean = false
        private set

    fun updateData(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
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
                root = view
            )
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val item = items[position]
        val isSelected = selectedPaths.contains(item.path)

        holder.name.text = item.name

        // Meta string: size / children count and date
        if (item.isDirectory) {
            val countStr = item.childCount?.let { "$it items" } ?: "Folder"
            val dateStr = Formatters.formatShortDate(item.lastModified)
            holder.meta.text = "$countStr • $dateStr"
        } else {
            val sizeStr = Formatters.formatFileSize(item.size)
            val dateStr = Formatters.formatShortDate(item.lastModified)
            holder.meta.text = "$sizeStr • $dateStr"
        }

        // Icon assignment
        val iconRes = when {
            item.isDirectory -> R.drawable.ic_folder
            item.isApk -> R.drawable.ic_apk
            item.category == FileCategory.IMAGES -> R.drawable.ic_image
            item.category == FileCategory.VIDEOS -> R.drawable.ic_video
            item.category == FileCategory.AUDIO -> R.drawable.ic_audio
            item.category == FileCategory.DOCUMENTS -> R.drawable.ic_document
            item.category == FileCategory.ARCHIVES -> R.drawable.ic_archive
            else -> R.drawable.ic_file
        }
        holder.icon.setImageResource(iconRes)

        // Multi-select Checkbox & Highlights
        if (holder.checkbox != null) {
            if (isSelectionMode) {
                holder.checkbox.visibility = View.VISIBLE
                holder.checkbox.isChecked = isSelected
            } else {
                holder.checkbox.visibility = View.GONE
            }
        }

        if (isSelected) {
            holder.root.setBackgroundResource(R.drawable.bg_selection)
        } else {
            holder.root.setBackgroundColor(Color.TRANSPARENT)
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
        val root: View
    )
}
