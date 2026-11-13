package com.k3weather.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class RadarMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Layer { PRECIP, WIND, TEMP }

    private var layer: Layer = Layer.PRECIP
    private var step: Int = 0
    private var sweepAngle = 0f
    private var cityName = "Location"
    private var tempString = "--°"

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xEE0B1320.toInt()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x14FFFFFF.toInt()
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x3338BDF8.toInt()
    }
    private val landPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x4D1E293B.toInt()
    }
    private val landStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0x4D94A3B8.toInt()
    }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        color = 0x8038BDF8.toInt()
    }

    private val landPath1 = Path()
    private val landPath2 = Path()

    private var animator: ValueAnimator? = null

    init {
        startSweepAnimation()
    }

    fun setLayer(newLayer: Layer) {
        if (layer != newLayer) {
            layer = newLayer
            invalidate()
        }
    }

    fun setStep(newStep: Int) {
        step = newStep.coerceIn(0, 5)
        invalidate()
    }

    fun setCity(name: String, temp: String) {
        cityName = name
        tempString = temp
        invalidate()
    }

    private fun startSweepAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 4000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                sweepAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animator?.isStarted != true) startSweepAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f

        // Dark glass base
        canvas.drawRoundRect(0f, 0f, w, h, 36f, 36f, bgPaint)

        // Radar grid lines
        val stepSize = 40f
        var x = 0f
        while (x <= w) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x += stepSize
        }
        var y = 0f
        while (y <= h) {
            canvas.drawLine(0f, y, w, y, gridPaint)
            y += stepSize
        }

        // Stylized coastline shapes
        drawCoastlines(canvas, w, h)

        // Concentric radar range rings
        val r1 = minOf(w, h) * 0.22f
        val r2 = minOf(w, h) * 0.42f
        val r3 = minOf(w, h) * 0.62f
        canvas.drawCircle(cx, cy, r1, ringPaint)
        canvas.drawCircle(cx, cy, r2, ringPaint)
        canvas.drawCircle(cx, cy, r3, ringPaint)

        canvas.drawText("45 km", cx + r1 + 6f, cy - 6f, labelPaint)
        canvas.drawText("95 km", cx + r2 + 6f, cy - 6f, labelPaint)
        canvas.drawText("145 km", cx + r3 + 6f, cy - 6f, labelPaint)

        // Sweeping scanner beam
        canvas.save()
        canvas.rotate(sweepAngle, cx, cy)
        sweepPaint.shader = SweepGradient(
            cx, cy,
            intArrayOf(0x0038BDF8, 0x0038BDF8, 0x4038BDF8),
            floatArrayOf(0f, 0.85f, 1f)
        )
        canvas.drawCircle(cx, cy, r3 * 1.05f, sweepPaint)
        canvas.restore()

        // Doppler weather cells based on active layer & timeline step
        drawWeatherCells(canvas, cx, cy, w, h)

        // Center location marker pin
        drawCenterPin(canvas, cx, cy)
    }

    private fun drawCoastlines(canvas: Canvas, w: Float, h: Float) {
        landPath1.reset()
        landPath1.moveTo(0f, h * 0.15f)
        landPath1.quadTo(w * 0.3f, h * 0.3f, w * 0.42f, h * 0.6f)
        landPath1.quadTo(w * 0.65f, h * 1.0f, 0f, h)
        landPath1.close()
        canvas.drawPath(landPath1, landPaint)
        canvas.drawPath(landPath1, landStrokePaint)

        landPath2.reset()
        landPath2.moveTo(w * 0.7f, 0f)
        landPath2.quadTo(w * 0.8f, h * 0.25f, w * 0.9f, h * 0.45f)
        landPath2.lineTo(w, h * 0.7f)
        landPath2.lineTo(w, 0f)
        landPath2.close()
        canvas.drawPath(landPath2, landPaint)
        canvas.drawPath(landPath2, landStrokePaint)
    }

    private fun drawWeatherCells(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float) {
        val dx = (step - 2) * 16f
        val dy = (step - 2) * -10f

        when (layer) {
            Layer.PRECIP -> {
                // Cell 1: Emerald Rain
                blobPaint.shader = RadialGradient(
                    cx - 70f + dx, cy - 50f + dy, 90f,
                    intArrayOf(0x7734D399.toInt(), 0x3310B981.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx - 70f + dx, cy - 50f + dy, 90f, blobPaint)

                // Cell 2: Heavy Amber Rain Core
                blobPaint.shader = RadialGradient(
                    cx - 50f + dx, cy - 40f + dy, 55f,
                    intArrayOf(0x88FBBF24.toInt(), 0x44F59E0B.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx - 50f + dx, cy - 40f + dy, 55f, blobPaint)

                // Cell 3: Sky Blue Showers
                blobPaint.shader = RadialGradient(
                    cx + 75f + dx, cy + 45f + dy, 80f,
                    intArrayOf(0x6638BDF8.toInt(), 0x220284C7.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx + 75f + dx, cy + 45f + dy, 80f, blobPaint)
            }
            Layer.WIND -> {
                // Cyan stream
                blobPaint.shader = RadialGradient(
                    cx - 60f + dx * 1.5f, cy - 40f + dy * 1.5f, 110f,
                    intArrayOf(0x6622D3EE.toInt(), 0x2206B6D4.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx - 60f + dx * 1.5f, cy - 40f + dy * 1.5f, 110f, blobPaint)

                // Indigo stream
                blobPaint.shader = RadialGradient(
                    cx + 65f + dx * 1.2f, cy + 50f + dy * 1.2f, 95f,
                    intArrayOf(0x66818CF8.toInt(), 0x224F46E5.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx + 65f + dx * 1.2f, cy + 50f + dy * 1.2f, 95f, blobPaint)
            }
            Layer.TEMP -> {
                // Warm Orange thermal pocket
                blobPaint.shader = RadialGradient(
                    cx - 50f + dx * 0.8f, cy - 40f + dy * 0.8f, 120f,
                    intArrayOf(0x66F97316.toInt(), 0x22EA580C.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx - 50f + dx * 0.8f, cy - 40f + dy * 0.8f, 120f, blobPaint)

                // Solar Amber pocket
                blobPaint.shader = RadialGradient(
                    cx + 60f + dx * 0.8f, cy + 40f + dy * 0.8f, 100f,
                    intArrayOf(0x66FBBF24.toInt(), 0x22F59E0B.toInt(), 0x00000000),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawCircle(cx + 60f + dx * 0.8f, cy + 40f + dy * 0.8f, 100f, blobPaint)
            }
        }
    }

    private fun drawCenterPin(canvas: Canvas, cx: Float, cy: Float) {
        // Center city pill tag
        val pillText = "$cityName • $tempString"
        val textWidth = textPaint.measureText(pillText)
        val pillPadH = 20f
        val pillH = 36f
        val pillLeft = cx - (textWidth / 2f) - pillPadH
        val pillRight = cx + (textWidth / 2f) + pillPadH
        val pillTop = cy - 42f
        val pillBottom = pillTop + pillH

        pinPaint.style = Paint.Style.FILL
        pinPaint.color = 0xEE0B1320.toInt()
        canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, 18f, 18f, pinPaint)

        pinPaint.style = Paint.Style.STROKE
        pinPaint.strokeWidth = 2f
        pinPaint.color = 0x66FFFFFF.toInt()
        canvas.drawRoundRect(pillLeft, pillTop, pillRight, pillBottom, 18f, 18f, pinPaint)

        // Pulsing emerald beacon dot inside pill
        pinPaint.style = Paint.Style.FILL
        pinPaint.color = 0xFF34D399.toInt()
        canvas.drawCircle(pillLeft + 14f, pillTop + 18f, 4f, pinPaint)

        canvas.drawText(pillText, cx + 4f, pillTop + 25f, textPaint)

        // Center location dot
        pinPaint.style = Paint.Style.FILL
        pinPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, 7f, pinPaint)

        pinPaint.style = Paint.Style.STROKE
        pinPaint.strokeWidth = 4f
        pinPaint.color = 0xFF38BDF8.toInt()
        canvas.drawCircle(cx, cy, 7f, pinPaint)
    }
}
