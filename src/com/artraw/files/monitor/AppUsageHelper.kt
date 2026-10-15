package com.artraw.files.monitor

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val totalTimeMillis: Long,
    val formattedDuration: String,
    val percentOfTotal: Int
)

data class AppUsageSnapshot(
    val hasPermission: Boolean,
    val totalTimeMillis: Long,
    val formattedTotalTime: String,
    val appCount: Int,
    val apps: List<AppUsageInfo>
)

object AppUsageHelper {

    fun hasUsagePermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    fun getTodayUsage(context: Context): AppUsageSnapshot {
        if (!hasUsagePermission(context)) {
            return AppUsageSnapshot(
                hasPermission = false,
                totalTimeMillis = 0L,
                formattedTotalTime = "Permission Required",
                appCount = 0,
                apps = emptyList()
            )
        }

        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return AppUsageSnapshot(false, 0L, "Unavailable", 0, emptyList())

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startMillis = cal.timeInMillis
            val endMillis = System.currentTimeMillis()

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMillis, endMillis)
            if (stats.isNullOrEmpty()) {
                return AppUsageSnapshot(true, 0L, "0 min (No recent usage logged)", 0, emptyList())
            }

            val pm = context.packageManager
            val aggregated = mutableMapOf<String, Long>()

            for (u in stats) {
                if (u.totalTimeInForeground > 30000L) { // minimum 30 seconds
                    val cur = aggregated.getOrDefault(u.packageName, 0L)
                    aggregated[u.packageName] = Math.max(cur, u.totalTimeInForeground)
                }
            }

            val totalTime = aggregated.values.sum()
            val list = mutableListOf<AppUsageInfo>()

            for ((pkg, duration) in aggregated) {
                val appName = try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Throwable) {
                    pkg.substringAfterLast(".")
                }

                val icon = try {
                    pm.getApplicationIcon(pkg)
                } catch (_: Throwable) {
                    null
                }

                val percent = if (totalTime > 0) ((duration.toFloat() / totalTime) * 100).toInt().coerceIn(0, 100) else 0

                list.add(
                    AppUsageInfo(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        totalTimeMillis = duration,
                        formattedDuration = formatDuration(duration),
                        percentOfTotal = percent
                    )
                )
            }

            list.sortByDescending { it.totalTimeMillis }

            AppUsageSnapshot(
                hasPermission = true,
                totalTimeMillis = totalTime,
                formattedTotalTime = formatDuration(totalTime),
                appCount = list.size,
                apps = list
            )
        } catch (_: Throwable) {
            AppUsageSnapshot(false, 0L, "Error querying stats", 0, emptyList())
        }
    }

    fun formatDuration(millis: Long): String {
        val totalMinutes = millis / (1000 * 60)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> "< 1m"
        }
    }
}
