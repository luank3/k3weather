package com.k3weather.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class CompassDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDegree = 0f
    private var targetDegree = 0f
    private var animator: ValueAnimator? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33000000.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x40FFFFFF.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        textAlign = Paint.Align.CENTER
        color = 0x99FFFFFF.toInt()
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
        color = 0xFFFBBF24.toInt()
        isFakeBoldText = true
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val needlePath = Path()

    fun setWindDirection(degrees: Int, animate: Boolean = true) {
        val newTarget = (degrees % 360).toFloat()
        if (targetDegree == newTarget) return
        targetDegree = newTarget

        if (!animate) {
            currentDegree = targetDegree
            invalidate()
            return
        }

        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentDegree, targetDegree).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                currentDegree = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - 4f

        // Dark translucent circular dial with glass rim
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Cardinal directions
        canvas.drawText("N", cx, cy - radius + 18f, northPaint)
        canvas.drawText("S", cx, cy + radius - 6f, textPaint)
        canvas.drawText("E", cx + radius - 12f, cy + 6f, textPaint)
        canvas.drawText("W", cx - radius + 12f, cy + 6f, textPaint)

        // Center needle rotated to wind direction
        canvas.save()
        canvas.rotate(currentDegree, cx, cy)

        val needleLen = radius * 0.65f
        val needleHalfWidth = 6f

        needlePath.reset()
        // North/pointer arrow
        needlePath.moveTo(cx, cy - needleLen)
        needlePath.lineTo(cx + needleHalfWidth, cy)
        needlePath.lineTo(cx, cy - 4f)
        needlePath.lineTo(cx - needleHalfWidth, cy)
        needlePath.close()

        needlePaint.shader = LinearGradient(
            cx, cy - needleLen, cx, cy,
            0xFF38BDF8.toInt(), 0xFF0284C7.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(needlePath, needlePaint)

        // South tail
        needlePath.reset()
        needlePath.moveTo(cx, cy + needleLen * 0.7f)
        needlePath.lineTo(cx + needleHalfWidth * 0.8f, cy)
        needlePath.lineTo(cx, cy + 4f)
        needlePath.lineTo(cx - needleHalfWidth * 0.8f, cy)
        needlePath.close()

        needlePaint.shader = LinearGradient(
            cx, cy, cx, cy + needleLen * 0.7f,
            0x80FFFFFF.toInt(), 0x20FFFFFF.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(needlePath, needlePaint)

        // Center hub
        needlePaint.shader = null
        needlePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 4f, needlePaint)

        canvas.restore()
    }
}
