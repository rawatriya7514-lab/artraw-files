package com.artraw.files.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateChecker — Checks for app updates via GitHub Releases API.
 *
 * Dynamically queries the installed package version from PackageManager at runtime
 * so the app never shows false update prompts when on the latest build.
 */
object UpdateChecker {

    private const val TAG = "ARTRAW_UPDATE"
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/rawatriya7514-lab/artraw-files/releases/latest"
    private const val GITHUB_RELEASES_URL =
        "https://github.com/rawatriya7514-lab/artraw-files/releases"

    private const val PREFS_NAME = "artfiles_update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"

    // Fallback constants if PackageManager is unavailable
    const val CURRENT_VERSION = "1.2.1"
    const val CURRENT_VERSION_CODE = 4

    /**
     * Dynamically reads installed app version from PackageManager.
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: CURRENT_VERSION
        } catch (_: Throwable) {
            CURRENT_VERSION
        }
    }

    fun getCurrentVersionCode(context: Context): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                pInfo.versionCode.toLong()
            }
        } catch (_: Throwable) {
            CURRENT_VERSION_CODE.toLong()
        }
    }

    /**
     * Check for updates safely on app startup.
     * Shows a glassmorphic bottom sheet if a newer version is available.
     */
    fun checkForUpdates(
        activity: Activity,
        force: Boolean = false,
        onUpToDate: (() -> Unit)? = null
    ) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        // Rate-limit automated checks to at most once every 6 hours
        if (!force && now - lastCheck < 6 * 3600 * 1000L) {
            return
        }

        val thread = Thread {
            try {
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
                val latestVersion = fetchLatestVersion()
                val currentVer = getCurrentVersion(activity)

                Log.d(TAG, "Update check: Current=$currentVer, Latest=$latestVersion")

                if (latestVersion.isNotEmpty() && isNewer(latestVersion, currentVer)) {
                    val dismissed = prefs.getString(KEY_DISMISSED_VERSION, "")
                    if (!force && dismissed == latestVersion) {
                        Log.d(TAG, "Update $latestVersion already dismissed by user.")
                        return@Thread
                    }

                    Handler(Looper.getMainLooper()).post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showUpdateBottomSheet(activity, latestVersion, currentVer)
                        }
                    }
                } else if (force) {
                    Handler(Looper.getMainLooper()).post {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            if (onUpToDate != null) {
                                onUpToDate.invoke()
                            } else {
                                Toast.makeText(
                                    activity,
                                    "ArtFiles is up to date (v$currentVer) ✓",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.message}")
            }
        }
        thread.isDaemon = true
        thread.start()
    }

    /**
     * Fetches the latest release tag_name from GitHub API.
     */
    private fun fetchLatestVersion(): String {
        val url = URL(GITHUB_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("User-Agent", "ArtFiles-App")

        return try {
            val statusCode = connection.responseCode
            if (statusCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val tagName = json.optString("tag_name", "")
                tagName.removePrefix("v").trim()
            } else {
                ""
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.2.1" vs "1.2.0").
     * Returns true if newVersion is strictly greater than currentVersion.
     */
    fun isNewer(newVersion: String, currentVersion: String): Boolean {
        val cleanNew = newVersion.removePrefix("v").split("-")[0]
        val cleanCurr = currentVersion.removePrefix("v").split("-")[0]

        val newParts = cleanNew.split(".").map { it.toIntOrNull() ?: 0 }
        val currParts = cleanCurr.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLen = maxOf(newParts.size, currParts.size)
        for (i in 0 until maxLen) {
            val newPart = if (i < newParts.size) newParts[i] else 0
            val currPart = if (i < currParts.size) currParts[i] else 0
            if (newPart > currPart) return true
            if (newPart < currPart) return false
        }
        return false // Equal versions
    }

    /**
     * Shows a modern glassmorphic bottom sheet notifying user of new update.
     */
    private fun showUpdateBottomSheet(
        activity: Activity,
        latestVersion: String,
        currentVersion: String
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_update_available, null)
        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            attributes?.windowAnimations = R.style.Animation_ArtrawFiles
        }

        view.findViewById<TextView>(R.id.tvCurrentVersionText)?.text = "v$currentVersion"
        view.findViewById<TextView>(R.id.tvLatestVersionText)?.text = "v$latestVersion"

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        view.findViewById<View>(R.id.btnUpdateSheetClose)?.setOnClickListener {
            prefs.edit().putString(KEY_DISMISSED_VERSION, latestVersion).apply()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnDismissUpdate)?.setOnClickListener {
            prefs.edit().putString(KEY_DISMISSED_VERSION, latestVersion).apply()
            dialog.dismiss()
        }

        view.findViewById<View>(R.id.btnDownloadUpdate)?.setOnClickListener {
            dialog.dismiss()
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
                activity.startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(activity, "Could not open browser", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
