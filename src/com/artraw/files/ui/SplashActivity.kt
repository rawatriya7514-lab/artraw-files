package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.SettingsManager

/**
 * Modern, high-performance SplashActivity for Artfiles.
 *
 * Implements a 1.5-second (1500ms) choreographed launch sequence:
 * - Fluid entrance animation with decelerate interpolators.
 * - Zero background clutter and ultra-smooth edge-to-edge rendering.
 * - Seamless handoff to MainActivity or OnboardingActivity.
 */
class SplashActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isNavigating = false
    private var navigationRunnable: Runnable? = null
    private var exitFadeRunnable: Runnable? = null

    private var rootLayout: View? = null
    private var logoCard: FrameLayout? = null
    private var appName: TextView? = null
    private var appTagline: TextView? = null
    private var appSubtitle: TextView? = null
    private var footerLayout: LinearLayout? = null

    companion object {
        private const val SPLASH_DURATION_MS = 1500L
        private const val EXIT_FADE_OFFSET_MS = 1250L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            startProfessionalAnimation()
            scheduleNavigation()
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_CRASH", "SplashActivity error: ${e.message}", e)
            navigateToNextScreen()
        }
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.layoutSplashRoot)
        logoCard = findViewById(R.id.flSplashLogoCard)
        appName = findViewById(R.id.tvSplashAppName)
        appTagline = findViewById(R.id.tvSplashTagline)
        appSubtitle = findViewById(R.id.tvSplashSubtitle)
        footerLayout = findViewById(R.id.layoutSplashFooter)

        // Prepare initial animation states (hidden/offset)
        logoCard?.apply {
            alpha = 0f
            scaleX = 0.72f
            scaleY = 0.72f
        }
        appName?.apply {
            alpha = 0f
            translationY = 32f
        }
        appTagline?.apply {
            alpha = 0f
            translationY = 20f
        }
        appSubtitle?.alpha = 0f
        footerLayout?.alpha = 0f

        // Allow instant skip on tap if user desires
        rootLayout?.setOnClickListener {
            if (!isNavigating) {
                navigateToNextScreen()
            }
        }
    }

    private fun startProfessionalAnimation() {
        val decelerateFast = DecelerateInterpolator(1.8f)
        val decelerateSmooth = DecelerateInterpolator(1.5f)

        // 1. Logo Entrance (0ms -> 650ms)
        logoCard?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(650L)
            ?.setInterpolator(decelerateFast)
            ?.start()

        // 2. App Title Reveal (160ms -> 710ms)
        appName?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(160L)
            ?.setDuration(550L)
            ?.setInterpolator(decelerateSmooth)
            ?.start()

        // 3. Tagline Reveal (300ms -> 800ms)
        appTagline?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(300L)
            ?.setDuration(500L)
            ?.setInterpolator(decelerateSmooth)
            ?.start()

        // 4. Subtitle / Badge Reveal (420ms -> 870ms)
        appSubtitle?.animate()
            ?.alpha(0.85f)
            ?.setStartDelay(420L)
            ?.setDuration(450L)
            ?.start()

        // 5. Minimal Footer Reveal (550ms -> 950ms)
        footerLayout?.animate()
            ?.alpha(0.65f)
            ?.setStartDelay(550L)
            ?.setDuration(400L)
            ?.start()
    }

    private fun scheduleNavigation() {
        // Soft exit fade before transition
        exitFadeRunnable = Runnable {
            rootLayout?.animate()
                ?.alpha(0.92f)
                ?.setDuration(SPLASH_DURATION_MS - EXIT_FADE_OFFSET_MS)
                ?.start()
        }
        mainHandler.postDelayed(exitFadeRunnable!!, EXIT_FADE_OFFSET_MS)

        // Exact 1.5s hand-off
        navigationRunnable = Runnable {
            navigateToNextScreen()
        }
        mainHandler.postDelayed(navigationRunnable!!, SPLASH_DURATION_MS)
    }

    private fun navigateToNextScreen() {
        if (isNavigating || isFinishing) return
        isNavigating = true

        try {
            val settingsManager = SettingsManager(this)
            val nextIntent = if (!settingsManager.isOnboardingCompleted) {
                Intent(this, OnboardingActivity::class.java)
            } else {
                Intent(this, MainActivity::class.java)
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(nextIntent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_CRASH", "SplashActivity navigate error: ${e.message}", e)
            finish()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        logoCard?.animate()?.cancel()
        appName?.animate()?.cancel()
        appTagline?.animate()?.cancel()
        appSubtitle?.animate()?.cancel()
        footerLayout?.animate()?.cancel()
        rootLayout?.animate()?.cancel()
        super.onDestroy()
    }
}
