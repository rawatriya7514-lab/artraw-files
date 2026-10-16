package com.artraw.files.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
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
            PermissionsHelper.showPermissionJustification(this) {
                PermissionsHelper.requestStoragePermission(this)
            }
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

        findViewById<View>(R.id.rowPrivacyPolicy)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Artfiles Privacy Policy")
                .setMessage(
                    "Artfiles is committed to complete user privacy & transparency:\n\n" +
                    "1. Local Storage: All file browsing, compression, audio, video, and image viewing happen 100% locally on your device. We never upload, transmit, or sell your files or personal documents.\n\n" +
                    "2. Google AdMob Disclosure: To support free development and updates, Artfiles integrates Google Mobile Ads SDK (AdMob). Google AdMob may collect device identifiers (such as the Google Advertising ID), IP addresses, and coarse performance diagnostics to serve personalized or non-personalized advertisements according to Google's Privacy Policy.\n\n" +
                    "3. User Data Rights: You can manage, reset, or opt out of personalized ads at any time via Android System Settings (Settings > Google > Ads) or online at adssettings.google.com."
                )
                .setPositiveButton("Online Policy") { _, _ ->
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://rawatriya7514-lab.github.io/artraw-files/privacy-policy.html")
                        )
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Close", null)
                .show()
        }

        findViewById<View>(R.id.rowGoogleAdSettings)?.setOnClickListener {
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://adssettings.google.com")
                )
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Could not open Google Ad Settings", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.rowAppAdsTxt)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Authorized Digital Sellers")
                .setMessage(
                    "Google AdMob Authorized Digital Sellers (app-ads.txt):\n\n" +
                    "• Publisher ID: pub-7925407604309207\n" +
                    "• Record: google.com, pub-7925407604309207, DIRECT, f08c47fec0942fa0\n\n" +
                    "This verifies domain ownership and protects your app revenue against unauthorized ad inventory manipulation."
                )
                .setPositiveButton("View app-ads.txt") { _, _ ->
                    try {
                        val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://rawatriya7514-lab.github.io/artraw-files/app-ads.txt")
                        )
                        startActivity(browserIntent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("OK", null)
                .show()
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
