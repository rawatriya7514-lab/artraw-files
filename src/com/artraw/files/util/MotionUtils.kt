package com.artraw.files.util

import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Small, shared motion toolkit.
 *
 * All animations are optional and honour both the in-app "Animations" switch
 * and the system animator duration scale. Motion is deliberately restrained:
 * short durations, tiny distances, no looping decoration on content.
 */
object MotionUtils {

    fun enabled(context: Context): Boolean {
        return try {
            val prefs = context.getSharedPreferences("artraw_files_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("animations_enabled", true)) return false
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale > 0f
        } catch (_: Throwable) {
            true
        }
    }

    /** Soft press feedback: scales down slightly while the finger is held. */
    fun pressScale(view: View, pressedScale: Float = 0.97f) {
        val ctx = view.context
        if (!enabled(ctx)) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(pressedScale)
                    .scaleY(pressedScale)
                    .setDuration(110)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            false
        }
    }

    /** Fade + rise entrance used by cards and list rows. */
    fun fadeUp(view: View, delayMs: Long = 0L, distanceDp: Float = 10f) {
        if (!enabled(view.context)) return
        val distance = distanceDp * view.resources.displayMetrics.density
        view.alpha = 0f
        view.translationY = distance
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    /** Staggered list entrance. Position is capped so long lists stay snappy. */
    fun stagger(view: View, position: Int, maxStagger: Int = 10) {
        if (!enabled(view.context)) return
        val delay = (position.coerceAtMost(maxStagger) * 24L)
        fadeUp(view, delayMs = delay, distanceDp = 8f)
    }

    /** Gentle scale pulse for a newly selected element. */
    fun pop(view: View) {
        if (!enabled(view.context)) return
        view.animate()
            .scaleX(1.06f)
            .scaleY(1.06f)
            .setDuration(120)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    fun crossFade(view: View, visible: Boolean, duration: Long = 180) {
        if (!enabled(view.context)) {
            view.visibility = if (visible) View.VISIBLE else View.GONE
            return
        }
        if (visible) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(duration).start()
        } else {
            view.animate().alpha(0f).setDuration(duration).withEndAction {
                view.visibility = View.GONE
            }.start()
        }
    }
}
