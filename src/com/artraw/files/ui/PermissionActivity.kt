package com.artraw.files.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper

class PermissionActivity : BaseActivity() {

    private lateinit var btnOpenSettings: Button
    private lateinit var permIcon: ImageView
    private lateinit var permTitle: TextView
    private lateinit var permSubtitle: TextView
    private lateinit var step1: LinearLayout
    private lateinit var step2: LinearLayout
    private lateinit var step3: LinearLayout

    private val mainHandler = Handler(Looper.getMainLooper())
    private var checkPermissionRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            bottomStickyViews = listOf(findViewById<View>(android.R.id.content))
        )

        initViews()
        setupListeners()
        startPermissionFlow()
    }

    private fun initViews() {
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        permIcon = findViewById(R.id.permIcon)
        permTitle = findViewById(R.id.permTitle)
        permSubtitle = findViewById(R.id.permSubtitle)
        step1 = findViewById(R.id.permStep1)
        step2 = findViewById(R.id.permStep2)
        step3 = findViewById(R.id.permStep3)

        // Start pulse animation on icon
        val pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.permission_pulse)
        permIcon.startAnimation(pulseAnim)
    }

    private fun setupListeners() {
        btnOpenSettings.setOnClickListener {
            openSystemSettings()
        }
    }

    private fun startPermissionFlow() {
        // Check if already granted
        if (hasStoragePermission()) {
            showGrantedState()
            return
        }

        // Show step 1
        step1.visibility = View.VISIBLE
        step2.visibility = View.GONE
        step3.visibility = View.GONE
        permTitle.text = "Storage Access Required"
        permSubtitle.text = "ArtFiles needs All Files Access to browse, copy, move, and manage your files."
        btnOpenSettings.text = "Open Settings & Grant Access"
        btnOpenSettings.isEnabled = true

        // Start auto-check after user returns from settings
        startAutoCheck()
    }

    private fun openSystemSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    return
                } catch (_: Throwable) {}

                try {
                    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallback)
                    return
                } catch (_: Throwable) {}
            } else {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    1001
                )
                return
            }

            // Universal App Details Settings Fallback
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(appDetails)
        } catch (e: Throwable) {
            Toast.makeText(this, "Could not open settings: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startAutoCheck() {
        // Check every 2 seconds if permission granted (user returned from settings)
        checkPermissionRunnable = object : Runnable {
            override fun run() {
                if (hasStoragePermission()) {
                    mainHandler.removeCallbacks(this)
                    showGrantedState()
                } else {
                    mainHandler.postDelayed(this, 2000)
                }
            }
        }
        mainHandler.postDelayed(checkPermissionRunnable!!, 2000)
    }

    private fun showGrantedState() {
        // Stop pulse, start success animation
        permIcon.clearAnimation()
        val successAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.permission_fade_in)
        permIcon.startAnimation(successAnim)

        permIcon.setImageResource(R.drawable.ic_check_circle)
        permIcon.setColorFilter(getColor(R.color.aurora_success))

        step1.visibility = View.GONE
        step2.visibility = View.GONE
        step3.visibility = View.VISIBLE

        permTitle.text = "Permission Granted ✓"
        permSubtitle.text = "ArtFiles can now access your files. Redirecting you to the app..."

        btnOpenSettings.text = "Continue to App"
        btnOpenSettings.isEnabled = true
        btnOpenSettings.setOnClickListener {
            navigateToMain()
        }

        // Auto-navigate to MainActivity after 2 seconds
        mainHandler.postDelayed({
            navigateToMain()
        }, 2000)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            val read = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            val write = checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            mainHandler.removeCallbacksAndMessages(null)
            showGrantedState()
        }
    }

    override fun onBackPressed() {
        if (hasStoragePermission()) {
            navigateToMain()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
