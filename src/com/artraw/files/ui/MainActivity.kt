package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.model.FileCategory
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var repository: FileRepository
    private lateinit var settingsManager: SettingsManager
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var layoutPermissionBanner: LinearLayout
    private lateinit var btnGrantPermission: Button
    private lateinit var tvStorageTitle: TextView
    private lateinit var tvStoragePercent: TextView
    private lateinit var tvStorageUsed: TextView
    private lateinit var tvStorageFree: TextView
    private lateinit var progressStorage: ProgressBar
    private lateinit var layoutRecentList: LinearLayout
    private lateinit var tvRecentEmpty: TextView
    private lateinit var layoutAdContainer: View
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        repository = FileRepository(this)

        if (!settingsManager.isOnboardingCompleted && !PermissionsHelper.hasStoragePermission(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        initViews()
        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topHeaders = listOf(findViewById(R.id.headerMain)),
            bottomScrollViews = listOf(findViewById(R.id.scrollMainContent)),
            bottomStickyViews = listOf(layoutAdContainer)
        )
        setupListeners()
        initAdMob()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
        checkPermissionsAndLoad()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        adView?.destroy()
        super.onDestroy()
    }

    private fun initViews() {
        layoutPermissionBanner = findViewById(R.id.layoutPermissionBanner)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        tvStorageTitle = findViewById(R.id.tvStorageTitle)
        tvStoragePercent = findViewById(R.id.tvStoragePercent)
        tvStorageUsed = findViewById(R.id.tvStorageUsed)
        tvStorageFree = findViewById(R.id.tvStorageFree)
        progressStorage = findViewById(R.id.progressStorage)
        layoutRecentList = findViewById(R.id.layoutRecentList)
        tvRecentEmpty = findViewById(R.id.tvRecentEmpty)
        layoutAdContainer = findViewById(R.id.layoutAdContainer)
        adView = findViewById(R.id.adView)
    }

    private fun initAdMob() {
        try {
            MobileAds.initialize(this) {
                // Mobile Ads SDK initialized
            }
            val ad = adView ?: return
            val request = AdRequest.Builder().build()
            ad.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    super.onAdLoaded()
                    layoutAdContainer.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    super.onAdFailedToLoad(loadAdError)
                }
            }
            ad.loadAd(request)
        } catch (_: Exception) {
            // Graceful fallback if Google Play Services is unavailable
        }
    }

    private fun setupListeners() {
        btnGrantPermission.setOnClickListener {
            PermissionsHelper.requestStoragePermission(this)
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.cardStorage).setOnClickListener {
            openBrowser(repository.getPrimaryStorageRoot().absolutePath)
        }

        // Quick action chips
        findViewById<View>(R.id.chipBrowseAll).setOnClickListener {
            openBrowser(repository.getPrimaryStorageRoot().absolutePath)
        }

        findViewById<View>(R.id.chipRecent).setOnClickListener {
            openCategory("recent")
        }

        findViewById<View>(R.id.chipLargeFiles).setOnClickListener {
            val intent = Intent(this, AnalyzerActivity::class.java).apply {
                putExtra("tab", "large")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.chipDuplicates).setOnClickListener {
            val intent = Intent(this, AnalyzerActivity::class.java).apply {
                putExtra("tab", "duplicates")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btnViewAllRecent).setOnClickListener {
            openCategory("recent")
        }

        // Categories
        findViewById<View>(R.id.catImages).setOnClickListener { openCategory(FileCategory.IMAGES.id) }
        findViewById<View>(R.id.catVideos).setOnClickListener { openCategory(FileCategory.VIDEOS.id) }
        findViewById<View>(R.id.catAudio).setOnClickListener { openCategory(FileCategory.AUDIO.id) }
        findViewById<View>(R.id.catDocuments).setOnClickListener { openCategory(FileCategory.DOCUMENTS.id) }
        findViewById<View>(R.id.catDownloads).setOnClickListener { openCategory(FileCategory.DOWNLOADS.id) }
        findViewById<View>(R.id.catArchives).setOnClickListener { openCategory(FileCategory.ARCHIVES.id) }
        findViewById<View>(R.id.catApks).setOnClickListener { openCategory(FileCategory.APKS.id) }
        findViewById<View>(R.id.catOther).setOnClickListener { openCategory(FileCategory.OTHER.id) }
    }

    private fun openCategory(categoryId: String) {
        val intent = Intent(this, CategoryActivity::class.java).apply {
            putExtra("category_id", categoryId)
        }
        startActivity(intent)
    }

    private fun openBrowser(path: String) {
        val intent = Intent(this, BrowserActivity::class.java).apply {
            putExtra("initial_path", path)
        }
        startActivity(intent)
    }

    private fun checkPermissionsAndLoad() {
        val hasPerm = PermissionsHelper.hasStoragePermission(this)
        if (hasPerm) {
            layoutPermissionBanner.visibility = View.GONE
            loadStorageStats()
            loadRecentFiles()
        } else {
            layoutPermissionBanner.visibility = View.VISIBLE
            tvStorageUsed.text = "Permission required"
            tvRecentEmpty.text = "Permission required to display recent files."
        }
    }

    private fun loadStorageStats() {
        executor.execute {
            val statsList = repository.getStorageStats()
            val primary = statsList.firstOrNull { !it.isExternalSd } ?: return@execute

            runOnUiThread {
                tvStorageTitle.text = primary.name
                tvStoragePercent.text = "${primary.usedPercent}%"
                progressStorage.progress = primary.usedPercent

                val usedStr = Formatters.formatFileSize(primary.usedBytes)
                val totalStr = Formatters.formatFileSize(primary.totalBytes)
                val freeStr = Formatters.formatFileSize(primary.freeBytes)

                tvStorageUsed.text = getString(R.string.storage_used, usedStr, totalStr)
                tvStorageFree.text = getString(R.string.storage_free, freeStr)
            }
        }
    }

    private fun loadRecentFiles() {
        executor.execute {
            val recent = repository.getRecentFiles(limit = 6)
            runOnUiThread {
                layoutRecentList.removeAllViews()
                if (recent.isEmpty()) {
                    layoutRecentList.addView(tvRecentEmpty)
                    tvRecentEmpty.text = getString(R.string.search_no_results)
                } else {
                    for (item in recent) {
                        val row = LayoutInflater.from(this).inflate(R.layout.item_file_list, layoutRecentList, false)
                        val icon = row.findViewById<ImageView>(R.id.imgFileIcon)
                        val name = row.findViewById<TextView>(R.id.tvFileName)
                        val meta = row.findViewById<TextView>(R.id.tvFileMeta)

                        name.text = item.name
                        meta.text = "${Formatters.formatFileSize(item.size)} • ${Formatters.formatDate(item.lastModified)}"

                        val iconRes = when {
                            item.isApk -> R.drawable.ic_apk
                            item.category == FileCategory.IMAGES -> R.drawable.ic_image
                            item.category == FileCategory.VIDEOS -> R.drawable.ic_video
                            item.category == FileCategory.AUDIO -> R.drawable.ic_audio
                            item.category == FileCategory.DOCUMENTS -> R.drawable.ic_document
                            item.category == FileCategory.ARCHIVES -> R.drawable.ic_archive
                            else -> R.drawable.ic_file
                        }
                        icon.setImageResource(iconRes)

                        row.setOnClickListener {
                            com.artraw.files.ui.dialog.FileActionDialog.show(this, item)
                        }
                        layoutRecentList.addView(row)
                    }
                }
            }
        }
    }
}
