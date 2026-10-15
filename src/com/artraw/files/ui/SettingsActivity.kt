package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.util.PermissionsHelper
import com.artraw.files.util.SettingsManager

class SettingsActivity : Activity() {

    private lateinit var settingsManager: SettingsManager

    private lateinit var tvCurrentLanguage: TextView
    private lateinit var cbShowHidden: CheckBox
    private lateinit var cbShowExtensions: CheckBox
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnManagePermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        setContentView(R.layout.activity_settings)

        com.artraw.files.util.EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerSettings)),
            bottomScrollViews = listOf(findViewById(R.id.scrollSettingsContent))
        )

        initViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        com.artraw.files.ad.AdMobManager.loadRewardedAd(this)
    }

    private fun initViews() {
        findViewById<View>(R.id.btnSettingsBack).setOnClickListener {
            finish()
        }

        tvCurrentLanguage = findViewById(R.id.tvCurrentLanguage)
        cbShowHidden = findViewById(R.id.cbShowHidden)
        cbShowExtensions = findViewById(R.id.cbShowExtensions)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnManagePermission = findViewById(R.id.btnManagePermission)

        tvCurrentLanguage.text = if (settingsManager.language == "hi") "हिन्दी (Hindi)" else "English"
        cbShowHidden.isChecked = settingsManager.showHiddenFiles
        cbShowExtensions.isChecked = settingsManager.showFileExtensions

        updatePermissionStatus()
    }

    private fun setupListeners() {
        findViewById<View>(R.id.rowLanguage).setOnClickListener {
            val languages = arrayOf("English", "हिन्दी (Hindi)")
            AlertDialog.Builder(this)
                .setTitle(R.string.pref_language)
                .setItems(languages) { _, which ->
                    settingsManager.language = if (which == 1) "hi" else "en"
                    tvCurrentLanguage.text = languages[which]
                }
                .show()
        }

        cbShowHidden.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.showHiddenFiles = isChecked
        }

        cbShowExtensions.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.showFileExtensions = isChecked
        }

        btnManagePermission.setOnClickListener {
            PermissionsHelper.requestStoragePermission(this)
        }

        findViewById<Button>(R.id.btnWatchRewardedAd)?.setOnClickListener {
            val shown = com.artraw.files.ad.AdMobManager.showRewardedAd(
                activity = this,
                onRewardEarned = { reward ->
                    android.widget.Toast.makeText(
                        this,
                        "Thank you for supporting Artfiles! You earned ${reward.amount} ${reward.type}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                },
                onDismissed = {
                    com.artraw.files.ad.AdMobManager.loadRewardedAd(this)
                }
            )
            if (!shown) {
                android.widget.Toast.makeText(
                    this,
                    "Loading video ad... Please try again in a moment.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                com.artraw.files.ad.AdMobManager.loadRewardedAd(this)
            }
        }
    }

    private fun updatePermissionStatus() {
        val hasPerm = PermissionsHelper.hasStoragePermission(this)
        if (hasPerm) {
            tvPermissionStatus.text = "All Files Access: Granted ✓"
            tvPermissionStatus.setTextColor(getColor(R.color.status_success))
        } else {
            tvPermissionStatus.text = "All Files Access: Denied ✗"
            tvPermissionStatus.setTextColor(getColor(R.color.status_error))
        }
    }
}
