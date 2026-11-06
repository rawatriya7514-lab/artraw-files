package com.artraw.files.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.artraw.files.R
import com.artraw.files.util.SettingsManager

/**
 * Theme-aware base activity.
 *
 * Theme mode is applied through a configuration override in [attachBaseContext]
 * so that `values/` (light) and `values-night/` (dark) resources resolve
 * correctly for every screen — including drawables and colors resolved in code.
 *
 * Default mode is "system", which follows the device setting.
 */
open class BaseActivity : Activity() {

    protected lateinit var baseSettings: SettingsManager
        private set

    private var lastUiMode: Int = Configuration.UI_MODE_NIGHT_UNDEFINED
    private var appliedMode: String = "system"

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("artraw_files_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getString("theme_mode", "system") ?: "system"
        val baseConfig = newBase.resources.configuration
        val config = Configuration(baseConfig)
        when (mode) {
            "dark" -> config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            "light" -> config.uiMode =
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
            else -> { /* system: leave the configuration untouched */ }
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // One consistent theme for every activity; values-night does the rest.
        setTheme(R.style.Theme_ArtrawFiles)
        super.onCreate(savedInstanceState)
        baseSettings = SettingsManager(this)
        lastUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        appliedMode = baseSettings.themeMode
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the theme in Settings while this activity
        // was paused underneath it; rebuild so the whole stack stays in sync.
        if (::baseSettings.isInitialized && baseSettings.themeMode != appliedMode) {
            appliedMode = baseSettings.themeMode
            recreate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val mode = baseSettings.themeMode
        val current = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (mode == "system" && lastUiMode != Configuration.UI_MODE_NIGHT_UNDEFINED && current != lastUiMode) {
            lastUiMode = current
            recreate()
        }
    }

    protected fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    protected fun motionEnabled(): Boolean = com.artraw.files.util.MotionUtils.enabled(this)
}
