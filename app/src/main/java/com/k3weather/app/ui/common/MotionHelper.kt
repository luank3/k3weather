package com.k3weather.app.ui.common

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView

/**
 * Shared micro-interaction helpers for tactile spring press feedback, fluid easing curves,
 * and subtle text cross-fades across views.
 */
object MotionHelper {

    val FLUID_EASE = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    private val RELEASE_EASE = DecelerateInterpolator(1.6f)

    @SuppressLint("ClickableViewAccessibility")
    fun attachPressScale(
        view: View,
        pressedScale: Float = 0.96f,
        hapticOnTap: Boolean = false
    ) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .setDuration(90L)
                        .setInterpolator(RELEASE_EASE)
                        .start()
                }
                MotionEvent.ACTION_UP -> {
                    if (hapticOnTap) {
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(FLUID_EASE)
                        .start()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(RELEASE_EASE)
                        .start()
                }
            }
            false
        }
    }

    fun pulseTextChange(view: TextView, newText: CharSequence, reduceMotion: Boolean) {
        if (view.text == newText) return
        if (reduceMotion) {
            view.text = newText
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(-4f * view.resources.displayMetrics.density)
            .setDuration(90L)
            .withEndAction {
                view.text = newText
                view.translationY = 4f * view.resources.displayMetrics.density
                view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(160L)
                    .setInterpolator(FLUID_EASE)
                    .start()
            }
            .start()
    }
}
