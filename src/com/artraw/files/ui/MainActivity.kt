package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.ad.AdMobManager
import com.artraw.files.model.FileCategory
import com.artraw.files.model.FileItem
import com.artraw.files.preview.FileViewerHelper
import com.artraw.files.repository.FileRepository
import com.artraw.files.util.AppSecurity
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.Formatters
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager
import com.artraw.files.util.UpdateChecker
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

        // Apply saved theme before setContentView
        val themeRes = if (settingsManager.themeMode == "light") {
            R.style.Theme_ArtrawFiles_Light
        } else {
            R.style.Theme_ArtrawFiles
        }
        setTheme(themeRes)

        if (!AppSecurity.verifyIntegrity(this)) {
            Log.w("ARTRAW_SECURITY", "Non-standard environment detected; continuing in safe mode.")
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
        setupBottomNavigation()

        // Check storage permission on app launch
        checkPermissionsAndLoad()

        // Check for app updates on startup
        UpdateChecker.checkForUpdates(this)
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
    private val adRetryHandler = Handler(Looper.getMainLooper())
    private var adRetryRunnable: Runnable? = null

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
        loadBanner(resolvedId)
        AdMobManager.loadInterstitial(this)
        AdMobManager.loadRewardedAd(this)
    }

    private fun loadBanner(adUnit: String) {
        runOnUiThread {
            try {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val container = adViewContainer
                container.visibility = View.VISIBLE

                try {
                    adView?.destroy()
                } catch (_: Throwable) {}

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
                        Log.i("ARTRAW_ADMOB", "Production ad loaded successfully! (Unit: $adUnit)")
                        container.visibility = View.VISIBLE
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        Log.w("ARTRAW_ADMOB", "Production ad failed to load (Unit: $adUnit, Code ${loadAdError.code}): ${loadAdError.message}")

                        adRetryRunnable?.let { adRetryHandler.removeCallbacks(it) }
                        adRetryRunnable = Runnable {
                            if (!isFinishing && !isDestroyed) {
                                Log.d("ARTRAW_ADMOB", "Retrying production banner load...")
                                loadBanner(adUnit)
                            }
                        }
                        adRetryHandler.postDelayed(adRetryRunnable!!, 30000)
                    }
                }

                val request = AdRequest.Builder().build()
                ad.loadAd(request)
            } catch (t: Throwable) {
                Log.e("ARTRAW_ADMOB", "loadBanner error: ${t.message}", t)
            }
        }
    }

    private fun setupListeners() {
        btnGrantPermission.setOnClickListener {
            PermissionsHelper.showPermissionJustification(this) {
                PermissionsHelper.requestStoragePermission(this, 1001)
            }
        }

        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<View>(R.id.cardStorage).setOnClickListener {
            openBrowser(repository.getPrimaryStorageRoot().absolutePath)
        }

        findViewById<View>(R.id.cardDeviceMonitor)?.setOnClickListener {
            startActivity(Intent(this, DeviceMonitorActivity::class.java))
        }

        findViewById<View>(R.id.chipDeviceMonitor)?.setOnClickListener {
            startActivity(Intent(this, DeviceMonitorActivity::class.java))
        }

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
                                icon.setColorFilter(null)
                                if (item.category == FileCategory.IMAGES || item.category == FileCategory.VIDEOS || item.isApk) {
                                    com.artraw.files.util.ThumbnailLoader.loadThumbnail(icon, item, targetSize = 96, defaultIconRes = iconRes)
                                }

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

    private fun setupBottomNavigation() {
        val navItems = listOf(
            R.id.btnNavHome to R.id.tvNavHome,
            R.id.btnNavBrowse to R.id.tvNavBrowse,
            R.id.btnNavAnalysis to R.id.tvNavAnalysis,
            R.id.btnNavSettings to R.id.tvNavSettings
        )

        fun setActive(index: Int) {
            navItems.forEachIndexed { i, (btnId, tvId) ->
                val isActive = i == index
                findViewById<LinearLayout>(btnId).let {
                    for (childIndex in 0 until it.childCount) {
                        val child = it.getChildAt(childIndex)
                        if (child is ImageView) {
                            child.setImageTintList(
                                android.content.res.ColorStateList.valueOf(
                                    if (isActive) resources.getColor(R.color.aurora_primary, null) else resources.getColor(R.color.aurora_text_secondary, null)
                                )
                            )
                        }
                        if (child is TextView) {
                            child.setTextColor(
                                if (isActive) resources.getColor(R.color.aurora_primary, null) else resources.getColor(R.color.aurora_text_secondary, null)
                            )
                        }
                    }
                }
            }
        }

        setActive(0)

        findViewById<LinearLayout>(R.id.btnNavHome).setOnClickListener {
            if (navItems.indexOfFirst { it.first == R.id.btnNavHome } != 0) setActive(0)
        }

        findViewById<LinearLayout>(R.id.btnNavBrowse).setOnClickListener {
            startActivity(Intent(this, BrowserActivity::class.java).apply {
                putExtra("initial_path", repository.getPrimaryStorageRoot().absolutePath)
            })
        }

        findViewById<LinearLayout>(R.id.btnNavAnalysis).setOnClickListener {
            startActivity(Intent(this, AnalyzerActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnNavSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            adView?.resume()
        } catch (_: Throwable) {}
        checkPermissionsAndLoad()
        updateDeviceMonitorPreview()
    }

    private fun updateDeviceMonitorPreview() {
        try {
            val thermal = com.artraw.files.monitor.ThermalMonitorManager.getThermalSnapshot(this)
            val cpu = com.artraw.files.monitor.DeviceHardwareHelper.getCpuSnapshot()
            findViewById<TextView>(R.id.tvMainLiveTempBadge)?.text = "${String.format("%.1f", thermal.currentTempCelsius)}°C"
            findViewById<TextView>(R.id.tvMainCpuSummary)?.text = "${cpu.coreCount} Cores • ${cpu.governor}"
            val hasUsage = com.artraw.files.monitor.AppUsageHelper.hasUsagePermission(this)
            findViewById<TextView>(R.id.tvMainUsageSummary)?.text = if (hasUsage) "Live Timeline →" else "Tap to Grant →"
        } catch (_: Throwable) {}
    }

    override fun onPause() {
        try {
            adView?.pause()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onDestroy() {
        try {
            adRetryRunnable?.let { adRetryHandler.removeCallbacks(it) }
            adView?.destroy()
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkPermissionsAndLoad()
    }
}