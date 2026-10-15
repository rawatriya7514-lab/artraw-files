package com.artraw.files.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.artraw.files.monitor.ThermalPoint

class ThermalGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val points = mutableListOf<ThermalPoint>()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#282828")
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E8E93")
        textSize = 10f * resources.displayMetrics.density
    }

    private val peakDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF3366")
        style = Paint.Style.FILL
    }

    private val peakTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * resources.displayMetrics.density
        isFakeBoldText = true
    }

    fun setPoints(newPoints: List<ThermalPoint>) {
        points.clear()
        points.addAll(newPoints)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.isEmpty()) return

        val paddingLeft = 40f * resources.displayMetrics.density
        val paddingRight = 16f * resources.displayMetrics.density
        val paddingTop = 24f * resources.displayMetrics.density
        val paddingBottom = 24f * resources.displayMetrics.density

        val graphWidth = width - paddingLeft - paddingRight
        val graphHeight = height - paddingTop - paddingBottom
        if (graphWidth <= 0 || graphHeight <= 0) return

        val minTemp = 25f
        val maxTemp = 50f
        val tempRange = maxTemp - minTemp

        // Draw horizontal grid lines (30°C, 38°C, 45°C)
        val gridTemps = floatArrayOf(30f, 38f, 45f)
        for (gTemp in gridTemps) {
            val y = paddingTop + graphHeight - ((gTemp - minTemp) / tempRange * graphHeight)
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint)
            val label = "${gTemp.toInt()}°C"
            canvas.drawText(label, 6f * resources.displayMetrics.density, y + 4f * resources.displayMetrics.density, textPaint)
        }

        // Compute coordinate for each point
        val coords = ArrayList<Pair<Float, Float>>()
        val count = points.size
        for (i in 0 until count) {
            val p = points[i]
            val x = if (count > 1) paddingLeft + (i.toFloat() / (count - 1)) * graphWidth else paddingLeft + graphWidth / 2f
            val clampedTemp = p.tempCelsius.coerceIn(minTemp, maxTemp)
            val y = paddingTop + graphHeight - ((clampedTemp - minTemp) / tempRange * graphHeight)
            coords.add(Pair(x, y))
        }

        // Build line path & fill path
        val linePath = Path()
        val fillPath = Path()

        val first = coords.first()
        linePath.moveTo(first.first, first.second)
        fillPath.moveTo(first.first, paddingTop + graphHeight)
        fillPath.lineTo(first.first, first.second)

        for (i in 1 until coords.size) {
            val prev = coords[i - 1]
            val curr = coords[i]
            val midX = (prev.first + curr.first) / 2f
            linePath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
            fillPath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
        }

        val last = coords.last()
        fillPath.lineTo(last.first, paddingTop + graphHeight)
        fillPath.close()

        // Gradient shader for fill
        val shader = LinearGradient(
            0f, paddingTop, 0f, paddingTop + graphHeight,
            Color.parseColor("#44FF3366"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = shader
        canvas.drawPath(fillPath, fillPaint)

        // Draw line with high-contrast accent
        linePaint.color = Color.parseColor("#FF3366")
        canvas.drawPath(linePath, linePaint)

        // Highlight Peak temperature point
        var peakIdx = 0
        var maxVal = Float.MIN_VALUE
        for (i in 0 until points.size) {
            if (points[i].tempCelsius > maxVal) {
                maxVal = points[i].tempCelsius
                peakIdx = i
            }
        }

        if (peakIdx < coords.size) {
            val peakCoord = coords[peakIdx]
            val dotRadius = 6f * resources.displayMetrics.density
            canvas.drawCircle(peakCoord.first, peakCoord.second, dotRadius, peakDotPaint)

            val badgeText = "Peak: ${String.format("%.1f", maxVal)}°C"
            val textWidth = peakTextPaint.measureText(badgeText)
            val badgeX = (peakCoord.first - textWidth / 2f).coerceIn(paddingLeft, width - paddingRight - textWidth)
            val badgeY = (peakCoord.second - 10f * resources.displayMetrics.density).coerceAtLeast(paddingTop)
            canvas.drawText(badgeText, badgeX, badgeY, peakTextPaint)
        }

        // Draw X-axis first and last time labels
        if (points.isNotEmpty()) {
            val startTime = points.first().formattedTime
            val endTime = points.last().formattedTime
            val yBottom = height - 6f * resources.displayMetrics.density
            canvas.drawText(startTime, paddingLeft, yBottom, textPaint)
            val endWidth = textPaint.measureText(endTime)
            canvas.drawText(endTime, width - paddingRight - endWidth, yBottom, textPaint)
        }
    }
}
