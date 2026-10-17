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
import com.artraw.files.model.FileItem
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.JunkCleaner
import com.artraw.files.util.MimeTypes
import java.io.File
import java.util.concurrent.Executors

class JunkCleanerActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var btnScanJunk: android.widget.Button
    private lateinit var btnCleanJunk: android.widget.Button
    private lateinit var cardScanJunk: View
    private lateinit var layoutJunkResults: View
    private lateinit var layoutJunkEmpty: View
    private lateinit var layoutCleanBar: View
    private lateinit var listViewJunk: ListView
    private lateinit var tvJunkTotalSize: TextView

    private var junkCategories: List<JunkCleaner.JunkCategory> = emptyList()
    private var selectedItems = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_junk_cleaner)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerJunk)),
            bottomStickyViews = listOf(findViewById(R.id.layoutCleanBar), findViewById(R.id.bottomNavigationJunk))
        )

        initViews()
        setupListeners()
    }

    private fun initViews() {
        btnScanJunk = findViewById(R.id.btnScanJunk)
        btnCleanJunk = findViewById(R.id.btnCleanJunk)
        cardScanJunk = findViewById(R.id.cardScanJunk)
        layoutJunkResults = findViewById(R.id.layoutJunkResults)
        layoutJunkEmpty = findViewById(R.id.layoutJunkEmpty)
        layoutCleanBar = findViewById(R.id.layoutCleanBar)
        listViewJunk = findViewById(R.id.listViewJunk)
        tvJunkTotalSize = findViewById(R.id.tvJunkTotalSize)

        findViewById<View>(R.id.btnJunkBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnNavHomeJunk).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavBrowseJunk).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavSettingsJunk).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupListeners() {
        btnScanJunk.setOnClickListener {
            startScan()
        }

        btnCleanJunk.setOnClickListener {
            confirmClean()
        }
    }

    private fun startScan() {
        btnScanJunk.isEnabled = false
        btnScanJunk.text = "Scanning..."
        cardScanJunk.visibility = View.GONE
        layoutJunkEmpty.visibility = View.GONE
        layoutJunkResults.visibility = View.GONE
        layoutCleanBar.visibility = View.GONE
        selectedItems.clear()

        executor.execute {
            val categories = JunkCleaner.scanJunk(this@JunkCleanerActivity)
            runOnUiThread {
                btnScanJunk.isEnabled = true
                btnScanJunk.text = getString(R.string.junk_scan)
                junkCategories = categories

                if (categories.isEmpty()) {
                    layoutJunkEmpty.visibility = View.VISIBLE
                } else {
                    layoutJunkResults.visibility = View.VISIBLE
                    layoutCleanBar.visibility = View.VISIBLE
                    val totalSize = categories.sumOf { it.totalSize }
                    tvJunkTotalSize.text = Formatters.formatFileSize(totalSize)
                    listViewJunk.adapter = JunkAdapter(categories)
                }
            }
        }
    }

    private fun confirmClean() {
        val itemsToDelete = junkCategories.flatMap { it.items }.filter {
            selectedItems.contains(it.path)
        }
        if (itemsToDelete.isEmpty()) {
            Toast.makeText(this, "Select items to clean", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Clean")
            .setMessage("Delete ${itemsToDelete.size} junk file(s)? This cannot be undone.")
            .setPositiveButton("Clean") { _, _ ->
                val count = JunkCleaner.deleteItems(itemsToDelete)
                Toast.makeText(this, "Cleaned $count items", Toast.LENGTH_SHORT).show()
                startScan()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private inner class JunkAdapter(private val categories: List<JunkCleaner.JunkCategory>) : BaseAdapter() {

        override fun getCount(): Int = categories.size
        override fun getItem(position: Int): JunkCleaner.JunkCategory = categories[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: JunkHolder

            if (convertView == null) {
                view = LinearLayout(this@JunkCleanerActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 14, 16, 14)
                    setBackgroundResource(R.drawable.bg_glass_file_item)
                    val params = AbsListView.LayoutParams(
                        AbsListView.LayoutParams.MATCH_PARENT,
                        AbsListView.LayoutParams.WRAP_CONTENT
                    )
                    layoutParams = params
                }
                val container = view as LinearLayout

                val header = LinearLayout(this@JunkCleanerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                container.addView(header)

                val icon = ImageView(this@JunkCleanerActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(32, 32)
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(android.graphics.Color.parseColor("#FF5252"))
                }
                header.addView(icon)

                val nameTv = TextView(this@JunkCleanerActivity).apply {
                    textSize = 15f
                    setTextColor(android.graphics.Color.parseColor("#F0F4F8"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 10
                    }
                }
                header.addView(nameTv)

                val sizeTv = TextView(this@JunkCleanerActivity).apply {
                    textSize = 13f
                    setTextColor(android.graphics.Color.parseColor("#4ECDC4"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                header.addView(sizeTv)

                val descTv = TextView(this@JunkCleanerActivity).apply {
                    textSize = 12f
                    setTextColor(android.graphics.Color.parseColor("#9AA5B1"))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.topMargin = 4
                    layoutParams = lp
                }
                container.addView(descTv)

                holder = JunkHolder(icon = icon, nameTv = nameTv, sizeTv = sizeTv, descTv = descTv)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as JunkHolder
            }

            val cat = categories[position]
            holder.nameTv.text = cat.name
            holder.sizeTv.text = Formatters.formatFileSize(cat.totalSize)
            holder.descTv.text = cat.description

            view.setOnClickListener {
                showCategoryItems(cat)
            }

            return view
        }
    }

    private fun showCategoryItems(cat: JunkCleaner.JunkCategory) {
        AlertDialog.Builder(this)
            .setTitle(cat.name)
            .setMessage("${cat.items.size} items • ${Formatters.formatFileSize(cat.totalSize)}")
            .setPositiveButton("Select All") { _, _ ->
                for (item in cat.items) selectedItems.add(item.path)
                Toast.makeText(this, "Selected ${cat.items.size} items", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class JunkHolder(
    val icon: ImageView,
    val nameTv: TextView,
    val sizeTv: TextView,
    val descTv: TextView
)
