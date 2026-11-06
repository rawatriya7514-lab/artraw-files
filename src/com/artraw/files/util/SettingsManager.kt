package com.artraw.files.util

import android.content.Context
import android.content.SharedPreferences
import com.artraw.files.model.SortDirection
import com.artraw.files.model.SortField
import com.artraw.files.model.SortOption

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("artraw_files_prefs", Context.MODE_PRIVATE)

    /** "system" (default), "dark" or "light". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(value) = prefs.edit().putString("theme_mode", value).apply()

    /** Master switch for non-essential UI motion. */
    var animationsEnabled: Boolean
        get() = prefs.getBoolean("animations_enabled", true)
        set(value) = prefs.edit().putBoolean("animations_enabled", value).apply()

    /** Last used grid column count (1-5). */
    var gridColumns: Int
        get() = prefs.getInt("grid_columns", 3).coerceIn(1, 5)
        set(value) = prefs.edit().putInt("grid_columns", value.coerceIn(1, 5)).apply()

    var language: String
        get() = prefs.getString("language", "en") ?: "en"
        set(value) = prefs.edit().putString("language", value).apply()

    var isGridView: Boolean
        get() = prefs.getBoolean("is_grid_view", false)
        set(value) = prefs.edit().putBoolean("is_grid_view", value).apply()

    var showHiddenFiles: Boolean
        get() = prefs.getBoolean("show_hidden_files", false)
        set(value) = prefs.edit().putBoolean("show_hidden_files", value).apply()

    var showFileExtensions: Boolean
        get() = prefs.getBoolean("show_file_extensions", true)
        set(value) = prefs.edit().putBoolean("show_file_extensions", value).apply()

    var sortField: SortField
        get() {
            val name = prefs.getString("sort_field", SortField.NAME.name) ?: SortField.NAME.name
            return try { SortField.valueOf(name) } catch (e: Exception) { SortField.NAME }
        }
        set(value) = prefs.edit().putString("sort_field", value.name).apply()

    var sortDirection: SortDirection
        get() {
            val name = prefs.getString("sort_dir", SortDirection.ASCENDING.name) ?: SortDirection.ASCENDING.name
            return try { SortDirection.valueOf(name) } catch (e: Exception) { SortDirection.ASCENDING }
        }
        set(value) = prefs.edit().putString("sort_dir", value.name).apply()

    fun getSortOption(): SortOption = SortOption(sortField, sortDirection)

    var isOnboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()
}
