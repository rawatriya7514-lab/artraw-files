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
        val minDimen = (24 * res.displayMetrics.density).toInt()
        return if (dimenHeight > 0) dimenHeight else minDimen
    }

    fun getNavigationBarHeight(context: Context): Int {
        val res = context.resources
        val resId = res.getIdentifier("navigation_bar_height", "dimen", "android")
        val dimenHeight = if (resId > 0) res.getDimensionPixelSize(resId) else 0
        val minDimen = (16 * res.displayMetrics.density).toInt()
        return if (dimenHeight > 0) dimenHeight else minDimen
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

        // 3. Match system-bar icon contrast to the resolved theme (light or dark)
        val isDark = (activity.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appearance = if (isDark) {
                0
            } else {
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            }
            window.insetsController?.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = if (isDark) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = if (isDark) {
                    flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            }
            window.decorView.systemUiVisibility = flags
        }

        val safeTopSpacers = topSpacers.filterNotNull()
        val safeTopHeaders = topHeaders.filterNotNull()
        val safeBottomScrollViews = bottomScrollViews.filterNotNull()
        val safeBottomStickyViews = bottomStickyViews.filterNotNull()

        try {
            // Ensure scroll views don't clip to padding so scrolling looks edge-to-edge
            for (view in safeBottomScrollViews) {
                if (view is ViewGroup) {
                    view.clipToPadding = false
                }
            }

            // Save original initial paddings to avoid compounding on repeated insets calls
            val topInitialPaddings = safeTopHeaders.map {
                Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
            }
            val bottomScrollPaddings = safeBottomScrollViews.map {
                Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
            }
            val bottomStickyPaddings = safeBottomStickyViews.map {
                Triple(it.paddingLeft, it.paddingTop, it.paddingRight) to it.paddingBottom
            }

            val baseStatusBarHeight = getStatusBarHeight(activity)
            val baseNavBarHeight = getNavigationBarHeight(activity)
            var lastStatusBar = baseStatusBarHeight
            var lastNavBar = baseNavBarHeight

            fun applyInsets(statusBarInset: Int, navBarInset: Int) {
                lastStatusBar = statusBarInset
                lastNavBar = navBarInset
                val effectiveStatusBar = if (statusBarInset > 0) statusBarInset else baseStatusBarHeight
                val effectiveNavBar = if (navBarInset > 0) navBarInset else baseNavBarHeight

                // Adjust status bar spacers
                for (spacer in safeTopSpacers) {
                    val params = spacer.layoutParams
                    if (params != null && params.height != effectiveStatusBar) {
                        params.height = effectiveStatusBar
                        spacer.layoutParams = params
                    }
                }

                // Adjust headers padding directly
                for ((index, header) in safeTopHeaders.withIndex()) {
                    if (index < topInitialPaddings.size) {
                        val (left, top, right) = topInitialPaddings[index].first
                        val bottom = topInitialPaddings[index].second
                        val targetTop = kotlin.math.max(top, effectiveStatusBar)
                        header.setPadding(left, targetTop, right, bottom)
                    }
                }

                // Apply navigation bar bottom padding to scroll views
                for ((index, scroll) in safeBottomScrollViews.withIndex()) {
                    if (index < bottomScrollPaddings.size) {
                        val (left, top, right) = bottomScrollPaddings[index].first
                        val bottom = bottomScrollPaddings[index].second
                        scroll.setPadding(left, top, right, bottom + effectiveNavBar)
                    }
                }

                // Apply navigation bar bottom padding to sticky bottom bars
                // Only the lowest VISIBLE sticky view receives the extra navBar padding!
                val lastVisibleSticky = safeBottomStickyViews.lastOrNull { it.visibility == View.VISIBLE }
                for ((index, sticky) in safeBottomStickyViews.withIndex()) {
                    if (index < bottomStickyPaddings.size) {
                        val (left, top, right) = bottomStickyPaddings[index].first
                        val bottom = bottomStickyPaddings[index].second
                        val extra = if (sticky === lastVisibleSticky) effectiveNavBar else 0
                        val targetBottom = bottom + extra
                        if (sticky.paddingLeft != left || sticky.paddingTop != top ||
                            sticky.paddingRight != right || sticky.paddingBottom != targetBottom) {
                            sticky.setPadding(left, top, right, targetBottom)
                        }
                    }
                }
            }

            // Apply immediately so first frame is never clipped
            applyInsets(baseStatusBarHeight, baseNavBarHeight)

            // Listen for visibility / size changes on sticky bars (e.g. ad loaded or hidden)
            for (sticky in safeBottomStickyViews) {
                sticky.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    applyInsets(lastStatusBar, lastNavBar)
                }
            }

            // 4. Listen for WindowInsets when system reports exact insets
            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                try {
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
                } catch (_: Throwable) {}
                insets
            }

            window.decorView.requestApplyInsets()
        } catch (_: Throwable) {}
    }
}
