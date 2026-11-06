package com.artraw.files.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.artraw.files.R
import com.artraw.files.util.EdgeToEdgeHelper
import com.artraw.files.util.MotionUtils

/**
 * Toolbox: a calm hub for every maintenance tool.
 * Rows are built from a declarative list so the screen stays consistent.
 */
class ToolboxActivity : BaseActivity() {

    private data class ToolEntry(
        val title: String,
        val description: String,
        val iconRes: Int,
        val tintRes: Int,
        val action: () -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toolbox)

        EdgeToEdgeHelper.applyEdgeToEdge(
            activity = this,
            topSpacers = listOf(findViewById(R.id.statusBarSpacerToolbox)),
            bottomScrollViews = listOf(findViewById(R.id.scrollToolbox))
        )

        findViewById<View>(R.id.btnToolboxBack).setOnClickListener { finish() }
        MotionUtils.pressScale(findViewById(R.id.cardToolboxHero), 0.985f)
        MotionUtils.fadeUp(findViewById(R.id.cardToolboxHero), 0, 12f)

        buildSections()
    }

    private fun buildSections() {
        val container = findViewById<LinearLayout>(R.id.layoutToolboxSections)

        val sections = listOf(
            "SMART MEDIA & AI" to listOf(
                ToolEntry(
                    "AI Smart Gallery",
                    "Natural language search, OCR, duplicate detection & smart categorization",
                    R.drawable.ic_sparkles,
                    R.color.aurora_primary
                ) { startActivity(Intent(this, com.artraw.files.ui.gallery.SmartGalleryActivity::class.java)) }
            ),
            "ANALYZE & CLEAN" to listOf(
                ToolEntry(
                    "Storage Analyzer",
                    "Understand what uses space, from folders to file types",
                    R.drawable.ic_storage,
                    R.color.aurora_primary
                ) { startActivity(Intent(this, AnalyzerActivity::class.java)) },
                ToolEntry(
                    "Empty Folder Cleaner",
                    "Find and remove empty directory shells",
                    R.drawable.ic_folder,
                    R.color.cyber_amber
                ) { startActivity(Intent(this, EmptyFolderCleanerActivity::class.java)) },
                ToolEntry(
                    "Junk & Cache Cleaner",
                    "Clear residual logs and temporary files",
                    R.drawable.ic_delete,
                    R.color.aurora_secondary
                ) { startActivity(Intent(this, JunkCleanerActivity::class.java)) }
            ),
            "PRIVACY & SECURITY" to listOf(
                ToolEntry(
                    "Secure Shredder",
                    "Overwrite files before permanent deletion",
                    R.drawable.ic_lock,
                    R.color.aurora_error
                ) { startActivity(Intent(this, ShredderActivity::class.java)) },
                ToolEntry(
                    "Secure Vault",
                    "AES-256 encrypted private storage",
                    R.drawable.ic_lock,
                    R.color.aurora_primary
                ) { startActivity(Intent(this, VaultActivity::class.java)) },
                ToolEntry(
                    "File Checksum",
                    "Verify integrity with MD5, SHA-1 and SHA-256",
                    R.drawable.ic_check_circle,
                    R.color.aurora_secondary
                ) { startActivity(Intent(this, ChecksumActivity::class.java)) },
                ToolEntry(
                    "Recycle Bin",
                    "Restore or permanently remove deleted files",
                    R.drawable.ic_delete,
                    R.color.aurora_text_secondary
                ) { startActivity(Intent(this, TrashActivity::class.java)) }
            ),
            "DEVICE & TRANSFER" to listOf(
                ToolEntry(
                    "System Health Monitor",
                    "Temperature, CPU and live usage timeline",
                    R.drawable.ic_monitor,
                    R.color.aurora_success
                ) { startActivity(Intent(this, DeviceMonitorActivity::class.java)) },
                ToolEntry(
                    "Wi-Fi Transfer",
                    "Move files to a computer over your local network",
                    R.drawable.ic_share,
                    R.color.cyber_violet
                ) { startActivity(Intent(this, WiFiTransferActivity::class.java)) }
            )
        )

        var delay = 60L
        sections.forEach { (label, entries) ->
            val header = TextView(this).apply {
                text = label
                setTextColor(getColor(R.color.aurora_primary))
                textSize = 11f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                letterSpacing = 0.12f
                setPadding((4 * resources.displayMetrics.density).toInt(), 0, 0, (8 * resources.displayMetrics.density).toInt())
            }
            container.addView(header)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_glass_clay_card)
                setPadding(dp(7), dp(4), dp(7), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(18) }
            }

            entries.forEachIndexed { index, entry ->
                val row = LayoutInflater.from(this)
                    .inflate(R.layout.item_toolbox_row, card, false)
                row.findViewById<TextView>(R.id.tvToolTitle).text = entry.title
                row.findViewById<TextView>(R.id.tvToolDesc).text = entry.description
                val icon = row.findViewById<ImageView>(R.id.imgToolIcon)
                icon.setImageResource(entry.iconRes)
                icon.setColorFilter(getColor(entry.tintRes))
                row.setOnClickListener { entry.action() }
                MotionUtils.pressScale(row, 0.985f)
                MotionUtils.fadeUp(row, delay, 6f)
                delay += 24L
                card.addView(row)

                if (index != entries.lastIndex) {
                    val divider = View(this).apply {
                        setBackgroundColor(getColor(R.color.aurora_divider))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            resources.displayMetrics.density.toInt().coerceAtLeast(1)
                        ).apply { marginStart = dp(62) }
                    }
                    card.addView(divider)
                }
            }
            container.addView(card)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
