package com.artraw.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdMobIntegrationTest {

    private val expectedAppId = "ca-app-pub-7925407604309207~4393395283"
    private val expectedAdUnitId = "ca-app-pub-7925407604309207/7795338269"
    private val expectedInterstitialId = "ca-app-pub-7925407604309207/4693787341"
    private val expectedRewardedId = "ca-app-pub-7925407604309207/4693787341"

    @Test
    fun testAdMobAppIdConfiguration() {
        val admobXml = File("res/values/admob.xml")
        assertTrue("res/values/admob.xml must exist", admobXml.exists())
        val content = admobXml.readText()
        assertTrue("AdMob App ID must match configured value", content.contains(expectedAppId))
        assertTrue("AdMob Banner Ad Unit ID must match configured value", content.contains(expectedAdUnitId))
        assertTrue("AdMob Interstitial Ad Unit ID must match configured value", content.contains(expectedInterstitialId))
        assertTrue("AdMob Rewarded Ad Unit ID must match configured value", content.contains(expectedRewardedId))
    }

    @Test
    fun testAndroidManifestAdMobMetaData() {
        val manifest = File("AndroidManifest.xml")
        assertTrue("AndroidManifest.xml must exist", manifest.exists())
        val content = manifest.readText()

        assertTrue("Manifest must declare INTERNET permission", content.contains("android.permission.INTERNET"))
        assertTrue("Manifest must declare ACCESS_NETWORK_STATE permission", content.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue("Manifest must declare AD_ID permission", content.contains("com.google.android.gms.permission.AD_ID"))

        assertTrue(
            "Manifest must contain AdMob APPLICATION_ID meta-data",
            content.contains("android:name=\"com.google.android.gms.ads.APPLICATION_ID\"")
        )
        assertTrue(
            "Manifest APPLICATION_ID must match expected value",
            content.contains("android:value=\"$expectedAppId\"")
        )
        assertTrue(
            "Manifest must declare AdActivity",
            content.contains("com.google.android.gms.ads.AdActivity")
        )
    }

    @Test
    fun testActivityMainLayoutBannerAd() {
        val layout = File("res/layout/activity_main.xml")
        assertTrue("res/layout/activity_main.xml must exist", layout.exists())
        val layoutContent = layout.readText()
        assertTrue("Layout must contain adViewContainer", layoutContent.contains("android:id=\"@+id/adViewContainer\""))

        val mainActivity = File("src/com/artraw/files/ui/MainActivity.kt")
        assertTrue("MainActivity.kt must exist", mainActivity.exists())
        val mainContent = mainActivity.readText()
        assertTrue("MainActivity must create AdView programmatically", mainContent.contains("AdView(this)"))
        assertTrue("MainActivity must initialize MobileAds", mainContent.contains("MobileAds.initialize"))
        assertTrue("MainActivity must attach AdListener", mainContent.contains("ad.adListener = object : AdListener()"))
    }

    @Test
    fun testAppAdsTxtCompliance() {
        val rootAppAds = File("app-ads.txt")
        assertTrue("Root app-ads.txt must exist", rootAppAds.exists())
        val docsAppAds = File("docs/app-ads.txt")
        assertTrue("docs/app-ads.txt must exist for GitHub Pages", docsAppAds.exists())

        val content = rootAppAds.readText()
        assertTrue("app-ads.txt must contain publisher ID", content.contains("pub-7925407604309207"))
        assertTrue("app-ads.txt must contain Google domain and direct flag", content.contains("google.com, pub-7925407604309207, DIRECT, f08c47fec0942fa0"))
    }

    @Test
    fun testPrivacyPolicyAndRequestConfiguration() {
        val privacyHtml = File("docs/privacy-policy.html")
        assertTrue("docs/privacy-policy.html must exist", privacyHtml.exists())
        val htmlContent = privacyHtml.readText()
        assertTrue("Privacy policy must reference AdMob", htmlContent.contains("Google Mobile Ads"))
        assertTrue("Privacy policy must reference Advertising ID", htmlContent.contains("AD_ID"))

        val adMobManager = File("src/com/artraw/files/ad/AdMobManager.kt")
        val mgrContent = adMobManager.readText()
        assertTrue("AdMobManager must configure RequestConfiguration", mgrContent.contains("setRequestConfiguration"))
        assertTrue("AdMobManager must configure COPPA", mgrContent.contains("TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE"))
    }
}
