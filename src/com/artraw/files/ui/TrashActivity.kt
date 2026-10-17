package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.TrashManager
import java.io.File

class TrashActivity : Activity() {

    private lateinit var tvTrashInfo: TextView
    private lateinit var listViewTrash: ListView
    private lateinit var layoutTrashEmpty: LinearLayout

    private var trashItems: List<TrashManager.TrashedItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrashManager.init(this)
        setContentView(R.layout.activity_trash)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerTrash)),
            bottomStickyViews = listOf(findViewById(R.id.bottomNavigationTrash))
        )

        initViews()
        loadTrash()
    }

    override fun onResume() {
        super.onResume()
        loadTrash()
    }

    private fun initViews() {
        tvTrashInfo = findViewById(R.id.tvTrashInfo)
        listViewTrash = findViewById(R.id.listViewTrash)
        layoutTrashEmpty = findViewById(R.id.layoutTrashEmpty)

        findViewById<View>(R.id.btnTrashBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnEmptyTrash).setOnClickListener {
            confirmEmptyTrash()
        }

        findViewById<View>(R.id.btnNavHomeTrash).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavBrowseTrash).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavSettingsTrash).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadTrash() {
        trashItems = TrashManager.getTrashItems()
        val count = trashItems.size
        val sizeStr = Formatters.formatFileSize(TrashManager.trashSize())
        tvTrashInfo.text = "$count item${if (count != 1) "s" else ""} • $sizeStr"

        if (count == 0) {
            layoutTrashEmpty.visibility = View.VISIBLE
            listViewTrash.visibility = View.GONE
            findViewById<View>(R.id.btnEmptyTrash).isEnabled = false
        } else {
            layoutTrashEmpty.visibility = View.GONE
            listViewTrash.visibility = View.VISIBLE
            listViewTrash.adapter = TrashAdapter(trashItems)
            findViewById<View>(R.id.btnEmptyTrash).isEnabled = true
        }
    }

    private inner class TrashAdapter(private val items: List<TrashManager.TrashedItem>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): TrashManager.TrashedItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: TrashHolder

            if (convertView == null) {
                view = LinearLayout(this@TrashActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundResource(R.drawable.bg_glass_file_item)
                    val params = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = params
                }
                val container = view as LinearLayout

                val icon = ImageView(this@TrashActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 40)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageResource(if (items[position].isDirectory) R.drawable.ic_folder else R.drawable.ic_file)
                    setColorFilter(android.graphics.Color.parseColor("#4ECDC4"))
                }
                container.addView(icon)

                val info = LinearLayout(this@TrashActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                    }
                }
                container.addView(info)

                val nameTv = TextView(this@TrashActivity).apply {
                    textSize = 15f
                    setTextColor(android.graphics.Color.parseColor("#F0F4F8"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(nameTv)

                val metaTv = TextView(this@TrashActivity).apply {
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#9AA5B1"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(metaTv)

                holder = TrashHolder(icon = icon, nameTv = nameTv, metaTv = metaTv)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as TrashHolder
            }

            val item = items[position]
            holder.nameTv.text = item.originalName
            holder.metaTv.text = Formatters.formatFileSize(item.size) + " • " + item.originalPath

            view.setOnClickListener {
                showItemOptions(item, position)
            }

            return view
        }
    }

    private fun showItemOptions(item: TrashManager.TrashedItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle(item.originalName)
            .setMessage(item.originalPath)
            .setPositiveButton(R.string.trash_restore) { _, _ ->
                if (TrashManager.restoreItem(item.trashedPath)) {
                    Toast.makeText(this, R.string.trash_restored, Toast.LENGTH_SHORT).show()
                    loadTrash()
                } else {
                    Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.trash_delete_permanent) { _, _ ->
                if (TrashManager.deletePermanently(item.trashedPath)) {
                    Toast.makeText(this, "Deleted permanently", Toast.LENGTH_SHORT).show()
                    loadTrash()
                } else {
                    Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(R.string.btn_cancel, null)
            .show()
    }

    private fun confirmEmptyTrash() {
        AlertDialog.Builder(this)
            .setTitle("Empty Recycle Bin")
            .setMessage("All ${trashItems.size} items will be permanently deleted. This cannot be undone.")
            .setPositiveButton("Empty Now") { _, _ ->
                val count = TrashManager.emptyTrash()
                Toast.makeText(this, "Emptied $count items", Toast.LENGTH_SHORT).show()
                loadTrash()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

class TrashHolder(
    val icon: ImageView,
    val nameTv: TextView,
    val metaTv: TextView
)
