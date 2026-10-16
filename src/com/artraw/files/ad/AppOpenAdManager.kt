package com.artraw.files.ad

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.artraw.files.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds

/**
 * AppOpenAdManager — Manages App Open ads displayed during app cold starts.
 *
 * Fully crash-safe: every operation wrapped in try-catch, all activity state
 * checked before use, MobileAds initialization awaited before loading ads.
 */
object AppOpenAdManager {

    private const val TAG = "ARTRAW_APP_OPEN"

    @Volatile
    private var appOpenAd: AppOpenAd? = null
    @Volatile
    private var isLoading = false
    private var shouldShowOnResume = false
    private var wasColdStart = true
    private var mobileAdsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initialize App Open ad support. Call from launcher Activity.onCreate()
     * AFTER MobileAds.initialize() has been called.
     */
    fun initialize(context: Context) {
        try {
            if (!context.resources.getBoolean(R.bool.admob_enabled)) {
                Log.d(TAG, "App Open ads disabled in configuration.")
                return
            }
        } catch (_: Throwable) {
            return
        }

        // Wait briefly for MobileAds.initialize() to complete, then load ad
        mainHandler.postDelayed({
            try {
                loadAd(context)
            } catch (_: Throwable) {}
        }, 500L)
    }

    /** Load an App Open ad — wrapped in try-catch. */
    fun loadAd(context: Context) {
        try {
            if (!context.resources.getBoolean(R.bool.admob_enabled)) return
            if (isLoading || appOpenAd != null) return

            val adUnitId = context.getString(R.string.admob_app_open_ad_unit_id)
            if (adUnitId.isEmpty()) {
                Log.w(TAG, "App Open ad unit ID is empty.")
                return
            }

            isLoading = true
            AppOpenAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        try {
                            appOpenAd = ad
                            isLoading = false
                            Log.i(TAG, "App Open ad loaded successfully.")
                        } catch (_: Throwable) {
                            isLoading = false
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        try {
                            appOpenAd = null
                            isLoading = false
                            Log.w(TAG, "App Open ad failed to load: ${loadAdError.message}")
                            mainHandler.postDelayed({ loadAd(context) }, 60000)
                        } catch (_: Throwable) {
                            isLoading = false
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Log.w(TAG, "App Open ad load exception: ${e.message}")
            isLoading = false
        }
    }

    /**
     * Show the App Open ad if available.
     * Call from Activity.onResume after a cold start.
     */
    fun showIfAvailable(activity: Activity) {
        try {
            if (!activity.resources.getBoolean(R.bool.admob_enabled)) return
            if (activity.isFinishing || activity.isDestroyed) return

            if (!wasColdStart) {
                loadAd(activity)
                return
            }
            wasColdStart = false

            if (appOpenAd != null && shouldShowOnResume) {
                try {
                    appOpenAd?.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            try {
                                appOpenAd = null
                                shouldShowOnResume = false
                                loadAd(activity)
                            } catch (_: Throwable) {}
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            try {
                                appOpenAd = null
                                shouldShowOnResume = false
                                loadAd(activity)
                            } catch (_: Throwable) {}
                        }
                    }
                    appOpenAd?.show(activity)
                    shouldShowOnResume = false
                } catch (e: Throwable) {
                    Log.w(TAG, "App Open ad show failed: ${e.message}")
                    appOpenAd = null
                    shouldShowOnResume = false
                    loadAd(activity)
                }
            } else {
                loadAd(activity)
            }
        } catch (_: Throwable) {}
    }

    /** Call from Activity.onPause to flag ad availability on next resume. */
    fun onActivityPaused() {
        try {
            shouldShowOnResume = true
        } catch (_: Throwable) {}
    }

    fun isAdReady(): Boolean = appOpenAd != null

    fun destroy() {
        try {
            appOpenAd = null
            isLoading = false
            shouldShowOnResume = false
        } catch (_: Throwable) {}
    }
}
