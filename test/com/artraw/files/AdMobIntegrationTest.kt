package com.artraw.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdMobIntegrationTest {

    private val expectedAppId = "ca-app-pub-9417083543459963~7636088996"
    private val expectedAdUnitId = "ca-app-pub-9417083543459963/1228883736"

    @Test
    fun testAdMobAppIdConfiguration() {
        val admobXml = File("res/values/admob.xml")
        assertTrue("res/values/admob.xml must exist", admobXml.exists())
        val content = admobXml.readText()
        assertTrue("AdMob App ID must match configured value", content.contains(expectedAppId))
        assertTrue("AdMob Banner Ad Unit ID must match configured value", content.contains(expectedAdUnitId))
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
        val content = layout.readText()

        assertTrue("Layout must contain layoutAdContainer", content.contains("android:id=\"@+id/layoutAdContainer\""))
        assertTrue("Layout must contain adView", content.contains("android:id=\"@+id/adView\""))
        assertTrue("AdView must be com.google.android.gms.ads.AdView", content.contains("com.google.android.gms.ads.AdView"))
    }
}
