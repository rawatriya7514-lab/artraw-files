package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
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
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.repository.FileRepository
import com.artraw.files.util.FavoritesManager
import com.artraw.files.util.Formatters
import com.artraw.files.util.MimeTypes
import com.artraw.files.util.ThumbnailLoader
import java.io.File
import java.util.concurrent.Executors

class FavoritesActivity : Activity() {

    private lateinit var repository: FileRepository
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tvFavCount: TextView
    private lateinit var listViewFavorites: ListView
    private lateinit var layoutFavEmpty: LinearLayout

    private var favoriteItems: List<FileItem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FavoritesManager.init(this)
        repository = FileRepository(this)
        setContentView(R.layout.activity_favorites)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerFavorites)),
            bottomStickyViews = listOf(findViewById(R.id.bottomNavigationFavorites))
        )

        initViews()
        loadFavorites()
    }

    override fun onResume() {
        super.onResume()
        loadFavorites()
    }

    private fun initViews() {
        tvFavCount = findViewById(R.id.tvFavCount)
        listViewFavorites = findViewById(R.id.listViewFavorites)
        layoutFavEmpty = findViewById(R.id.layoutFavEmpty)

        findViewById<View>(R.id.btnFavBack).setOnClickListener { finish() }

        findViewById<View>(R.id.btnNavHomeFav).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavBrowseFav).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }

        findViewById<View>(R.id.btnNavSettingsFav).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadFavorites() {
        executor.execute {
            favoriteItems = FavoritesManager.getFavoriteFiles()
            runOnUiThread {
                val count = favoriteItems.size
                tvFavCount.text = "$count item${if (count != 1) "s" else ""}"

                if (count == 0) {
                    layoutFavEmpty.visibility = View.VISIBLE
                    listViewFavorites.visibility = View.GONE
                } else {
                    layoutFavEmpty.visibility = View.GONE
                    listViewFavorites.visibility = View.VISIBLE
                    listViewFavorites.adapter = FavoritesAdapter(favoriteItems)
                }
            }
        }
    }

    private inner class FavoritesAdapter(private val items: List<FileItem>) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): FileItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: FavHolder

            if (convertView == null) {
                view = LinearLayout(this@FavoritesActivity).apply {
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

                val icon = ImageView(this@FavoritesActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(40, 40)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
                container.addView(icon)

                val info = LinearLayout(this@FavoritesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12
                    }
                }
                container.addView(info)

                val nameTv = TextView(this@FavoritesActivity).apply {
                    textSize = 15f
                    setTextColor(Color.parseColor("#F0F4F8"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(nameTv)

                val metaTv = TextView(this@FavoritesActivity).apply {
                    textSize = 12f
                    setTextColor(Color.parseColor("#9AA5B1"))
                    setSingleLine()
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                info.addView(metaTv)

                holder = FavHolder(icon = icon, nameTv = nameTv, metaTv = metaTv)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as FavHolder
            }

            val item = items[position]
            val catColor = when (item.category) {
                FileCategory.IMAGES -> R.color.aurora_primary
                FileCategory.VIDEOS -> R.color.aurora_secondary
                FileCategory.AUDIO -> R.color.aurora_primary
                FileCategory.ARCHIVES -> R.color.aurora_secondary
                FileCategory.APKS -> R.color.aurora_success
                else -> R.color.aurora_text_secondary
            }

            holder.icon.setImageResource(MimeTypes.getIconRes(item))
            holder.icon.setColorFilter(resources.getColor(catColor, null))
            holder.nameTv.text = item.name
            holder.metaTv.text = Formatters.formatFileSize(item.size) + " • " + Formatters.formatDate(item.lastModified)

            view.setOnClickListener {
                com.artraw.files.ui.dialog.FileActionDialog.show(this@FavoritesActivity, item)
            }

            view.setOnLongClickListener {
                confirmRemoveFavorite(item, position)
                true
            }

            return view
        }
    }

    private fun confirmRemoveFavorite(item: FileItem, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove from Favorites")
            .setMessage("Remove \"${item.name}\" from favorites?")
            .setPositiveButton("Remove") { _, _ ->
                FavoritesManager.toggleFavorite(item)
                loadFavorites()
                Toast.makeText(this, "Removed from favorites", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class FavHolder(
    val icon: ImageView,
    val nameTv: TextView,
    val metaTv: TextView
)
