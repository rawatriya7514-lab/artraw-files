package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.util.SettingsManager

class SplashActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val tv = TextView(this)
            tv.text = "Artfiles"
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.setTextSize(24f)
            tv.gravity = android.view.Gravity.CENTER
            setContentView(tv)
        } catch (e: Throwable) {
            android.util.Log.e("ARTRAW_CRASH", "SplashActivity.onCreate error: ${e.message}", e)
        }

        val delayMillis = 2000L
        mainHandler.postDelayed({
            try {
                val settingsManager = SettingsManager(this)
                val intent = if (!settingsManager.isOnboardingCompleted) {
                    Intent(this, OnboardingActivity::class.java)
                } else {
                    Intent(this, MainActivity::class.java)
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                finish()
            } catch (e: Throwable) {
                android.util.Log.e("ARTRAW_CRASH", "SplashActivity handler error: ${e.message}", e)
            }
        }, delayMillis)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
