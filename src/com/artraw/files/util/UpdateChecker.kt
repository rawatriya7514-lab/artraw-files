package com.artraw.files.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * UpdateChecker — Checks for app updates via GitHub Releases API.
 *
 * Fetches the latest release tag from:
 * https://api.github.com/repos/rawatriya7514-lab/artraw-files/releases/latest
 *
 * Compares with current version and shows an update prompt if available.
 */
object UpdateChecker {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/rawatriya7514-lab/artraw-files/releases/latest"

    private const val GITHUB_RELEASES_URL =
        "https://github.com/rawatriya7514-lab/artraw-files/releases"

    /**
     * Current app version (must match build.gradle.kts / AndroidManifest.xml).
     */
    const val CURRENT_VERSION = "1.2.0"
    const val CURRENT_VERSION_CODE = 3

    /**
     * Check for updates on app startup.
     * Shows an AlertDialog if a newer version is available.
     */
    fun checkForUpdates(activity: Activity) {
        val thread = Thread {
            try {
                val latestVersion = fetchLatestVersion()
                if (latestVersion.isNotEmpty() && isNewer(latestVersion, CURRENT_VERSION)) {
                    Handler(Looper.getMainLooper()).post {
                        showUpdateDialog(activity, latestVersion)
                    }
                }
            } catch (e: Exception) {
                // Silently fail — update check is non-critical
                android.util.Log.e("ARTRAW_UPDATE", "Update check failed: ${e.message}")
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
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
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
                // Remove leading 'v' if present (e.g., "v1.3.0" -> "1.3.0")
                tagName.removePrefix("v")
            } else {
                ""
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Compares two version strings (e.g., "1.2.0" vs "1.3.0").
     * Returns true if newVersion is strictly greater than currentVersion.
     */
    private fun isNewer(newVersion: String, currentVersion: String): Boolean {
        val newParts = newVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val currParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }

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
     * Shows an update available dialog with a link to GitHub releases.
     */
    private fun showUpdateDialog(activity: Activity, latestVersion: String) {
        AlertDialog.Builder(activity)
            .setTitle("Update Available")
            .setMessage(
                "A new version of ArtFiles is available!\n\n" +
                "Current: v$CURRENT_VERSION\n" +
                "Latest: v$latestVersion\n\n" +
                "Update to get new features, bug fixes, and improvements."
            )
            .setPositiveButton("Download Update") { _, _ ->
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
                activity.startActivity(browserIntent)
            }
            .setNegativeButton("Remind Me Later", null)
            .setCancelable(false)
            .show()
    }
}
