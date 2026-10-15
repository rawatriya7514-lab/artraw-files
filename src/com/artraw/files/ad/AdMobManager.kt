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
import com.google.android.gms.ads.RequestConfiguration

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
                // AdMob Qualification & Policy Compliance: Declare COPPA & rating
                val reqConfig = MobileAds.getRequestConfiguration().toBuilder()
                    .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
                    .setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE)
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                    .build()
                MobileAds.setRequestConfiguration(reqConfig)

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

    @Volatile
    private var interstitialAd: com.google.android.gms.ads.interstitial.InterstitialAd? = null
    @Volatile
    private var isInterstitialLoading = false
    private var lastInterstitialShownTime = 0L

    fun loadInterstitial(activity: Activity) {
        val isAdMobEnabled = try {
            activity.resources.getBoolean(R.bool.admob_enabled)
        } catch (_: Throwable) {
            true
        }
        if (!isAdMobEnabled || interstitialAd != null || isInterstitialLoading) return

        init(activity)

        val rawId = try {
            activity.getString(R.string.admob_interstitial_ad_unit_id)
        } catch (_: Throwable) {
            ""
        }
        val targetAdUnitId = AppSecurity.resolveInterstitialAdUnitId(rawId)
        if (targetAdUnitId.isEmpty()) return

        isInterstitialLoading = true
        val adRequest = AdRequest.Builder().build()
        com.google.android.gms.ads.interstitial.InterstitialAd.load(
            activity,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.interstitial.InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                    Log.i(TAG, "Interstitial ad loaded successfully! (Unit: $targetAdUnitId)")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial ad failed to load ($targetAdUnitId) [code ${loadAdError.code}]: ${loadAdError.message}")
                    mainHandler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            loadInterstitial(activity)
                        }
                    }, 60000)
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onDismissed: (() -> Unit)? = null): Boolean {
        val ad = interstitialAd
        val now = System.currentTimeMillis()
        if (ad != null && now - lastInterstitialShownTime > 60000) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    lastInterstitialShownTime = System.currentTimeMillis()
                    loadInterstitial(activity)
                    onDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onDismissed?.invoke()
                }
            }
            ad.show(activity)
            return true
        } else {
            onDismissed?.invoke()
            if (interstitialAd == null) {
                loadInterstitial(activity)
            }
            return false
        }
    }

    @Volatile
    private var rewardedAd: com.google.android.gms.ads.rewarded.RewardedAd? = null
    @Volatile
    private var isRewardedLoading = false

    fun loadRewardedAd(activity: Activity) {
        val isAdMobEnabled = try {
            activity.resources.getBoolean(R.bool.admob_enabled)
        } catch (_: Throwable) {
            true
        }
        if (!isAdMobEnabled || rewardedAd != null || isRewardedLoading) return

        init(activity)

        val rawId = try {
            activity.getString(R.string.admob_rewarded_ad_unit_id)
        } catch (_: Throwable) {
            ""
        }
        val targetAdUnitId = AppSecurity.resolveRewardedAdUnitId(rawId)
        if (targetAdUnitId.isEmpty()) return

        isRewardedLoading = true
        val adRequest = AdRequest.Builder().build()
        com.google.android.gms.ads.rewarded.RewardedAd.load(
            activity,
            targetAdUnitId,
            adRequest,
            object : com.google.android.gms.ads.rewarded.RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: com.google.android.gms.ads.rewarded.RewardedAd) {
                    rewardedAd = ad
                    isRewardedLoading = false
                    Log.i(TAG, "Rewarded ad loaded successfully! (Unit: $targetAdUnitId)")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded ad failed to load ($targetAdUnitId) [code ${loadAdError.code}]: ${loadAdError.message}")
                    mainHandler.postDelayed({
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            loadRewardedAd(activity)
                        }
                    }, 60000)
                }
            }
        )
    }

    fun isRewardedAdReady(): Boolean = rewardedAd != null

    fun showRewardedAd(
        activity: Activity,
        onRewardEarned: ((com.google.android.gms.ads.rewarded.RewardItem) -> Unit)? = null,
        onDismissed: (() -> Unit)? = null
    ): Boolean {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd(activity)
                    onDismissed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    rewardedAd = null
                    loadRewardedAd(activity)
                    onDismissed?.invoke()
                }
            }
            ad.show(activity) { rewardItem ->
                Log.i(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned?.invoke(rewardItem)
            }
            return true
        } else {
            loadRewardedAd(activity)
            onDismissed?.invoke()
            return false
        }
    }
}
