package com.k3weather.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class SolarArcView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dayProgress: Float = 0.5f // 0.0 to 1.0
    private var isDay: Boolean = true

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x40FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x38FFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFDE047.toInt()
    }

    private val arcPath = Path()

    fun setProgress(progress: Float, day: Boolean) {
        dayProgress = progress.coerceIn(0f, 1f)
        isDay = day
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val paddingHorizontal = 20f
        val horizonY = h - 16f
        val startX = paddingHorizontal
        val endX = w - paddingHorizontal
        val peakY = 8f

        // 1. Dashed Horizon line
        canvas.drawLine(startX - 8f, horizonY, endX + 8f, horizonY, horizonPaint)

        // 2. Solar curve (Quadratic Bezier)
        val controlX = w / 2f
        val controlY = peakY - 18f
        arcPath.reset()
        arcPath.moveTo(startX, horizonY)
        arcPath.quadTo(controlX, controlY, endX, horizonY)
        canvas.drawPath(arcPath, arcPaint)

        // 3. Sun marker position along quadratic Bezier curve
        // B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
        val t = dayProgress
        val sunX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
        val sunY = (1 - t) * (1 - t) * horizonY + 2 * (1 - t) * t * controlY + t * t * horizonY

        // Glowing outer coronal halo
        val glowRadius = 18f
        sunGlowPaint.shader = RadialGradient(
            sunX, sunY, glowRadius,
            intArrayOf(
                if (isDay) 0x66FBBF24.toInt() else 0x44818CF8.toInt(),
                0x00000000
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(sunX, sunY, glowRadius, sunGlowPaint)

        // Core sun disc
        sunCorePaint.color = if (isDay) 0xFFFDE047.toInt() else 0xFFC7D2FE.toInt()
        canvas.drawCircle(sunX, sunY, 7f, sunCorePaint)
    }
}
