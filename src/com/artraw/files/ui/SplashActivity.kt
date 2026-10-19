package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.SettingsManager
import java.io.File

/**
 * Modern, high-performance SplashActivity for Artfiles.
 *
 * Plays 4K upscaled launch animation:
 * - Direct playback of requested video animation.
 * - Tap-to-skip functionality for instantaneous access.
 * - Graceful fallback to choreographed vector logo reveal.
 * - Smooth fade transition into MainActivity / OnboardingActivity.
 */
class SplashActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isNavigating = false
    private var safetyTimeoutRunnable: Runnable? = null
    private var navigationRunnable: Runnable? = null
    private var exitFadeRunnable: Runnable? = null

    private var rootLayout: View? = null
    private var videoSplash: VideoView? = null
    private var layoutBrand: LinearLayout? = null
    private var logoCard: FrameLayout? = null
    private var appName: TextView? = null
    private var appTagline: TextView? = null
    private var appSubtitle: TextView? = null
    private var footerLayout: LinearLayout? = null

    companion object {
        private const val VIDEO_PATH = "/storage/emulated/0/Download/Video_2026-10-19_17_55_39_4K_upscaled.mp4"
        private const val MAX_VIDEO_SAFETY_TIMEOUT_MS = 2800L
        private const val FALLBACK_SPLASH_DURATION_MS = 1500L
        private const val EXIT_FADE_OFFSET_MS = 1250L

        // Process-level session state: ensures the video animation only plays on cold start (1st time launch)
        var hasPlayedSplashThisSession = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. If not task root, the app is already open in background! Resume immediately without showing splash.
        if (!isTaskRoot) {
            finish()
            return
        }

        // 2. If already played animation during this session, skip animation immediately
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
            playLaunchAnimation()
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_CRASH", "SplashActivity error: ${e.message}", e)
            navigateToNextScreen()
        }
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.layoutSplashRoot)
        videoSplash = findViewById(R.id.videoSplashAnimation)
        layoutBrand = findViewById(R.id.layoutSplashBrand)
        logoCard = findViewById(R.id.flSplashLogoCard)
        appName = findViewById(R.id.tvSplashAppName)
        appTagline = findViewById(R.id.tvSplashTagline)
        appSubtitle = findViewById(R.id.tvSplashSubtitle)
        footerLayout = findViewById(R.id.layoutSplashFooter)

        // Initial brand state
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

        // Instant skip on user tap
        val skip = {
            if (!isNavigating) {
                navigateToNextScreen()
            }
        }
        rootLayout?.setOnClickListener { skip() }
        videoSplash?.setOnTouchListener { _, _ ->
            skip()
            true
        }
    }

    private fun playLaunchAnimation() {
        var videoUri: Uri? = null

        // 1. Check user-specified external video path
        val externalFile = File(VIDEO_PATH)
        if (externalFile.exists() && externalFile.length() > 0) {
            videoUri = Uri.fromFile(externalFile)
        } else {
            // 2. Embedded APK raw resource fallback
            try {
                videoUri = Uri.parse("android.resource://$packageName/${R.raw.splash_animation}")
            } catch (_: Throwable) {}
        }

        if (videoUri != null && videoSplash != null) {
            try {
                videoSplash?.setVideoURI(videoUri)
                videoSplash?.setOnPreparedListener { mp ->
                    if (isNavigating || isFinishing) return@setOnPreparedListener
                    // Video is ready: hide fallback brand layout and play
                    layoutBrand?.animate()?.alpha(0f)?.setDuration(120L)?.withEndAction {
                        layoutBrand?.visibility = View.GONE
                    }?.start()
                    footerLayout?.visibility = View.GONE
                    mp.start()
                }

                videoSplash?.setOnCompletionListener {
                    navigateToNextScreen()
                }

                videoSplash?.setOnErrorListener { _, _, _ ->
                    fallbackToStandardAnimation()
                    true
                }

                // Safety timeout to ensure navigation proceeds even if decoder stalls
                safetyTimeoutRunnable = Runnable {
                    if (!isNavigating) {
                        navigateToNextScreen()
                    }
                }
                mainHandler.postDelayed(safetyTimeoutRunnable!!, MAX_VIDEO_SAFETY_TIMEOUT_MS)
                return
            } catch (t: Throwable) {
                android.util.Log.w("SplashActivity", "Video launch animation failed, falling back", t)
            }
        }

        fallbackToStandardAnimation()
    }

    private fun fallbackToStandardAnimation() {
        videoSplash?.visibility = View.GONE
        layoutBrand?.visibility = View.VISIBLE
        startProfessionalAnimation()
        scheduleNavigation()
    }

    private fun startProfessionalAnimation() {
        val decelerateFast = DecelerateInterpolator(1.8f)
        val decelerateSmooth = DecelerateInterpolator(1.5f)

        logoCard?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(650L)
            ?.setInterpolator(decelerateFast)
            ?.start()

        appName?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(160L)
            ?.setDuration(550L)
            ?.setInterpolator(decelerateSmooth)
            ?.start()

        appTagline?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setStartDelay(300L)
            ?.setDuration(500L)
            ?.setInterpolator(decelerateSmooth)
            ?.start()

        appSubtitle?.animate()
            ?.alpha(0.85f)
            ?.setStartDelay(420L)
            ?.setDuration(450L)
            ?.start()

        footerLayout?.animate()
            ?.alpha(0.65f)
            ?.setStartDelay(550L)
            ?.setDuration(400L)
            ?.start()
    }

    private fun scheduleNavigation() {
        exitFadeRunnable = Runnable {
            rootLayout?.animate()
                ?.alpha(0.92f)
                ?.setDuration(FALLBACK_SPLASH_DURATION_MS - EXIT_FADE_OFFSET_MS)
                ?.start()
        }
        mainHandler.postDelayed(exitFadeRunnable!!, EXIT_FADE_OFFSET_MS)

        navigationRunnable = Runnable {
            navigateToNextScreen()
        }
        mainHandler.postDelayed(navigationRunnable!!, FALLBACK_SPLASH_DURATION_MS)
    }

    private fun navigateToNextScreen(instant: Boolean = false) {
        if (isNavigating || isFinishing) return
        isNavigating = true

        try {
            safetyTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            navigationRunnable?.let { mainHandler.removeCallbacks(it) }
            exitFadeRunnable?.let { mainHandler.removeCallbacks(it) }

            try {
                videoSplash?.stopPlayback()
            } catch (_: Throwable) {}

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
            android.util.Log.e("ARTRAW_CRASH", "SplashActivity navigate error: ${e.message}", e)
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            if (videoSplash?.isPlaying == true) {
                videoSplash?.pause()
            }
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        try {
            videoSplash?.stopPlayback()
        } catch (_: Throwable) {}
        logoCard?.animate()?.cancel()
        appName?.animate()?.cancel()
        appTagline?.animate()?.cancel()
        appSubtitle?.animate()?.cancel()
        footerLayout?.animate()?.cancel()
        rootLayout?.animate()?.cancel()
        super.onDestroy()
    }
}
