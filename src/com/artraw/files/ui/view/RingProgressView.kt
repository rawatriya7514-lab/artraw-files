package com.artraw.files.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.artraw.files.R

/**
 * A calm, instrument-like progress ring.
 *
 * - Gradient sweep through the palette's crimson / dusty blue / sage accents
 * - Fine tick marks for a premium "precision instrument" feel
 * - Animates from 0 to the requested value once, then stays still
 */
class RingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val strokeWidth = 7f * density
    private val glowWidth = 12f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@RingProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
        color = resources.getColor(R.color.aurora_surface_elevated)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = resources.getColor(R.color.aurora_outline)
        alpha = 150
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@RingProgressView.glowWidth
        strokeCap = Paint.Cap.ROUND
        alpha = 38
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@RingProgressView.strokeWidth
        strokeCap = Paint.Cap.ROUND
    }

    private val arcBounds = RectF()
    private var progress: Float = 0f
    private var animator: ValueAnimator? = null

    private val colorPrimary = resources.getColor(R.color.aurora_primary)
    private val colorSecondary = resources.getColor(R.color.aurora_secondary)
    private val colorSuccess = resources.getColor(R.color.aurora_success)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setProgress(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 100f)
        animator?.cancel()
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 720
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (128 * density).toInt()
        val w = resolveSize(desired, widthMeasureSpec)
        val h = resolveSize(desired, heightMeasureSpec)
        val size = minOf(w, h)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - glowWidth / 2f - 2f * density
        if (radius <= 0f) return

        // Fine tick marks around the dial
        val tickOuter = radius + strokeWidth * 0.75f
        val tickInner = radius - strokeWidth * 0.75f
        for (i in 0 until 60) {
            val angle = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            tickPaint.alpha = if (major) 180 else 70
            tickPaint.strokeWidth = if (major) 1.6f * density else 1f * density
            val startR = if (major) tickInner - 2f * density else tickInner
            canvas.drawLine(
                cx + (startR * Math.cos(angle)).toFloat(),
                cy + (startR * Math.sin(angle)).toFloat(),
                cx + (tickOuter * Math.cos(angle)).toFloat(),
                cy + (tickOuter * Math.sin(angle)).toFloat(),
                tickPaint
            )
        }

        // Track
        canvas.drawCircle(cx, cy, radius, trackPaint)

        // Progress sweep
        if (progress > 0f) {
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            val shader = SweepGradient(
                cx, cy,
                intArrayOf(colorPrimary, colorSecondary, colorSuccess),
                floatArrayOf(0f, 0.55f, 1f)
            )
            progressPaint.shader = shader
            glowPaint.shader = shader
            val sweep = progress / 100f * 360f
            canvas.drawArc(arcBounds, -90f, sweep, false, glowPaint)
            canvas.drawArc(arcBounds, -90f, sweep, false, progressPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
