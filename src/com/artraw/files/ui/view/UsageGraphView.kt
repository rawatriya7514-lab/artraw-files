package com.artraw.files.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.artraw.files.monitor.AppUsageInfo

class UsageGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val apps = mutableListOf<AppUsageInfo>()

    private val colors = intArrayOf(
        Color.parseColor("#FF3366"), // Artfiles Crimson
        Color.parseColor("#33CCFF"), // Cyan / Blue
        Color.parseColor("#FFCC00"), // Amber / Gold
        Color.parseColor("#B366FF"), // Purple
        Color.parseColor("#00E676"), // Emerald Green
        Color.parseColor("#FF9933")  // Orange
    )

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#252525")
        style = Paint.Style.FILL
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * resources.displayMetrics.density
        isFakeBoldText = true
    }

    private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 11f * resources.displayMetrics.density
    }

    fun setApps(newApps: List<AppUsageInfo>) {
        apps.clear()
        apps.addAll(newApps.take(6))
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val itemHeight = 44f * density
        val totalH = if (apps.isEmpty()) (100f * density).toInt() else (apps.size * itemHeight + 16f * density).toInt()
        setMeasuredDimension(w, totalH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (apps.isEmpty()) return

        val density = resources.displayMetrics.density
        val padding = 12f * density
        val availWidth = width - (padding * 2)
        var curY = padding

        val maxTime = apps.firstOrNull()?.totalTimeMillis?.coerceAtLeast(1L) ?: 1L

        for (i in 0 until apps.size) {
            val app = apps[i]
            val color = colors[i % colors.size]

            // Draw app name
            canvas.drawText(app.appName, padding, curY + 12f * density, titlePaint)

            // Draw duration and percent
            val rightText = "${app.formattedDuration} (${app.percentOfTotal}%)"
            val textWidth = metaPaint.measureText(rightText)
            canvas.drawText(rightText, width - padding - textWidth, curY + 12f * density, metaPaint)

            // Draw bar
            val barTop = curY + 18f * density
            val barBottom = barTop + 8f * density
            val bgRect = RectF(padding, barTop, width - padding, barBottom)
            canvas.drawRoundRect(bgRect, 4f * density, 4f * density, barBgPaint)

            val fillFraction = (app.totalTimeMillis.toFloat() / maxTime).coerceIn(0.02f, 1.0f)
            val fillWidth = availWidth * fillFraction
            val fillRect = RectF(padding, barTop, padding + fillWidth, barBottom)
            barPaint.color = color
            canvas.drawRoundRect(fillRect, 4f * density, 4f * density, barPaint)

            curY += 44f * density
        }
    }
}
