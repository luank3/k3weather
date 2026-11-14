package com.k3weather.app.ui.common

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
import com.k3weather.app.core.model.AppColorScheme
import com.k3weather.app.data.mapper.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WeatherEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var condition: WeatherCondition = WeatherCondition.CLEAR
    private var isDay: Boolean = true
    private var reduceMotion: Boolean = false
    private var colorScheme: AppColorScheme = AppColorScheme.CYAN

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val horizonGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var bgGradient: LinearGradient? = null
    private var horizonGlowGradient: RadialGradient? = null
    private var sunGradient: RadialGradient? = null
    private var sunCoreGradient: RadialGradient? = null
    private var moonGradient: RadialGradient? = null
    private var cloudUnitGradient: RadialGradient? = null
    private var fogBandGradient: LinearGradient? = null
    private val shaderMatrix = Matrix()
    private val boltPath = Path()

    private class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var speed: Float = 0f,
        var size: Float = 0f,
        var alpha: Int = 255,
        var length: Float = 0f,
        var depth: Float = 1f
    )

    private class CloudPuff(
        val baseX: Float,
        val baseY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val speed: Float,
        val phase: Float,
        val alphaScale: Float
    )

    private val rainDrops = Array(90) { Particle() }
    private val stars = Array(72) { Particle() }
    private val snowFlakes = Array(58) { Particle() }
    private val hailStones = Array(34) { Particle() }

    private val cloudPuffs = arrayOf(
        CloudPuff(0.18f, 0.11f, 0.42f, 0.13f, 0.00022f, 0.0f, 1.0f),
        CloudPuff(0.76f, 0.14f, 0.46f, 0.14f, -0.00018f, 1.4f, 0.9f),
        CloudPuff(0.45f, 0.21f, 0.50f, 0.15f, 0.00015f, 2.8f, 0.75f),
        CloudPuff(0.12f, 0.29f, 0.38f, 0.12f, -0.00020f, 4.1f, 0.65f),
        CloudPuff(0.84f, 0.32f, 0.44f, 0.13f, 0.00017f, 5.2f, 0.70f)
    )

    private var lightningAlpha = 0
    private var nextLightningFrame = 150
    private var boltStartX = 0.65f
    private var transitionStartMs = 0L

    private val crescentGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mistMotePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val branchPath = Path()

    init {
        initParticles()
    }

    private fun initParticles() {
        val rnd = Random(42)
        for (p in rainDrops) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.depth = 0.45f + rnd.nextFloat() * 0.55f
            p.speed = (0.018f + rnd.nextFloat() * 0.024f) * p.depth
            p.length = (26f + rnd.nextFloat() * 42f) * p.depth
            p.alpha = (65 + rnd.nextInt(135) * p.depth).toInt().coerceIn(50, 210)
        }
        for (p in stars) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat() * 0.72f
            p.size = 1.0f + rnd.nextFloat() * 2.4f
            p.alpha = 80 + rnd.nextInt(170)
            p.speed = 0.6f + rnd.nextFloat() * 1.9f
        }
        for (p in snowFlakes) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.depth = 0.4f + rnd.nextFloat() * 0.6f
            p.speed = (0.0022f + rnd.nextFloat() * 0.0050f) * p.depth
            p.size = (2.2f + rnd.nextFloat() * 4.5f) * p.depth
            p.alpha = (105 + rnd.nextInt(140) * p.depth).toInt().coerceIn(85, 245)
        }
        for (p in hailStones) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.025f + rnd.nextFloat() * 0.032f
            p.size = 2.8f + rnd.nextFloat() * 3.4f
            p.alpha = 155 + rnd.nextInt(90)
        }
    }

    fun setWeather(
        condition: WeatherCondition,
        isDay: Boolean,
        reduceMotion: Boolean = false,
        colorScheme: AppColorScheme = AppColorScheme.CYAN
    ) {
        if (this.condition != condition || this.isDay != isDay || this.reduceMotion != reduceMotion || this.colorScheme != colorScheme) {
            val changedAtmosphere = this.condition != condition || this.isDay != isDay
            this.condition = condition
            this.isDay = isDay
            this.reduceMotion = reduceMotion
            this.colorScheme = colorScheme
            invalidateShaders()
            if (changedAtmosphere && !reduceMotion) {
                transitionStartMs = System.currentTimeMillis()
                alpha = 0.86f
                animate().alpha(1f).setDuration(260L).setInterpolator(MotionHelper.FLUID_EASE).start()
            } else {
                alpha = 1f
            }
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateShaders()
    }

    private fun invalidateShaders() {
        bgGradient = null
        horizonGlowGradient = null
        sunGradient = null
        sunCoreGradient = null
        moonGradient = null
        cloudUnitGradient = null
        fogBandGradient = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawBackgroundGradient(canvas, w, h)
        drawAmbientAccentGlow(canvas, w, h)

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = true) else drawStars(canvas, w, h, withMoon = true)
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = false) else drawStars(canvas, w, h, withMoon = true)
                drawVolumetricClouds(canvas, w, h, density = 1)
            }
            WeatherCondition.CLOUDY -> {
                if (!isDay) drawStars(canvas, w, h, withMoon = false)
                drawVolumetricClouds(canvas, w, h, density = 2)
            }
            WeatherCondition.FOG -> {
                drawVolumetricClouds(canvas, w, h, density = 1)
                drawAtmosphericFog(canvas, w, h)
            }
            WeatherCondition.DRIZZLE -> {
                drawVolumetricClouds(canvas, w, h, density = 1)
                drawDrizzle(canvas, w, h)
            }
            WeatherCondition.RAIN -> {
                drawVolumetricClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = false)
            }
            WeatherCondition.HEAVY_RAIN -> {
                drawVolumetricClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = true)
            }
            WeatherCondition.SLEET -> {
                drawVolumetricClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = false)
                drawHail(canvas, w, h)
            }
            WeatherCondition.SNOW -> {
                drawVolumetricClouds(canvas, w, h, density = 1)
                drawSnow(canvas, w, h)
            }
            WeatherCondition.THUNDER -> {
                drawVolumetricClouds(canvas, w, h, density = 2)
                drawThunder(canvas, w, h, withHail = false)
            }
            WeatherCondition.THUNDER_HAIL -> {
                drawVolumetricClouds(canvas, w, h, density = 2)
                drawThunder(canvas, w, h, withHail = true)
            }
        }

        if (!reduceMotion && shouldAnimate()) {
            postInvalidateOnAnimation()
        }
    }

    private fun shouldAnimate(): Boolean {
        return when (condition) {
            WeatherCondition.CLEAR -> true
            WeatherCondition.PARTLY_CLOUDY,
            WeatherCondition.CLOUDY,
            WeatherCondition.FOG,
            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.SLEET,
            WeatherCondition.SNOW,
            WeatherCondition.THUNDER,
            WeatherCondition.THUNDER_HAIL -> true
        }
    }

    private fun drawBackgroundGradient(canvas: Canvas, w: Float, h: Float) {
        if (bgGradient == null) {
            val colors = when (condition) {
                WeatherCondition.CLEAR -> {
                    if (isDay) intArrayOf(
                        Color.rgb(11, 74, 158),
                        Color.rgb(24, 114, 200),
                        Color.rgb(46, 148, 224),
                        Color.rgb(86, 178, 238)
                    ) else intArrayOf(
                        Color.rgb(5, 11, 30),
                        Color.rgb(11, 23, 54),
                        Color.rgb(18, 37, 78),
                        Color.rgb(28, 52, 98)
                    )
                }
                WeatherCondition.PARTLY_CLOUDY -> {
                    if (isDay) intArrayOf(
                        Color.rgb(18, 66, 128),
                        Color.rgb(33, 96, 164),
                        Color.rgb(52, 124, 190),
                        Color.rgb(74, 144, 204)
                    ) else intArrayOf(
                        Color.rgb(8, 15, 36),
                        Color.rgb(15, 29, 60),
                        Color.rgb(24, 44, 82),
                        Color.rgb(34, 58, 96)
                    )
                }
                WeatherCondition.CLOUDY -> {
                    if (isDay) intArrayOf(
                        Color.rgb(30, 56, 86),
                        Color.rgb(45, 76, 110),
                        Color.rgb(62, 96, 130),
                        Color.rgb(78, 112, 144)
                    ) else intArrayOf(
                        Color.rgb(10, 18, 32),
                        Color.rgb(17, 30, 50),
                        Color.rgb(26, 43, 68),
                        Color.rgb(34, 54, 82)
                    )
                }
                WeatherCondition.FOG -> {
                    if (isDay) intArrayOf(
                        Color.rgb(40, 64, 84),
                        Color.rgb(58, 86, 108),
                        Color.rgb(78, 106, 128),
                        Color.rgb(94, 122, 142)
                    ) else intArrayOf(
                        Color.rgb(16, 28, 42),
                        Color.rgb(26, 42, 58),
                        Color.rgb(38, 56, 74),
                        Color.rgb(48, 68, 86)
                    )
                }
                WeatherCondition.DRIZZLE -> intArrayOf(
                    Color.rgb(16, 38, 62),
                    Color.rgb(26, 56, 86),
                    Color.rgb(38, 72, 104),
                    Color.rgb(50, 86, 118)
                )
                WeatherCondition.RAIN -> intArrayOf(
                    Color.rgb(12, 29, 50),
                    Color.rgb(20, 44, 72),
                    Color.rgb(28, 58, 90),
                    Color.rgb(38, 70, 102)
                )
                WeatherCondition.HEAVY_RAIN -> intArrayOf(
                    Color.rgb(8, 19, 34),
                    Color.rgb(14, 30, 50),
                    Color.rgb(22, 42, 66),
                    Color.rgb(30, 52, 78)
                )
                WeatherCondition.SLEET -> intArrayOf(
                    Color.rgb(18, 34, 54),
                    Color.rgb(30, 52, 76),
                    Color.rgb(44, 68, 94),
                    Color.rgb(56, 82, 108)
                )
                WeatherCondition.SNOW -> intArrayOf(
                    Color.rgb(24, 46, 76),
                    Color.rgb(40, 68, 104),
                    Color.rgb(58, 90, 128),
                    Color.rgb(74, 108, 146)
                )
                WeatherCondition.THUNDER -> intArrayOf(
                    Color.rgb(9, 13, 28),
                    Color.rgb(18, 25, 48),
                    Color.rgb(28, 36, 66),
                    Color.rgb(36, 46, 78)
                )
                WeatherCondition.THUNDER_HAIL -> intArrayOf(
                    Color.rgb(6, 10, 22),
                    Color.rgb(13, 20, 38),
                    Color.rgb(22, 30, 54),
                    Color.rgb(30, 40, 66)
                )
            }

            bgGradient = LinearGradient(
                0f, 0f, 0f, h,
                colors,
                floatArrayOf(0f, 0.36f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            gradientPaint.shader = bgGradient
        }

        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    /** Subtle liquid-glass atmospheric horizon bloom tinted by the active color scheme */
    private fun drawAmbientAccentGlow(canvas: Canvas, w: Float, h: Float) {
        if (horizonGlowGradient == null) {
            val accent = colorScheme.primaryAccent
            val r = Color.red(accent)
            val g = Color.green(accent)
            val b = Color.blue(accent)
            horizonGlowGradient = RadialGradient(
                w * 0.5f, h * 0.32f, w * 0.85f,
                intArrayOf(
                    Color.argb(26, r, g, b),
                    Color.argb(10, r, g, b),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            horizonGlowPaint.shader = horizonGlowGradient
        }
        canvas.drawRect(0f, 0f, w, h, horizonGlowPaint)
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, fullHalo: Boolean) {
        val cx = w * 0.82f
        val cy = h * 0.13f
        val pulse = if (reduceMotion) 1f else 1f + sin(System.currentTimeMillis() * 0.0012).toFloat() * 0.03f
        val outerRadius = (if (fullHalo) w * 0.52f else w * 0.36f) * pulse
        val coreRadius = if (fullHalo) w * 0.13f else w * 0.095f

        if (sunGradient == null) {
            val alphaCore = if (fullHalo) 130 else 95
            sunGradient = RadialGradient(
                cx, cy, outerRadius,
                intArrayOf(
                    Color.argb(alphaCore, 255, 244, 150),
                    Color.argb(55, 255, 218, 100),
                    Color.argb(18, 255, 190, 90),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.32f, 0.68f, 1f),
                Shader.TileMode.CLAMP
            )
            sunPaint.shader = sunGradient
        }

        if (sunCoreGradient == null) {
            val coreAlpha = if (fullHalo) 215 else 165
            sunCoreGradient = RadialGradient(
                cx, cy, coreRadius,
                intArrayOf(
                    Color.argb(coreAlpha, 255, 254, 240),
                    Color.argb((coreAlpha * 0.45f).toInt(), 255, 240, 120),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            sunCorePaint.shader = sunCoreGradient
        }

        // Soft atmospheric corona glow (avoids hard-edged rectangular ray clipping on cards)
        if (fullHalo) {
            val coronaRadius = outerRadius * 1.18f
            rayPaint.style = Paint.Style.FILL
            rayPaint.shader = RadialGradient(
                cx, cy, coronaRadius,
                intArrayOf(
                    Color.argb(42, 255, 248, 200),
                    Color.argb(16, 255, 224, 130),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, coronaRadius, rayPaint)
        }

        canvas.drawCircle(cx, cy, outerRadius, sunPaint)
        canvas.drawCircle(cx, cy, coreRadius, sunCorePaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, withMoon: Boolean) {
        if (withMoon) {
            val mcx = w * 0.82f
            val mcy = h * 0.14f
            val mRadius = w * 0.28f
            if (moonGradient == null) {
                moonGradient = RadialGradient(
                    mcx, mcy, mRadius,
                    intArrayOf(
                        Color.argb(110, 232, 245, 255),
                        Color.argb(42, 186, 230, 253),
                        Color.argb(12, 125, 211, 252),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                moonPaint.shader = moonGradient
            }
            canvas.drawCircle(mcx, mcy, mRadius, moonPaint)
        }

        particlePaint.style = Paint.Style.FILL
        val now = System.currentTimeMillis()
        for (star in stars) {
            val sx = star.x * w
            val sy = star.y * h
            val alpha = if (reduceMotion) {
                star.alpha
            } else {
                (star.alpha + sin(now * 0.0022 * star.speed).toFloat() * 44f).toInt().coerceIn(30, 255)
            }
            particlePaint.color = Color.argb(alpha, 240, 249, 255)
            canvas.drawCircle(sx, sy, star.size, particlePaint)
        }
    }

    /**
     * Renders soft volumetric clouds using radial gradients that fade completely to transparent
     * at their edges — eliminating hard geometric oval borders.
     */
    private fun drawVolumetricClouds(canvas: Canvas, w: Float, h: Float, density: Int) {
        if (cloudUnitGradient == null) {
            cloudUnitGradient = RadialGradient(
                0f, 0f, 1f,
                intArrayOf(
                    Color.argb(58, 244, 250, 255),
                    Color.argb(30, 224, 240, 252),
                    Color.argb(10, 200, 224, 246),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 0.78f, 1f),
                Shader.TileMode.CLAMP
            )
        }

        val grad = cloudUnitGradient ?: return
        val now = System.currentTimeMillis()
        val count = if (density > 1) cloudPuffs.size else 3

        for (i in 0 until count) {
            val puff = cloudPuffs[i]
            val drift = if (reduceMotion) 0f else sin(now * puff.speed + puff.phase).toFloat() * (w * 0.06f)
            val cx = puff.baseX * w + drift
            val cy = puff.baseY * h
            val rx = puff.radiusX * w
            val ry = puff.radiusY * h

            shaderMatrix.reset()
            shaderMatrix.postScale(rx, ry)
            shaderMatrix.postTranslate(cx, cy)
            grad.setLocalMatrix(shaderMatrix)

            cloudPaint.shader = grad
            cloudPaint.alpha = (255 * puff.alphaScale * (if (density > 1) 1.15f else 0.85f)).toInt().coerceIn(0, 255)
            canvas.drawRect(cx - rx, cy - ry, cx + rx, cy + ry, cloudPaint)
        }
    }

    /** Smooth drifting mist banks + soft glowing fog haze */
    private fun drawAtmosphericFog(canvas: Canvas, w: Float, h: Float) {
        if (fogBandGradient == null) {
            fogBandGradient = LinearGradient(
                0f, -1f, 0f, 1f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(38, 220, 236, 248),
                    Color.argb(62, 232, 244, 252),
                    Color.argb(38, 220, 236, 248),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        val grad = fogBandGradient ?: return
        val bands = floatArrayOf(0.16f, 0.30f, 0.46f, 0.62f, 0.78f)
        val bandHeights = floatArrayOf(0.12f, 0.16f, 0.18f, 0.15f, 0.14f)
        val now = System.currentTimeMillis()

        for (i in bands.indices) {
            val swayY = if (reduceMotion) 0f else sin(now * 0.00035 + i * 1.3).toFloat() * (h * 0.015f)
            val cy = bands[i] * h + swayY
            val halfH = bandHeights[i] * h
            shaderMatrix.reset()
            shaderMatrix.postScale(1f, halfH)
            shaderMatrix.postTranslate(0f, cy)
            grad.setLocalMatrix(shaderMatrix)
            fogPaint.shader = grad
            fogPaint.alpha = (210 + (if (reduceMotion) 0 else (sin(now * 0.0005 + i) * 35).toInt())).coerceIn(150, 250)
            canvas.drawRect(0f, cy - halfH, w, cy + halfH, fogPaint)
        }

        // Subtle drifting foreground mist motes for atmospheric depth
        mistMotePaint.style = Paint.Style.FILL
        for (i in 0 until minOf(18, snowFlakes.size)) {
            val p = snowFlakes[i]
            val driftX = if (reduceMotion) 0f else ((now * 0.000018f * (i % 3 + 1)) % 1f)
            val mx = ((p.x + driftX) % 1f) * w
            val my = (0.15f + p.y * 0.7f) * h
            mistMotePaint.color = Color.argb(26, 224, 242, 254)
            canvas.drawCircle(mx, my, p.size * 5.5f, mistMotePaint)
        }
    }

    private fun drawDrizzle(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.STROKE
        particlePaint.strokeWidth = 1.5f
        particlePaint.strokeCap = Paint.Cap.ROUND
        val count = minOf(50, rainDrops.size)
        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h
            particlePaint.color = Color.argb((drop.alpha * 0.72f).toInt(), 186, 230, 253)
            val len = drop.length * 0.44f
            canvas.drawLine(sx, sy, sx - 2.5f, sy + len, particlePaint)

            if (!reduceMotion) {
                drop.y += drop.speed * 0.58f
                if (drop.y > 1f) {
                    drop.y = -0.04f
                    drop.x = (drop.x + 0.11f) % 1f
                }
            }
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, isHeavy: Boolean) {
        particlePaint.style = Paint.Style.STROKE
        particlePaint.strokeCap = Paint.Cap.ROUND

        val slant = if (isHeavy) 8.5f else 5.5f
        val count = if (isHeavy) rainDrops.size else (rainDrops.size * 3 / 4)

        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h

            particlePaint.strokeWidth = (if (isHeavy) 2.5f else 1.9f) * drop.depth
            particlePaint.color = Color.argb(drop.alpha, 186, 230, 253)
            val dropLen = if (isHeavy) drop.length * 1.25f else drop.length
            canvas.drawLine(sx, sy, sx - slant * drop.depth, sy + dropLen, particlePaint)

            if (!reduceMotion) {
                val speedMultiplier = if (isHeavy) 1.30f else 1.0f
                drop.y += drop.speed * speedMultiplier
                if (drop.y > 1f) {
                    drop.y = -0.05f
                    drop.x = (drop.x + 0.13f) % 1f
                }
            }
        }

        if (isHeavy) {
            splashPaint.style = Paint.Style.STROKE
            splashPaint.strokeWidth = 1.2f
            splashPaint.color = Color.argb(28, 186, 230, 253)
            canvas.drawOval(w * 0.10f, h - 30f, w * 0.34f, h - 12f, splashPaint)
            canvas.drawOval(w * 0.56f, h - 42f, w * 0.84f, h - 22f, splashPaint)
        }
    }

    private fun drawHail(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.FILL
        for (hail in hailStones) {
            val sx = hail.x * w
            val sy = hail.y * h

            particlePaint.color = Color.argb(hail.alpha, 228, 246, 255)
            canvas.drawCircle(sx, sy, hail.size, particlePaint)

            if (!reduceMotion) {
                hail.y += hail.speed
                if (hail.y > 1f) {
                    hail.y = -0.05f
                    hail.x = (hail.x + 0.19f) % 1f
                }
            }
        }
    }

    private fun drawThunder(canvas: Canvas, w: Float, h: Float, withHail: Boolean) {
        drawRain(canvas, w, h, isHeavy = true)
        if (withHail) {
            drawHail(canvas, w, h)
        }

        if (!reduceMotion) {
            nextLightningFrame--
            if (nextLightningFrame <= 0) {
                lightningAlpha = 140
                boltStartX = 0.25f + Random.nextFloat() * 0.55f
                nextLightningFrame = 150 + Random.nextInt(170)
            }
        }

        if (lightningAlpha > 0) {
            lightningPaint.color = Color.argb((lightningAlpha * 0.52f).toInt(), 210, 232, 255)
            canvas.drawRect(0f, 0f, w, h, lightningPaint)

            // Branched lightning bolt with electric cyan outer glow + crisp white core
            boltPath.reset()
            branchPath.reset()
            val bx = boltStartX * w
            val p1x = bx - w * 0.045f
            val p1y = h * 0.09f
            val p2x = bx + w * 0.025f
            val p2y = h * 0.15f
            val p3x = bx - w * 0.055f
            val p3y = h * 0.27f

            boltPath.moveTo(bx, 0f)
            boltPath.lineTo(p1x, p1y)
            boltPath.lineTo(p2x, p2y)
            boltPath.lineTo(p3x, p3y)

            branchPath.moveTo(p1x, p1y)
            branchPath.lineTo(p1x - w * 0.05f, p1y + h * 0.06f)

            boltGlowPaint.strokeWidth = 9f
            boltGlowPaint.color = Color.argb((lightningAlpha * 0.45f).toInt(), 125, 211, 252)
            canvas.drawPath(boltPath, boltGlowPaint)
            canvas.drawPath(branchPath, boltGlowPaint)

            boltPaint.strokeWidth = 2.6f
            boltPaint.color = Color.argb(lightningAlpha.coerceAtMost(235), 245, 250, 255)
            canvas.drawPath(boltPath, boltPaint)
            boltPaint.strokeWidth = 1.4f
            canvas.drawPath(branchPath, boltPaint)

            if (!reduceMotion) {
                lightningAlpha = (lightningAlpha - 16).coerceAtLeast(0)
            }
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.FILL

        for (flake in snowFlakes) {
            val sx = flake.x * w
            val sy = flake.y * h

            particlePaint.color = Color.argb(flake.alpha, 242, 249, 255)
            canvas.drawCircle(sx, sy, flake.size, particlePaint)
            if (flake.depth > 0.78f) {
                particlePaint.color = Color.argb((flake.alpha * 0.25f).toInt(), 210, 240, 255)
                canvas.drawCircle(sx, sy, flake.size * 2.1f, particlePaint)
            }

            if (!reduceMotion) {
                flake.y += flake.speed
                flake.x += sin(flake.y * 12.0).toFloat() * 0.00065f
                if (flake.y > 1f) {
                    flake.y = -0.03f
                    flake.x = (flake.x + 0.17f) % 1f
                }
            }
        }
    }
}
