package com.artraw.files.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * ARTRAW Security & Anti-Tamper Engine
 *
 * Enforces runtime bytecode and signature integrity:
 * - Anti-Tampering & Anti-Re-signing via SHA-256 certificate fingerprint verification
 * - Dynamic runtime string obfuscation (AdMob Unit ID)
 * - Anti-Debugging & Anti-Cloning package checks
 */
object AppSecurity {

    private const val TAG = "ARTRAW_SECURITY"
    private const val EXPECTED_PACKAGE_NAME = "com.artraw.files"

    // Official ARTRAW Release Certificate SHA-256 (artraw-release.keystore)
    private const val RELEASE_CERT_SHA256 = "812da611ca120ab5729ee4405856ecfd1fbd01c4a3a58a1184d7b76dc5810de8"

    // Google Test Banner Unit ID
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

    // Masked byte sequence for production Banner Unit ID (ca-app-pub-7925407604309207/7795338269)
    // XOR key: 0x5C
    private val MASKED_PROD_AD_UNIT = byteArrayOf(
        63, 61, 113, 61, 44, 44, 113, 44, 41, 62, 113, 107, 101, 110, 105, 104, 108, 107,
        106, 108, 104, 111, 108, 101, 110, 108, 107, 115, 107, 107, 101, 105, 111, 111, 100,
        110, 106, 101
    )
    // Masked byte sequence for production Interstitial Unit ID (ca-app-pub-7925407604309207/4693787341)
    private val MASKED_PROD_INTERSTITIAL_UNIT = byteArrayOf(
        63, 61, 113, 61, 44, 44, 113, 44, 41, 62, 113, 107, 101, 110, 105, 104, 108, 107,
        106, 108, 104, 111, 108, 101, 110, 108, 107, 115, 104, 106, 101, 111, 107, 100, 107,
        111, 104, 109
    )
    private const val XOR_KEY: Byte = 0x5C

    /**
     * Resolves the verified Ad Unit ID at runtime.
     * Prevents static string scraping and ad unit swapping by unauthorized forks.
     */
    fun resolveAdUnitId(fallbackId: String): String {
        return try {
            if (fallbackId == TEST_BANNER_ID) {
                TEST_BANNER_ID
            } else {
                val decoded = String(ByteArray(MASKED_PROD_AD_UNIT.size) { i ->
                    (MASKED_PROD_AD_UNIT[i].toInt() xor XOR_KEY.toInt()).toByte()
                }, Charsets.UTF_8)
                if (decoded.startsWith("ca-app-pub-")) decoded else fallbackId
            }
        } catch (_: Throwable) {
            fallbackId
        }
    }

    /**
     * Resolves the verified Interstitial Ad Unit ID at runtime.
     */
    fun resolveInterstitialAdUnitId(fallbackId: String): String {
        return try {
            val decoded = String(ByteArray(MASKED_PROD_INTERSTITIAL_UNIT.size) { i ->
                (MASKED_PROD_INTERSTITIAL_UNIT[i].toInt() xor XOR_KEY.toInt()).toByte()
            }, Charsets.UTF_8)
            if (decoded.startsWith("ca-app-pub-")) decoded else fallbackId
        } catch (_: Throwable) {
            fallbackId
        }
    }

    /**
     * Resolves the verified Rewarded Ad Unit ID at runtime.
     */
    fun resolveRewardedAdUnitId(fallbackId: String): String {
        return resolveInterstitialAdUnitId(fallbackId)
    }

    /**
     * Verifies that the running application has not been altered, decompiled, or re-signed.
     */
    fun verifyIntegrity(context: Context): Boolean {
        try {
            // 1. Verify package identity
            if (context.packageName != EXPECTED_PACKAGE_NAME) {
                Log.e(TAG, "Integrity failure: package mismatch")
                return false
            }

            // 2. Anti-Debug inspection (disallow debugging in production release)
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable) {
                Log.e(TAG, "Integrity failure: debuggable flag detected")
                return false
            }

            // 3. Verify cryptographic release signature fingerprint
            val signature = getCertificateSha256(context)
            if (signature != null && !signature.equals(RELEASE_CERT_SHA256, ignoreCase = true)) {
                Log.e(TAG, "Integrity failure: signature mismatch (got $signature)")
                return false
            }

            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Integrity check exception: ${t.message}")
            return true
        }
    }

    /**
     * Extracts and computes the SHA-256 fingerprint of the installed APK signing certificate.
     */
    fun getCertificateSha256(context: Context): String? {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else null
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return null
            val certBytes = signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(certBytes)
            val hex = StringBuilder()
            for (b in digest) {
                hex.append(String.format("%02x", b))
            }
            hex.toString()
        } catch (_: Throwable) {
            null
        }
    }
}
