package com.k3weather.app.ui.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin

class HeroWeatherArtworkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Theme { SUNNY, RAINY, CLOUDY, NIGHT }

    private var theme: Theme = Theme.SUNNY
    private var sunRotation = 0f
    private var cloudOffset = 0f
    private var rainPhase = 0f
    private var starAlpha = 1f
    private var auraScale = 1f

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFDE047.toInt()
    }
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudBackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFEF08A.toInt()
    }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val cloudPath = Path()
    private val cloudBackPath = Path()
    private val moonPath = Path()

    private var animator: ValueAnimator? = null

    init {
        startAnimation()
    }

    fun setTheme(newTheme: Theme) {
        if (theme != newTheme) {
            theme = newTheme
            invalidate()
        }
    }

    private fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 10000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                sunRotation = fraction * 360f
                cloudOffset = sin(fraction * Math.PI.toFloat() * 2f) * 8f
                rainPhase = (fraction * 12f) % 1f
                starAlpha = 0.5f + 0.5f * sin(fraction * Math.PI.toFloat() * 6f)
                auraScale = 0.95f + 0.05f * sin(fraction * Math.PI.toFloat() * 4f)
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
        if (animator?.isStarted != true) startAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        val cy = h / 2f

        when (theme) {
            Theme.SUNNY -> drawSunny(canvas, cx, cy)
            Theme.RAINY -> drawRainy(canvas, cx, cy)
            Theme.NIGHT -> drawNight(canvas, cx, cy)
            Theme.CLOUDY -> drawCloudy(canvas, cx, cy)
        }
    }

    private fun drawSunny(canvas: Canvas, cx: Float, cy: Float) {
        // Outer pulsing amber aura
        val auraRadius = (cx * 0.85f) * auraScale
        auraPaint.shader = RadialGradient(
            cx, cy - 8f, auraRadius,
            intArrayOf(0x66FBBF24.toInt(), 0x22F59E0B.toInt(), 0x00000000),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy - 8f, auraRadius, auraPaint)

        // Rotating Sun Rays (12 rays)
        canvas.save()
        canvas.rotate(sunRotation, cx, cy - 8f)
        val rayInner = cx * 0.46f
        val rayOuter = cx * 0.62f
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val x1 = cx + (rayInner * cos(angle)).toFloat()
            val y1 = (cy - 8f) + (rayInner * sin(angle)).toFloat()
            val x2 = cx + (rayOuter * cos(angle)).toFloat()
            val y2 = (cy - 8f) + (rayOuter * sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }
        canvas.restore()

        // Glowing Sun Sphere
        val sunRadius = cx * 0.38f
        sunPaint.shader = RadialGradient(
            cx - sunRadius * 0.25f, (cy - 8f) - sunRadius * 0.25f, sunRadius * 1.3f,
            intArrayOf(0xFFFFF7AE.toInt(), 0xFFFACC15.toInt(), 0xFFF97316.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy - 8f, sunRadius, sunPaint)

        // Soft Drifting Foreground Cloud
        canvas.save()
        canvas.translate(0f, cloudOffset + 12f)
        drawCloudShape(canvas, cx - 10f, cy + 18f, scale = 0.85f, isFront = true)
        canvas.restore()
    }

    private fun drawRainy(canvas: Canvas, cx: Float, cy: Float) {
        // Cyan glowing blur aura
        auraPaint.shader = RadialGradient(
            cx, cy, cx * 0.85f,
            intArrayOf(0x5538BDF8.toInt(), 0x180E7490.toInt(), 0x00000000),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, cx * 0.85f, auraPaint)

        // Back Cloud (Slate)
        drawCloudShape(canvas, cx + 18f, cy - 14f, scale = 0.82f, isFront = false)

        // Front Cloud (Light Silver-Slate)
        drawCloudShape(canvas, cx - 12f, cy + 2f, scale = 0.95f, isFront = true)

        // Animated Raindrops (6 falling streams)
        val drops = arrayOf(
            floatArrayOf(cx - 45f, cy + 25f, 0.1f),
            floatArrayOf(cx - 20f, cy + 30f, 0.4f),
            floatArrayOf(cx + 5f, cy + 22f, 0.7f),
            floatArrayOf(cx + 30f, cy + 28f, 0.25f),
            floatArrayOf(cx - 32f, cy + 45f, 0.55f),
            floatArrayOf(cx + 15f, cy + 48f, 0.85f)
        )

        for (d in drops) {
            val progress = (rainPhase + d[2]) % 1f
            val startX = d[0] - progress * 8f
            val startY = d[1] + progress * 32f
            val endX = startX - 4f
            val endY = startY + 16f
            val alpha = (sin(progress * Math.PI.toFloat()) * 255).toInt().coerceIn(40, 255)
            rainPaint.color = (alpha shl 24) or 0x38BDF8
            canvas.drawLine(startX, startY, endX, endY, rainPaint)
        }
    }

    private fun drawNight(canvas: Canvas, cx: Float, cy: Float) {
        // Purple-Indigo glowing blur aura
        auraPaint.shader = RadialGradient(
            cx, cy, cx * 0.85f,
            intArrayOf(0x55818CF8.toInt(), 0x22312E81.toInt(), 0x00000000),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, cx * 0.85f, auraPaint)

        // Twinkling Stars
        val stars = arrayOf(
            floatArrayOf(cx - 55f, cy - 42f, 3f),
            floatArrayOf(cx + 50f, cy - 48f, 3.5f),
            floatArrayOf(cx + 62f, cy - 8f, 2.5f),
            floatArrayOf(cx - 62f, cy - 2f, 2.5f),
            floatArrayOf(cx - 15f, cy - 58f, 2.8f)
        )
        for (s in stars) {
            val a = (starAlpha * 255).toInt().coerceIn(60, 255)
            starPaint.color = (a shl 24) or 0xFEF08A
            canvas.drawCircle(s[0], s[1], s[2], starPaint)
        }

        // Glowing Crescent Moon using Path.op DIFFERENCE
        canvas.save()
        val moonRadius = cx * 0.36f
        val moonCenterY = cy - 10f
        val outerMoonPath = Path().apply {
            addCircle(cx, moonCenterY, moonRadius, Path.Direction.CW)
        }
        val innerCutterPath = Path().apply {
            addCircle(cx + moonRadius * 0.48f, moonCenterY - moonRadius * 0.28f, moonRadius * 0.88f, Path.Direction.CW)
        }
        moonPath.reset()
        moonPath.op(outerMoonPath, innerCutterPath, Path.Op.DIFFERENCE)

        moonPaint.shader = LinearGradient(
            cx - moonRadius, moonCenterY - moonRadius, cx + moonRadius, moonCenterY + moonRadius,
            intArrayOf(0xFFFEF9C3.toInt(), 0xFFFDE047.toInt(), 0xFFF59E0B.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(moonPath, moonPaint)
        canvas.restore()

        // Translucent Night Cloud
        drawNightCloudShape(canvas, cx - 10f, cy + 18f, scale = 0.88f)
    }

    private fun drawCloudy(canvas: Canvas, cx: Float, cy: Float) {
        // Soft Sky blur aura
        auraPaint.shader = RadialGradient(
            cx, cy, cx * 0.85f,
            intArrayOf(0x44BAE6FD.toInt(), 0x1538BDF8.toInt(), 0x00000000),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, cx * 0.85f, auraPaint)

        // Peeking Sun
        val sunRadius = cx * 0.28f
        sunPaint.shader = RadialGradient(
            cx - 28f, cy - 30f, sunRadius,
            intArrayOf(0xFFFFF7AE.toInt(), 0xFFFBBF24.toInt(), 0xFFF59E0B.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - 28f, cy - 30f, sunRadius, sunPaint)

        // Back Cloud
        drawCloudShape(canvas, cx + 22f, cy - 6f, scale = 0.84f, isFront = false)

        // Crisp Front Cloud
        drawCloudShape(canvas, cx - 12f, cy + 12f, scale = 0.95f, isFront = true)
    }

    private fun drawCloudShape(canvas: Canvas, x: Float, y: Float, scale: Float, isFront: Boolean) {
        val paint = if (isFront) cloudPaint else cloudBackPaint
        if (isFront) {
            paint.shader = LinearGradient(
                x, y - 30f * scale, x, y + 25f * scale,
                intArrayOf(0xF5FFFFFF.toInt(), 0xDFE2E8F0.toInt(), 0xCCCBD5E1.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        } else {
            paint.shader = LinearGradient(
                x, y - 25f * scale, x, y + 20f * scale,
                intArrayOf(0xCCE2E8F0.toInt(), 0xB394A3B8.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }

        // Three overlapping puffs for a sculpted cloud
        canvas.drawCircle(x - 28f * scale, y + 4f * scale, 18f * scale, paint)
        canvas.drawCircle(x + 28f * scale, y + 4f * scale, 16f * scale, paint)
        canvas.drawCircle(x, y - 8f * scale, 24f * scale, paint)
        canvas.drawRoundRect(
            x - 38f * scale, y - 2f * scale,
            x + 38f * scale, y + 20f * scale,
            12f * scale, 12f * scale,
            paint
        )
    }

    private fun drawNightCloudShape(canvas: Canvas, x: Float, y: Float, scale: Float) {
        cloudPaint.shader = LinearGradient(
            x, y - 25f * scale, x, y + 20f * scale,
            intArrayOf(0xB36366F1.toInt(), 0xCC312E81.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(x - 24f * scale, y + 4f * scale, 16f * scale, cloudPaint)
        canvas.drawCircle(x + 24f * scale, y + 4f * scale, 14f * scale, cloudPaint)
        canvas.drawCircle(x, y - 6f * scale, 22f * scale, cloudPaint)
        canvas.drawRoundRect(
            x - 34f * scale, y - 2f * scale,
            x + 34f * scale, y + 18f * scale,
            10f * scale, 10f * scale,
            cloudPaint
        )
    }
}
