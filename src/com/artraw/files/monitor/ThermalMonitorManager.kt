package com.artraw.files.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ThermalPoint(
    val timestamp: Long,
    val tempCelsius: Float,
    val formattedTime: String
)

data class HeatEvent(
    val timestamp: Long,
    val peakTemp: Float,
    val timeLabel: String,
    val severity: String // "Warm", "Hot", "Critical Overheat"
)

data class ThermalSnapshot(
    val currentTempCelsius: Float,
    val batteryPercent: Int,
    val voltageMv: Int,
    val health: String,
    val chargingStatus: String,
    val technology: String,
    val peakTempCelsius: Float,
    val peakTimeLabel: String,
    val history: List<ThermalPoint>,
    val heatEvents: List<HeatEvent>
)

object ThermalMonitorManager {

    private const val LOG_FILE_NAME = "thermal_history.json"
    private const val MAX_HISTORY_POINTS = 60
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val fullDateTimeFormat = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())

    fun getThermalSnapshot(context: Context): ThermalSnapshot {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val bStatus: Intent? = context.registerReceiver(null, ifilter)

        var tempCelsius = 32.0f
        var level = 50
        var voltage = 3800
        var healthStr = "Good"
        var statusStr = "Discharging"
        var techStr = "Li-ion"

        if (bStatus != null) {
            val rawTemp = bStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320)
            tempCelsius = rawTemp / 10.0f

            val bLevel = bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val bScale = bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level = if (bLevel >= 0 && bScale > 0) ((bLevel.toFloat() / bScale) * 100).toInt() else 50

            voltage = bStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3800)
            techStr = bStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"

            healthStr = when (bStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)) {
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated ⚠"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                else -> "Good ✓"
            }

            statusStr = when (bStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging ⚡"
                BatteryManager.BATTERY_STATUS_FULL -> "Full 100%"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Discharging"
            }
        }

        // Record current reading into persistent history
        val now = System.currentTimeMillis()
        recordThermalPoint(context, now, tempCelsius)

        val history = loadHistory(context)
        val peakPoint = history.maxByOrNull { it.tempCelsius }
        val peakTemp = peakPoint?.tempCelsius ?: tempCelsius
        val peakTime = peakPoint?.let { fullDateTimeFormat.format(Date(it.timestamp)) } ?: fullDateTimeFormat.format(Date(now))

        val heatEvents = mutableListOf<HeatEvent>()
        for (p in history) {
            if (p.tempCelsius >= 40.0f) {
                val severity = when {
                    p.tempCelsius >= 47.0f -> "Critical Overheat"
                    p.tempCelsius >= 43.0f -> "High Heat"
                    else -> "Warm"
                }
                heatEvents.add(
                    HeatEvent(
                        timestamp = p.timestamp,
                        peakTemp = p.tempCelsius,
                        timeLabel = fullDateTimeFormat.format(Date(p.timestamp)),
                        severity = severity
                    )
                )
            }
        }

        return ThermalSnapshot(
            currentTempCelsius = tempCelsius,
            batteryPercent = level,
            voltageMv = voltage,
            health = healthStr,
            chargingStatus = statusStr,
            technology = techStr,
            peakTempCelsius = peakTemp,
            peakTimeLabel = peakTime,
            history = history,
            heatEvents = heatEvents
        )
    }

    @Synchronized
    private fun recordThermalPoint(context: Context, timestamp: Long, temp: Float) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            val jsonArray = if (file.exists()) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) JSONArray(text) else JSONArray()
            } else {
                JSONArray()
            }

            // Avoid recording duplicate points within 20 seconds unless temperature changed significantly
            if (jsonArray.length() > 0) {
                val lastObj = jsonArray.getJSONObject(jsonArray.length() - 1)
                val lastTime = lastObj.optLong("time", 0L)
                val lastTemp = lastObj.optDouble("temp", 0.0).toFloat()
                if (timestamp - lastTime < 20000 && Math.abs(temp - lastTemp) < 0.2f) {
                    return
                }
            }

            val obj = JSONObject().apply {
                put("time", timestamp)
                put("temp", temp.toDouble())
            }
            jsonArray.put(obj)

            // Trim to maximum allowed history
            val trimmed = if (jsonArray.length() > MAX_HISTORY_POINTS) {
                val newArr = JSONArray()
                val start = jsonArray.length() - MAX_HISTORY_POINTS
                for (i in start until jsonArray.length()) {
                    newArr.put(jsonArray.get(i))
                }
                newArr
            } else {
                jsonArray
            }

            file.writeText(trimmed.toString())
        } catch (_: Throwable) {}
    }

    @Synchronized
    private fun loadHistory(context: Context): List<ThermalPoint> {
        val list = mutableListOf<ThermalPoint>()
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                val text = file.readText().trim()
                if (text.isNotEmpty()) {
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val t = obj.optLong("time")
                        val temp = obj.optDouble("temp").toFloat()
                        list.add(ThermalPoint(timestamp = t, tempCelsius = temp, formattedTime = timeFormat.format(Date(t))))
                    }
                }
            }
        } catch (_: Throwable) {}

        // If history is empty, populate with sensible initial baseline
        if (list.isEmpty()) {
            val now = System.currentTimeMillis()
            for (i in 10 downTo 0) {
                val t = now - (i * 60000L)
                list.add(ThermalPoint(timestamp = t, tempCelsius = 31.0f + (i % 3) * 0.5f, formattedTime = timeFormat.format(Date(t))))
            }
        }

        return list
    }
}
