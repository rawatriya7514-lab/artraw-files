package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.SettingsManager

/**
 * Minimalist, silent, high-performance SplashActivity for Artfiles.
 *
 * Designed according to Material Design 3 guidelines:
 * - Completely silent (0 audio playback, 0 video decoder overhead).
 * - Instantaneous tap-to-skip for immediate productivity.
 * - Gentle typography reveal with graceful smooth handoff to MainActivity / OnboardingActivity.
 */
class SplashActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isNavigating = false
    private var navigationRunnable: Runnable? = null
    private var exitFadeRunnable: Runnable? = null

    private var rootLayout: View? = null
    private var layoutBrand: LinearLayout? = null
    private var appName: TextView? = null
    private var appTagline: TextView? = null
    private var footerLayout: LinearLayout? = null

    companion object {
        private const val SPLASH_DISPLAY_DURATION_MS = 650L
        private const val EXIT_FADE_OFFSET_MS = 500L

        // Process-level session state: ensures splash only displays on cold launch
        var hasPlayedSplashThisSession = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. If not task root, the app is already running in background! Resume immediately.
        if (!isTaskRoot) {
            finish()
            return
        }

        // 2. If already launched during this session, skip splash screen instantly.
        if (hasPlayedSplashThisSession) {
            navigateToNextScreen(instant = true)
            return
        }
        hasPlayedSplashThisSession = true

        try {
            val settingsManager = SettingsManager(this)
            val themeRes = if (settingsManager.themeMode == "light") {
                R.style.Theme_ArtrawFiles_Light
            } else {
                R.style.Theme_ArtrawFiles_Splash
            }
            setTheme(themeRes)
            setContentView(R.layout.activity_splash)

            EdgeToEdgeHelper.applyEdgeToEdge(this)

            initViews()
            startMinimalReveal()
            scheduleNavigation()
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_SPLASH", "SplashActivity init error: ${e.message}", e)
            navigateToNextScreen(instant = true)
        }
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.layoutSplashRoot)
        layoutBrand = findViewById(R.id.layoutSplashBrand)
        appName = findViewById(R.id.tvSplashAppName)
        appTagline = findViewById(R.id.tvSplashTagline)
        footerLayout = findViewById(R.id.layoutSplashFooter)

        // Initial alpha states for minimal, smooth fade
        appName?.alpha = 0f
        appTagline?.alpha = 0f
        footerLayout?.alpha = 0f

        // Instant skip on tap
        val skip = {
            if (!isNavigating) {
                navigateToNextScreen(instant = false)
            }
        }
        rootLayout?.setOnClickListener { skip() }
    }

    private fun startMinimalReveal() {
        val interpolator = DecelerateInterpolator(1.5f)

        appName?.animate()
            ?.alpha(1f)
            ?.setDuration(300L)
            ?.setInterpolator(interpolator)
            ?.start()

        appTagline?.animate()
            ?.alpha(1f)
            ?.setStartDelay(100L)
            ?.setDuration(300L)
            ?.setInterpolator(interpolator)
            ?.start()

        footerLayout?.animate()
            ?.alpha(0.7f)
            ?.setStartDelay(180L)
            ?.setDuration(300L)
            ?.start()
    }

    private fun scheduleNavigation() {
        exitFadeRunnable = Runnable {
            rootLayout?.animate()
                ?.alpha(0.85f)
                ?.setDuration(SPLASH_DISPLAY_DURATION_MS - EXIT_FADE_OFFSET_MS)
                ?.start()
        }
        mainHandler.postDelayed(exitFadeRunnable!!, EXIT_FADE_OFFSET_MS)

        navigationRunnable = Runnable {
            navigateToNextScreen()
        }
        mainHandler.postDelayed(navigationRunnable!!, SPLASH_DISPLAY_DURATION_MS)
    }

    private fun navigateToNextScreen(instant: Boolean = false) {
        if (isNavigating || isFinishing) return
        isNavigating = true

        try {
            navigationRunnable?.let { mainHandler.removeCallbacks(it) }
            exitFadeRunnable?.let { mainHandler.removeCallbacks(it) }

            val settingsManager = SettingsManager(this)
            val nextIntent = if (!settingsManager.isOnboardingCompleted) {
                Intent(this, OnboardingActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }

            startActivity(nextIntent)
            if (!instant) {
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else {
                overridePendingTransition(0, 0)
            }
            finish()
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_SPLASH", "SplashActivity navigate error: ${e.message}", e)
            finish()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        appName?.animate()?.cancel()
        appTagline?.animate()?.cancel()
        footerLayout?.animate()?.cancel()
        rootLayout?.animate()?.cancel()
        super.onDestroy()
    }
}
