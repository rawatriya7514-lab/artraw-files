package com.artraw.files.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.RandomAccessFile

data class CpuCoreInfo(
    val coreIndex: Int,
    val currentFreqKHz: Long,
    val minFreqKHz: Long,
    val maxFreqKHz: Long
) {
    val currentFreqMhz: Long get() = currentFreqKHz / 1000
    val maxFreqMhz: Long get() = maxFreqKHz / 1000
    val percent: Int get() = if (maxFreqKHz > 0) ((currentFreqKHz.toFloat() / maxFreqKHz) * 100).toInt().coerceIn(0, 100) else 0
}

data class CpuSnapshot(
    val processorName: String,
    val architecture: String,
    val coreCount: Int,
    val governor: String,
    val cores: List<CpuCoreInfo>
)

data class RamSnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val usedPercent: Int,
    val isLowMemory: Boolean
)

data class DeviceSpecsSnapshot(
    val model: String,
    val manufacturer: String,
    val brand: String,
    val board: String,
    val androidVersion: String,
    val apiLevel: Int,
    val kernelVersion: String,
    val displayResolution: String,
    val displayRefreshRate: Float,
    val displayDpi: Int
)

object DeviceHardwareHelper {

    fun getCpuSnapshot(): CpuSnapshot {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = mutableListOf<CpuCoreInfo>()

        for (i in 0 until coreCount) {
            val cur = readLongFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val min = readLongFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            val max = readLongFromFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            cores.add(CpuCoreInfo(coreIndex = i, currentFreqKHz = cur, minFreqKHz = min, maxFreqKHz = max))
        }

        val governor = readStringFromFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").ifEmpty { "schedutil" }
        val cpuName = parseCpuName().ifEmpty { getBuildField("HARDWARE", "ARMv8") }
        val arch = System.getProperty("os.arch") ?: getFirstSupportedAbi()

        return CpuSnapshot(
            processorName = cpuName,
            architecture = arch,
            coreCount = coreCount,
            governor = governor,
            cores = cores
        )
    }

    fun getRamSnapshot(context: Context): RamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val total = memInfo.totalMem
        val avail = memInfo.availMem
        val used = (total - avail).coerceAtLeast(0)
        val percent = if (total > 0) ((used.toFloat() / total) * 100).toInt().coerceIn(0, 100) else 0

        return RamSnapshot(
            totalBytes = total,
            availableBytes = avail,
            usedBytes = used,
            usedPercent = percent,
            isLowMemory = memInfo.lowMemory
        )
    }

    fun getDeviceSpecs(context: Context): DeviceSpecsSnapshot {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)

        @Suppress("DEPRECATION")
        val refreshRate = wm.defaultDisplay.refreshRate
        val resolution = "${dm.widthPixels} x ${dm.heightPixels}"
        val kernel = System.getProperty("os.version") ?: "Linux"

        val model = getBuildField("MODEL", "Android Device")
        val manufacturer = getBuildField("MANUFACTURER", "Generic").replaceFirstChar { it.uppercase() }
        val brand = getBuildField("BRAND", "Generic").replaceFirstChar { it.uppercase() }
        val board = getBuildField("BOARD", "Generic")
        val release = getBuildVersionRelease()
        val sdk = getBuildVersionSdk()

        return DeviceSpecsSnapshot(
            model = model,
            manufacturer = manufacturer,
            brand = brand,
            board = board,
            androidVersion = "Android $release",
            apiLevel = sdk,
            kernelVersion = kernel,
            displayResolution = resolution,
            displayRefreshRate = refreshRate,
            displayDpi = dm.densityDpi
        )
    }

    private fun parseCpuName(): String {
        return try {
            val file = File("/proc/cpuinfo")
            if (file.exists()) {
                val lines = file.readLines()
                for (line in lines) {
                    if (line.startsWith("Hardware", ignoreCase = true) || line.startsWith("model name", ignoreCase = true)) {
                        val parts = line.split(":")
                        if (parts.size >= 2) {
                            return parts[1].trim()
                        }
                    }
                }
            }
            getBuildField("HARDWARE", "ARM Processor")
        } catch (_: Throwable) {
            getBuildField("HARDWARE", "ARM Processor")
        }
    }

    private fun getBuildField(fieldName: String, defaultVal: String): String {
        return try {
            val clazz = Class.forName("android.os.Build")
            val field = clazz.getField(fieldName)
            field.get(null)?.toString() ?: defaultVal
        } catch (_: Throwable) {
            defaultVal
        }
    }

    private fun getFirstSupportedAbi(): String {
        return try {
            val clazz = Class.forName("android.os.Build")
            val field = clazz.getField("SUPPORTED_ABIS")
            val abis = field.get(null) as? Array<*>
            abis?.firstOrNull()?.toString() ?: "arm64-v8a"
        } catch (_: Throwable) {
            "arm64-v8a"
        }
    }

    private fun getBuildVersionRelease(): String {
        return try {
            val clazz = Class.forName("android.os.Build\$VERSION")
            clazz.getField("RELEASE").get(null)?.toString() ?: "15"
        } catch (_: Throwable) {
            "15"
        }
    }

    private fun getBuildVersionSdk(): Int {
        return try {
            val clazz = Class.forName("android.os.Build\$VERSION")
            clazz.getField("SDK_INT").getInt(null)
        } catch (_: Throwable) {
            35
        }
    }

    private fun readLongFromFile(path: String): Long {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().toLongOrNull() ?: 0L
            } else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun readStringFromFile(path: String): String {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim()
            } else ""
        } catch (_: Throwable) {
            ""
        }
    }
}
