package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.artraw.files.R
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager

class OnboardingActivity : Activity() {

    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_onboarding)

        val root = findViewById<android.view.View>(R.id.layoutOnboardingRoot)
        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topHeaders = listOf(root),
            bottomScrollViews = listOf(root)
        )

        findViewById<Button>(R.id.btnOnboardingGrant).setOnClickListener {
            settingsManager.isOnboardingCompleted = true
            PermissionsHelper.requestStoragePermission(this, 2001)
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionsHelper.hasStoragePermission(this)) {
            settingsManager.isOnboardingCompleted = true
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (PermissionsHelper.hasStoragePermission(this)) {
            settingsManager.isOnboardingCompleted = true
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
        }
    }
}
