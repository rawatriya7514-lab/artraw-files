package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.monitor.AppUsageHelper
import com.artraw.files.monitor.DeviceHardwareHelper
import com.artraw.files.monitor.ThermalMonitorManager
import com.artraw.files.ui.view.ThermalGraphView
import com.artraw.files.ui.view.UsageGraphView
import com.artraw.files.util.Formatters

class DeviceMonitorActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isUpdating = false

    // Tabs
    private lateinit var btnTabCpu: Button
    private lateinit var btnTabThermal: Button
    private lateinit var btnTabUsage: Button
    private lateinit var btnTabSpecs: Button

    // Content layouts
    private lateinit var layoutCpuContent: LinearLayout
    private lateinit var layoutThermalContent: LinearLayout
    private lateinit var layoutUsageContent: LinearLayout
    private lateinit var layoutSpecsContent: LinearLayout

    // CPU Views
    private lateinit var tvProcessorName: TextView
    private lateinit var tvCpuArchitecture: TextView
    private lateinit var containerCpuCores: LinearLayout
    private lateinit var tvRamPercent: TextView
    private lateinit var progressRam: ProgressBar
    private lateinit var tvRamDetails: TextView

    // Thermal Views
    private lateinit var tvCurrentThermalTemp: TextView
    private lateinit var tvThermalStatus: TextView
    private lateinit var tvBatteryDetails: TextView
    private lateinit var tvPeakHeatSummary: TextView
    private lateinit var thermalGraphView: ThermalGraphView
    private lateinit var containerHeatEvents: LinearLayout

    // Usage Views
    private lateinit var cardUsagePermission: LinearLayout
    private lateinit var btnGrantUsagePerm: Button
    private lateinit var tvTotalScreenTime: TextView
    private lateinit var tvAppsActiveCount: TextView
    private lateinit var usageGraphView: UsageGraphView
    private lateinit var containerAppUsageList: LinearLayout

    // Specs Views
    private lateinit var tvSpecsModel: TextView
    private lateinit var tvSpecsAndroid: TextView
    private lateinit var tvSpecsDisplay: TextView
    private lateinit var tvSpecsKernel: TextView

    private val updateRunnable = object : Runnable {
        override fun run() {
            refreshLiveStats()
            if (isUpdating) {
                mainHandler.postDelayed(this, 1500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_monitor)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerMonitor)),
            bottomScrollViews = listOf(findViewById(R.id.scrollMonitorContent))
        )

        initViews()
        setupTabs()
        populateStaticSpecs()
    }

    override fun onResume() {
        super.onResume()
        isUpdating = true
        mainHandler.post(updateRunnable)
        refreshAppUsage()
    }

    override fun onPause() {
        super.onPause()
        isUpdating = false
        mainHandler.removeCallbacks(updateRunnable)
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.btnMonitorBack).setOnClickListener { finish() }

        btnTabCpu = findViewById(R.id.btnTabCpu)
        btnTabThermal = findViewById(R.id.btnTabThermal)
        btnTabUsage = findViewById(R.id.btnTabUsage)
        btnTabSpecs = findViewById(R.id.btnTabSpecs)

        layoutCpuContent = findViewById(R.id.layoutCpuContent)
        layoutThermalContent = findViewById(R.id.layoutThermalContent)
        layoutUsageContent = findViewById(R.id.layoutUsageContent)
        layoutSpecsContent = findViewById(R.id.layoutSpecsContent)

        // CPU
        tvProcessorName = findViewById(R.id.tvProcessorName)
        tvCpuArchitecture = findViewById(R.id.tvCpuArchitecture)
        containerCpuCores = findViewById(R.id.containerCpuCores)
        tvRamPercent = findViewById(R.id.tvRamPercent)
        progressRam = findViewById(R.id.progressRam)
        tvRamDetails = findViewById(R.id.tvRamDetails)

        // Thermal
        tvCurrentThermalTemp = findViewById(R.id.tvCurrentThermalTemp)
        tvThermalStatus = findViewById(R.id.tvThermalStatus)
        tvBatteryDetails = findViewById(R.id.tvBatteryDetails)
        tvPeakHeatSummary = findViewById(R.id.tvPeakHeatSummary)
        thermalGraphView = findViewById(R.id.thermalGraphView)
        containerHeatEvents = findViewById(R.id.containerHeatEvents)

        // Usage
        cardUsagePermission = findViewById(R.id.cardUsagePermission)
        btnGrantUsagePerm = findViewById(R.id.btnGrantUsagePerm)
        tvTotalScreenTime = findViewById(R.id.tvTotalScreenTime)
        tvAppsActiveCount = findViewById(R.id.tvAppsActiveCount)
        usageGraphView = findViewById(R.id.usageGraphView)
        containerAppUsageList = findViewById(R.id.containerAppUsageList)

        btnGrantUsagePerm.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        // Specs
        tvSpecsModel = findViewById(R.id.tvSpecsModel)
        tvSpecsAndroid = findViewById(R.id.tvSpecsAndroid)
        tvSpecsDisplay = findViewById(R.id.tvSpecsDisplay)
        tvSpecsKernel = findViewById(R.id.tvSpecsKernel)
    }

    private fun setupTabs() {
        btnTabCpu.setOnClickListener { switchTab(0) }
        btnTabThermal.setOnClickListener { switchTab(1) }
        btnTabUsage.setOnClickListener { switchTab(2) }
        btnTabSpecs.setOnClickListener { switchTab(3) }
    }

    private fun switchTab(tabIndex: Int) {
        val buttons = listOf(btnTabCpu, btnTabThermal, btnTabUsage, btnTabSpecs)
        val layouts = listOf(layoutCpuContent, layoutThermalContent, layoutUsageContent, layoutSpecsContent)

        for (i in buttons.indices) {
            if (i == tabIndex) {
                buttons[i].setBackgroundResource(R.drawable.bg_button_primary)
                buttons[i].setTextColor(Color.WHITE)
                layouts[i].visibility = View.VISIBLE
            } else {
                buttons[i].setBackgroundResource(R.drawable.bg_button_outlined)
                buttons[i].setTextColor(getColor(R.color.text_secondary_dark))
                layouts[i].visibility = View.GONE
            }
        }

        if (tabIndex == 2) {
            refreshAppUsage()
        }
    }

    private fun populateStaticSpecs() {
        val specs = DeviceHardwareHelper.getDeviceSpecs(this)
        tvSpecsModel.text = "Device: ${specs.manufacturer} ${specs.model} (${specs.brand})"
        tvSpecsAndroid.text = "${specs.androidVersion} (API Level ${specs.apiLevel})"
        tvSpecsDisplay.text = "Display: ${specs.displayResolution} @ ${specs.displayRefreshRate.toInt()}Hz (${specs.displayDpi} DPI)"
        tvSpecsKernel.text = "Kernel: ${specs.kernelVersion} • Board: ${specs.board}"
    }

    private fun refreshLiveStats() {
        // 1. CPU & RAM
        val cpu = DeviceHardwareHelper.getCpuSnapshot()
        tvProcessorName.text = cpu.processorName
        tvCpuArchitecture.text = "${cpu.coreCount} Cores • ${cpu.architecture} • ${cpu.governor}"

        // Populate / Update Core views
        if (containerCpuCores.childCount != cpu.cores.size) {
            containerCpuCores.removeAllViews()
            for (c in cpu.cores) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (32 * resources.displayMetrics.density).toInt()
                    )
                }

                val title = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                    text = "Core ${c.coreIndex}:"
                    setTextColor(getColor(R.color.text_secondary_dark))
                    textSize = 12f
                }

                val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    layoutParams = LinearLayout.LayoutParams(0, (6 * resources.displayMetrics.density).toInt(), 2.0f).apply {
                        setMargins(8, 12, 8, 0)
                    }
                    max = 100
                    progress = c.percent
                    progressTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.artraw_crimson_accent))
                }

                val freq = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                    text = if (c.currentFreqMhz > 0) "${c.currentFreqMhz} MHz" else "Sleeping"
                    setTextColor(getColor(R.color.text_primary_dark))
                    textSize = 12f
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_END
                }

                row.addView(title)
                row.addView(bar)
                row.addView(freq)
                containerCpuCores.addView(row)
            }
        } else {
            for (i in cpu.cores.indices) {
                val c = cpu.cores[i]
                val row = containerCpuCores.getChildAt(i) as? LinearLayout ?: continue
                val bar = row.getChildAt(1) as? ProgressBar
                val freq = row.getChildAt(2) as? TextView
                bar?.progress = c.percent
                freq?.text = if (c.currentFreqMhz > 0) "${c.currentFreqMhz} MHz" else "Sleeping"
            }
        }

        // RAM
        val ram = DeviceHardwareHelper.getRamSnapshot(this)
        tvRamPercent.text = "${ram.usedPercent}% Used"
        progressRam.progress = ram.usedPercent
        val usedGb = Formatters.formatFileSize(ram.usedBytes)
        val totalGb = Formatters.formatFileSize(ram.totalBytes)
        val freeGb = Formatters.formatFileSize(ram.availableBytes)
        tvRamDetails.text = "Used: $usedGb / Total: $totalGb ($freeGb Free)"

        // 2. Thermal & Battery
        val thermal = ThermalMonitorManager.getThermalSnapshot(this)
        tvCurrentThermalTemp.text = "${String.format("%.1f", thermal.currentTempCelsius)}°C"

        val tempColor = when {
            thermal.currentTempCelsius >= 44.0f -> Color.parseColor("#FF3366")
            thermal.currentTempCelsius >= 38.0f -> Color.parseColor("#FFCC00")
            else -> getColor(R.color.status_success)
        }
        tvCurrentThermalTemp.setTextColor(tempColor)

        val severityDesc = when {
            thermal.currentTempCelsius >= 46.0f -> "Critical Heat ⚠"
            thermal.currentTempCelsius >= 42.0f -> "Warm / Heavy Load"
            else -> "Optimal / Cool ✓"
        }
        tvThermalStatus.text = "$severityDesc • Health: ${thermal.health} • ${thermal.chargingStatus}"
        tvBatteryDetails.text = "Battery: ${thermal.batteryPercent}% • ${thermal.voltageMv} mV • ${thermal.technology}"
        tvPeakHeatSummary.text = "Peak Heat Recorded: ${String.format("%.1f", thermal.peakTempCelsius)}°C at ${thermal.peakTimeLabel}"

        thermalGraphView.setPoints(thermal.history)

        // Populate Heat Spike Events
        containerHeatEvents.removeAllViews()
        if (thermal.heatEvents.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "No severe overheating recorded. Device operating within cool thresholds."
                setTextColor(getColor(R.color.text_secondary_dark))
                textSize = 12f
            }
            containerHeatEvents.addView(emptyTv)
        } else {
            for (evt in thermal.heatEvents.takeLast(5).reversed()) {
                val row = TextView(this).apply {
                    text = "• ${evt.timeLabel}: ${String.format("%.1f", evt.peakTemp)}°C (${evt.severity})"
                    setTextColor(if (evt.peakTemp >= 44.0f) Color.parseColor("#FF3366") else Color.parseColor("#FFCC00"))
                    textSize = 12f
                    setPadding(0, 4, 0, 4)
                }
                containerHeatEvents.addView(row)
            }
        }
    }

    private fun refreshAppUsage() {
        val usage = AppUsageHelper.getTodayUsage(this)
        if (!usage.hasPermission) {
            cardUsagePermission.visibility = View.VISIBLE
            tvTotalScreenTime.text = "--"
            tvAppsActiveCount.text = "Usage permission not granted"
            usageGraphView.setApps(emptyList())
            containerAppUsageList.removeAllViews()
            return
        }

        cardUsagePermission.visibility = View.GONE
        tvTotalScreenTime.text = usage.formattedTotalTime
        tvAppsActiveCount.text = "${usage.appCount} active apps used today"

        usageGraphView.setApps(usage.apps)

        // Populate Detailed App List
        containerAppUsageList.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (app in usage.apps.take(15)) {
            val row = inflater.inflate(R.layout.item_file_list, containerAppUsageList, false)
            val icon = row.findViewById<ImageView>(R.id.imgFileIcon)
            val name = row.findViewById<TextView>(R.id.tvFileName)
            val meta = row.findViewById<TextView>(R.id.tvFileMeta)

            name.text = app.appName
            meta.text = "${app.formattedDuration} (${app.percentOfTotal}% of screen time)"
            if (app.icon != null) {
                icon.setImageDrawable(app.icon)
            } else {
                icon.setImageResource(R.drawable.ic_apk)
            }
            containerAppUsageList.addView(row)
        }
    }
}
