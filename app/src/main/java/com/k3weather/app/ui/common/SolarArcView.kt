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

    private val density = resources.displayMetrics.density

    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0x33FFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        color = 0x47FFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val dropLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = 0x26FDE047.toInt()
        pathEffect = DashPathEffect(floatArrayOf(2f * density, 2f * density), 0f)
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

        val paddingHorizontal = 16f * density
        val horizonY = h - (12f * density)
        val startX = paddingHorizontal
        val endX = w - paddingHorizontal
        val peakY = 8f * density

        // 1. Dashed Horizon line
        canvas.drawLine(startX - (4f * density), horizonY, endX + (4f * density), horizonY, horizonPaint)

        // 2. Solar curve (Quadratic Bezier)
        val controlX = w / 2f
        val controlY = peakY - (12f * density)
        arcPath.reset()
        arcPath.moveTo(startX, horizonY)
        arcPath.quadTo(controlX, controlY, endX, horizonY)
        canvas.drawPath(arcPath, arcPaint)

        // 3. Sun marker position along quadratic Bezier curve
        // B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
        val t = dayProgress
        val sunX = (1 - t) * (1 - t) * startX + 2 * (1 - t) * t * controlX + t * t * endX
        val sunY = (1 - t) * (1 - t) * horizonY + 2 * (1 - t) * t * controlY + t * t * horizonY

        // 4. Subtle vertical drop line to horizon
        if (isDay && sunY < horizonY) {
            canvas.drawLine(sunX, sunY + (5f * density), sunX, horizonY, dropLinePaint)
        }

        // Glowing outer coronal halo
        val glowRadius = 14f * density
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
        canvas.drawCircle(sunX, sunY, 4.5f * density, sunCorePaint)
    }
}
