package com.artraw.files.ad

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.artraw.files.R
import com.artraw.files.util.AppSecurity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds

/**
 * Production AdMob manager for Artfiles.
 * Strictly serves user configured production ad units.
 * Never falls back to sample test banners in production builds.
 */
object AdMobManager {

    private const val TAG = "ARTRAW_ADMOB"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                if (!context.resources.getBoolean(R.bool.admob_enabled)) {
                    Log.d(TAG, "AdMob is disabled in configuration.")
                    return
                }
                MobileAds.initialize(context.applicationContext) { status ->
                    Log.d(TAG, "MobileAds initialized: $status")
                }
                isInitialized = true
                Log.d(TAG, "MobileAds initialization dispatched.")
            } catch (t: Throwable) {
                Log.e(TAG, "MobileAds init error: ${t.message}", t)
            }
        }
    }

    fun loadBanner(activity: Activity, container: FrameLayout?): AdView? {
        if (container == null) return null

        val isAdMobEnabled = try {
            activity.resources.getBoolean(R.bool.admob_enabled)
        } catch (_: Throwable) {
            true
        }

        if (!isAdMobEnabled) {
            container.visibility = View.GONE
            return null
        }

        init(activity)

        // Ensure container is measured and visible
        container.visibility = View.VISIBLE

        val rawId = try {
            activity.getString(R.string.admob_banner_ad_unit_id)
        } catch (_: Throwable) {
            ""
        }
        val targetAdUnitId = AppSecurity.resolveAdUnitId(rawId)

        return setupBannerView(activity, container, targetAdUnitId)
    }

    private fun setupBannerView(
        activity: Activity,
        container: FrameLayout,
        adUnitId: String
    ): AdView {
        val adView = AdView(activity).apply {
            this.adUnitId = adUnitId
            setAdSize(AdSize.BANNER)
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        container.removeAllViews()
        container.addView(adView, params)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.i(TAG, "Banner loaded successfully! (Unit: $adUnitId)")
                activity.runOnUiThread {
                    container.visibility = View.VISIBLE
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.w(TAG, "Banner failed to load ($adUnitId) [code ${loadAdError.code}]: ${loadAdError.message}")
                // Retry loading the production unit after 30 seconds
                mainHandler.postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        Log.d(TAG, "Retrying banner load for unit: $adUnitId")
                        setupBannerView(activity, container, adUnitId)
                    }
                }, 30000)
            }
        }

        try {
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
            Log.d(TAG, "Banner load request sent for unit: $adUnitId")
        } catch (t: Throwable) {
            Log.e(TAG, "adView.loadAd error: ${t.message}", t)
        }

        return adView
    }
}
