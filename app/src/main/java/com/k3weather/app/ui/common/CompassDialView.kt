package com.k3weather.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.sin

class CompassDialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDegree = 0f
    private var targetDegree = 0f
    private var animator: ValueAnimator? = null

    private val density = resources.displayMetrics.density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x3D06131E.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0x33FFFFFF.toInt()
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        strokeCap = Paint.Cap.ROUND
        color = 0x26FFFFFF.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9.5f * density
        textAlign = Paint.Align.CENTER
        color = 0x99FFFFFF.toInt()
        isFakeBoldText = true
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f * density
        textAlign = Paint.Align.CENTER
        color = 0xFF38BDF8.toInt()
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
        val radius = (minOf(w, h) / 2f) - (2f * density)

        // Dark translucent circular dial with fine glass rim
        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, ringPaint)

        // Graduated dial ticks every 30 degrees
        for (i in 0 until 12) {
            val isCardinal = i % 3 == 0
            val angle = Math.toRadians((i * 30).toDouble())
            val innerR = radius - (if (isCardinal) 5f * density else 3f * density)
            val outerR = radius - (1f * density)
            val x1 = cx + (innerR * cos(angle)).toFloat()
            val y1 = cy + (innerR * sin(angle)).toFloat()
            val x2 = cx + (outerR * cos(angle)).toFloat()
            val y2 = cy + (outerR * sin(angle)).toFloat()
            tickPaint.color = if (isCardinal) 0x4DFFFFFF.toInt() else 0x24FFFFFF.toInt()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Cardinal directions (localized N/S/E/W or B/N/Đ/T)
        val isVi = resources.configuration.locales[0]?.language == "vi"
        val labelN = if (isVi) "B" else "N"
        val labelS = if (isVi) "N" else "S"
        val labelE = if (isVi) "Đ" else "E"
        val labelW = if (isVi) "T" else "W"

        val labelInset = 7f * density
        canvas.drawText(labelN, cx, cy - radius + labelInset + (7f * density), northPaint)
        canvas.drawText(labelS, cx, cy + radius - labelInset, textPaint)
        canvas.drawText(labelE, cx + radius - labelInset - (2f * density), cy + (3.5f * density), textPaint)
        canvas.drawText(labelW, cx - radius + labelInset + (2f * density), cy + (3.5f * density), textPaint)

        // Center needle rotated to wind direction
        canvas.save()
        canvas.rotate(currentDegree, cx, cy)

        val needleLen = radius * 0.62f
        val needleHalfWidth = 4.5f * density

        needlePath.reset()
        // North/pointer arrow
        needlePath.moveTo(cx, cy - needleLen)
        needlePath.lineTo(cx + needleHalfWidth, cy)
        needlePath.lineTo(cx, cy - (3f * density))
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
        needlePath.moveTo(cx, cy + needleLen * 0.68f)
        needlePath.lineTo(cx + needleHalfWidth * 0.75f, cy)
        needlePath.lineTo(cx, cy + (3f * density))
        needlePath.lineTo(cx - needleHalfWidth * 0.75f, cy)
        needlePath.close()

        needlePaint.shader = LinearGradient(
            cx, cy, cx, cy + needleLen * 0.68f,
            0x80FFFFFF.toInt(), 0x20FFFFFF.toInt(),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(needlePath, needlePaint)

        // Center hub
        needlePaint.shader = null
        needlePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 3f * density, needlePaint)

        canvas.restore()
    }
}
