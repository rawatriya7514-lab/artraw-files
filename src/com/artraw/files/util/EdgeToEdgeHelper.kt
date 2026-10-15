package com.artraw.files.util

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController

object EdgeToEdgeHelper {

    fun getStatusBarHeight(context: Context): Int {
        val res = context.resources
        val resId = res.getIdentifier("status_bar_height", "dimen", "android")
        val dimenHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        val minDimen = (36 * res.displayMetrics.density).toInt()
        return kotlin.math.max(dimenHeight, minDimen)
    }

    fun getNavigationBarHeight(context: Context): Int {
        val res = context.resources
        val resId = res.getIdentifier("navigation_bar_height", "dimen", "android")
        val dimenHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        val minDimen = (24 * res.displayMetrics.density).toInt()
        return kotlin.math.max(dimenHeight, minDimen)
    }

    fun applyEdgeToEdge(
        activity: Activity,
        topSpacers: List<View> = emptyList(),
        topHeaders: List<View> = emptyList(),
        bottomScrollViews: List<View> = emptyList(),
        bottomStickyViews: List<View> = emptyList()
    ) {
        val window = activity.window

        // 1. Enable edge-to-edge layout behind system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }

        // 2. Make status bar and navigation bar transparent
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Disable artificial contrast scrim on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        // 3. Ensure Status Bar and Navigation Bar icons remain light/white on dark backgrounds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        }

        // Ensure scroll views don't clip to padding so scrolling looks edge-to-edge
        for (view in bottomScrollViews) {
            if (view is ViewGroup) {
                view.clipToPadding = false
            }
        }

        // Save original initial paddings to avoid compounding on repeated insets calls
        val topInitialPaddings = topHeaders.map {
            Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
        }
        val bottomScrollPaddings = bottomScrollViews.map {
            Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
        }
        val bottomStickyPaddings = bottomStickyViews.map {
            Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
        }

        val baseStatusBarHeight = getStatusBarHeight(activity)
        val baseNavBarHeight = getNavigationBarHeight(activity)
        val extraTopGap = (16 * activity.resources.displayMetrics.density).toInt()

        fun applyInsets(statusBarInset: Int, navBarInset: Int) {
            val effectiveStatusBar = kotlin.math.max(statusBarInset, baseStatusBarHeight) + extraTopGap
            val effectiveNavBar = kotlin.math.max(navBarInset, baseNavBarHeight)

            // Adjust status bar spacers
            for (spacer in topSpacers) {
                val params = spacer.layoutParams
                if (params != null && params.height != effectiveStatusBar) {
                    params.height = effectiveStatusBar
                    spacer.layoutParams = params
                }
            }

            // Adjust headers padding directly
            for ((index, header) in topHeaders.withIndex()) {
                val (left, top, right) = topInitialPaddings[index].first
                val bottom = topInitialPaddings[index].second
                val targetTop = kotlin.math.max(top, effectiveStatusBar)
                header.setPadding(left, targetTop, right, bottom)
            }

            // Apply navigation bar bottom padding to scroll views
            for ((index, scroll) in bottomScrollViews.withIndex()) {
                val (left, top, right) = bottomScrollPaddings[index].first
                val bottom = bottomScrollPaddings[index].second
                scroll.setPadding(left, top, right, bottom + effectiveNavBar)
            }

            // Apply navigation bar bottom padding to sticky bottom bars
            for ((index, sticky) in bottomStickyViews.withIndex()) {
                val (left, top, right) = bottomStickyPaddings[index].first
                val bottom = bottomStickyPaddings[index].second
                sticky.setPadding(left, top, right, bottom + effectiveNavBar)
            }
        }

        // Apply immediately so first frame is never clipped
        applyInsets(baseStatusBarHeight, baseNavBarHeight)

        // 4. Listen for WindowInsets when system reports exact insets
        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            val statusBarInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }

            val navBarInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }

            applyInsets(statusBarInset, navBarInset)
            insets
        }

        window.decorView.requestApplyInsets()
    }
}
