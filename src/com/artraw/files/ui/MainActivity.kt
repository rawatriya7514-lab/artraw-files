package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.ad.AdMobManager
import com.artraw.files.model.FileCategory
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.util.AppSecurity
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
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
    private lateinit var adViewContainer: FrameLayout
    private var adView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        repository = FileRepository(this)

        if (!AppSecurity.verifyIntegrity(this)) {
            Log.w("ARTRAW_SECURITY", "Non-standard environment detected; continuing in safe mode.")
        }

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
            bottomStickyViews = listOf(adViewContainer)
        )
        setupListeners()
        initAdMob()
    }

    override fun onResume() {
        super.onResume()
        try {
            adView?.resume()
        } catch (_: Throwable) {}
        checkPermissionsAndLoad()
    }

    override fun onPause() {
        try {
            adView?.pause()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroy() {
        try {
            adView?.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkPermissionsAndLoad()
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
        adViewContainer = findViewById(R.id.adViewContainer)
    }

    private var isAdMobInitializing = false
    private var hasFallbackToTestAd = false

    private fun initAdMob() {
        if (!resources.getBoolean(R.bool.admob_enabled)) {
            adViewContainer.visibility = View.GONE
            return
        }

        if (isAdMobInitializing) return
        isAdMobInitializing = true

        try {
            MobileAds.initialize(this) { initStatus ->
                Log.d("ARTRAW_ADMOB", "MobileAds initialized: $initStatus")
            }
        } catch (t: Throwable) {
            Log.e("ARTRAW_ADMOB", "MobileAds init exception: ${t.message}", t)
        }

        val rawId = getString(R.string.admob_banner_ad_unit_id)
        val resolvedId = AppSecurity.resolveAdUnitId(rawId)
        loadBanner(resolvedId, isFallback = false)
    }

    private fun loadBanner(adUnit: String, isFallback: Boolean) {
        runOnUiThread {
            try {
                val container = adViewContainer
                container.visibility = View.VISIBLE
                val ad = AdView(this).apply {
                    adUnitId = adUnit
                    setAdSize(AdSize.BANNER)
                }
                this.adView = ad

                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }

                container.removeAllViews()
                container.addView(ad, params)

                ad.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        super.onAdLoaded()
                        Log.d("ARTRAW_ADMOB", "Ad loaded successfully! (Unit: $adUnit)")
                        container.visibility = View.VISIBLE
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        Log.w("ARTRAW_ADMOB", "Ad failed to load (code ${loadAdError.code}): ${loadAdError.message}")

                        if (!isFallback && !hasFallbackToTestAd) {
                            hasFallbackToTestAd = true
                            Log.i("ARTRAW_ADMOB", "Falling back to Google official test banner...")
                            loadBanner("ca-app-pub-3940256099942544/6300978111", isFallback = true)
                        }
                    }
                }

                val request = AdRequest.Builder().build()
                ad.loadAd(request)

                if (!isFallback && !hasFallbackToTestAd) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (!hasFallbackToTestAd && !isFinishing) {
                            hasFallbackToTestAd = true
                            Log.i("ARTRAW_ADMOB", "Watchdog triggered fallback to Google test banner.")
                            loadBanner("ca-app-pub-3940256099942544/6300978111", isFallback = true)
                        }
                    }, 4500)
                }
            } catch (t: Throwable) {
                Log.e("ARTRAW_ADMOB", "loadBanner error: ${t.message}", t)
            }
        }
    }

    private fun setupListeners() {
        btnGrantPermission.setOnClickListener {
            PermissionsHelper.requestStoragePermission(this, 1001)
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
        try {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "checkPermissionsAndLoad error", e)
        }
    }

    private fun loadStorageStats() {
        executor.execute {
            try {
                val statsList = repository.getStorageStats()
                val primary = statsList.firstOrNull { !it.isExternalSd } ?: return@execute

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    try {
                        tvStorageTitle.text = primary.name
                        tvStoragePercent.text = "${primary.usedPercent}%"
                        progressStorage.progress = primary.usedPercent

                        val usedStr = Formatters.formatFileSize(primary.usedBytes)
                        val totalStr = Formatters.formatFileSize(primary.totalBytes)
                        val freeStr = Formatters.formatFileSize(primary.freeBytes)

                        tvStorageUsed.text = getString(R.string.storage_used, usedStr, totalStr)
                        tvStorageFree.text = getString(R.string.storage_free, freeStr)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error updating storage UI", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error calculating storage stats", e)
            }
        }
    }

    private fun loadRecentFiles() {
        executor.execute {
            try {
                val recent = repository.getRecentFiles(limit = 6)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    try {
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
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error displaying recent files", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching recent files", e)
            }
        }
    }
}
