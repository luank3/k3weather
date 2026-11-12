package com.k3weather.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.k3weather.app.core.model.AppColorScheme
import com.k3weather.app.data.mapper.WeatherCondition
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

    // Paints
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cached Shaders
    private var bgGradient: LinearGradient? = null
    private var sunGradient: RadialGradient? = null
    private var sunCoreGradient: RadialGradient? = null
    private var moonGradient: RadialGradient? = null

    // Particles
    private class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var speed: Float = 0f,
        var size: Float = 0f,
        var alpha: Int = 255,
        var length: Float = 0f,
        var depth: Float = 1f
    )

    private val rainDrops = Array(85) { Particle() }
    private val stars = Array(64) { Particle() }
    private val snowFlakes = Array(52) { Particle() }
    private val hailStones = Array(32) { Particle() }

    private var lightningAlpha = 0
    private var nextLightningFrame = 160

    init {
        initParticles()
    }

    private fun initParticles() {
        val rnd = Random(42)
        for (p in rainDrops) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.depth = 0.5f + rnd.nextFloat() * 0.5f
            p.speed = (0.016f + rnd.nextFloat() * 0.024f) * p.depth
            p.length = (24f + rnd.nextFloat() * 38f) * p.depth
            p.alpha = (70 + rnd.nextInt(135) * p.depth).toInt().coerceIn(55, 220)
        }
        for (p in stars) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat() * 0.74f // Upper 74% of sky
            p.size = 1.2f + rnd.nextFloat() * 2.5f
            p.alpha = 85 + rnd.nextInt(165)
            p.speed = 0.5f + rnd.nextFloat() * 1.8f
        }
        for (p in snowFlakes) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.depth = 0.45f + rnd.nextFloat() * 0.55f
            p.speed = (0.0025f + rnd.nextFloat() * 0.0055f) * p.depth
            p.size = (2.2f + rnd.nextFloat() * 4.4f) * p.depth
            p.alpha = (110 + rnd.nextInt(135) * p.depth).toInt().coerceIn(90, 245)
        }
        for (p in hailStones) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.024f + rnd.nextFloat() * 0.034f
            p.size = 3.2f + rnd.nextFloat() * 3.6f
            p.alpha = 160 + rnd.nextInt(90)
        }
    }

    fun setWeather(
        condition: WeatherCondition,
        isDay: Boolean,
        reduceMotion: Boolean = false,
        colorScheme: AppColorScheme = AppColorScheme.CYAN
    ) {
        if (this.condition != condition || this.isDay != isDay || this.reduceMotion != reduceMotion || this.colorScheme != colorScheme) {
            this.condition = condition
            this.isDay = isDay
            this.reduceMotion = reduceMotion
            this.colorScheme = colorScheme
            bgGradient = null
            sunGradient = null
            sunCoreGradient = null
            moonGradient = null
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgGradient = null
        sunGradient = null
        sunCoreGradient = null
        moonGradient = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawBackgroundGradient(canvas, w, h)

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = true) else drawStars(canvas, w, h, withMoon = true)
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = false) else drawStars(canvas, w, h, withMoon = true)
                drawClouds(canvas, w, h, density = 1)
            }
            WeatherCondition.CLOUDY -> {
                if (!isDay) drawStars(canvas, w, h, withMoon = false)
                drawClouds(canvas, w, h, density = 2)
            }
            WeatherCondition.FOG -> {
                drawClouds(canvas, w, h, density = 1)
                drawFog(canvas, w, h)
            }
            WeatherCondition.DRIZZLE -> {
                drawClouds(canvas, w, h, density = 1)
                drawDrizzle(canvas, w, h)
            }
            WeatherCondition.RAIN -> {
                drawClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = false)
            }
            WeatherCondition.HEAVY_RAIN -> {
                drawClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = true)
            }
            WeatherCondition.SLEET -> {
                drawClouds(canvas, w, h, density = 2)
                drawRain(canvas, w, h, isHeavy = false)
                drawHail(canvas, w, h)
            }
            WeatherCondition.SNOW -> {
                drawClouds(canvas, w, h, density = 1)
                drawSnow(canvas, w, h)
            }
            WeatherCondition.THUNDER -> {
                drawClouds(canvas, w, h, density = 2)
                drawThunder(canvas, w, h, withHail = false)
            }
            WeatherCondition.THUNDER_HAIL -> {
                drawClouds(canvas, w, h, density = 2)
                drawThunder(canvas, w, h, withHail = true)
            }
        }

        if (!reduceMotion && shouldAnimate()) {
            postInvalidateOnAnimation()
        }
    }

    private fun shouldAnimate(): Boolean {
        return when (condition) {
            WeatherCondition.CLEAR -> !isDay
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
                    if (isDay) intArrayOf(Color.rgb(14, 78, 150), Color.rgb(28, 112, 188), Color.rgb(52, 146, 218))
                    else intArrayOf(Color.rgb(5, 11, 28), Color.rgb(12, 22, 48), Color.rgb(20, 36, 70))
                }
                WeatherCondition.PARTLY_CLOUDY -> {
                    if (isDay) intArrayOf(Color.rgb(18, 64, 116), Color.rgb(32, 88, 142), Color.rgb(48, 112, 166))
                    else intArrayOf(Color.rgb(8, 15, 32), Color.rgb(15, 27, 48), Color.rgb(24, 40, 66))
                }
                WeatherCondition.CLOUDY -> {
                    if (isDay) intArrayOf(Color.rgb(22, 44, 66), Color.rgb(34, 62, 88), Color.rgb(46, 78, 106))
                    else intArrayOf(Color.rgb(9, 16, 26), Color.rgb(16, 28, 42), Color.rgb(24, 38, 54))
                }
                WeatherCondition.FOG -> {
                    intArrayOf(Color.rgb(28, 44, 56), Color.rgb(44, 64, 78), Color.rgb(60, 82, 98))
                }
                WeatherCondition.DRIZZLE -> {
                    intArrayOf(Color.rgb(16, 34, 50), Color.rgb(24, 48, 68), Color.rgb(34, 62, 84))
                }
                WeatherCondition.RAIN -> {
                    intArrayOf(Color.rgb(12, 26, 42), Color.rgb(19, 38, 58), Color.rgb(28, 50, 72))
                }
                WeatherCondition.HEAVY_RAIN -> {
                    intArrayOf(Color.rgb(8, 18, 30), Color.rgb(14, 26, 42), Color.rgb(20, 36, 54))
                }
                WeatherCondition.SLEET -> {
                    intArrayOf(Color.rgb(20, 32, 46), Color.rgb(30, 46, 64), Color.rgb(42, 60, 78))
                }
                WeatherCondition.SNOW -> {
                    intArrayOf(Color.rgb(24, 40, 60), Color.rgb(40, 62, 88), Color.rgb(62, 88, 116))
                }
                WeatherCondition.THUNDER -> {
                    intArrayOf(Color.rgb(8, 13, 24), Color.rgb(14, 22, 36), Color.rgb(22, 32, 48))
                }
                WeatherCondition.THUNDER_HAIL -> {
                    intArrayOf(Color.rgb(6, 10, 18), Color.rgb(12, 18, 30), Color.rgb(18, 26, 40))
                }
            }

            bgGradient = LinearGradient(
                0f, 0f, 0f, h,
                colors,
                floatArrayOf(0f, 0.52f, 1f),
                Shader.TileMode.CLAMP
            )
            gradientPaint.shader = bgGradient
        }

        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, fullHalo: Boolean) {
        val cx = w * 0.82f
        val cy = h * 0.15f
        val outerRadius = if (fullHalo) w * 0.44f else w * 0.32f
        val coreRadius = if (fullHalo) w * 0.14f else w * 0.10f

        if (sunGradient == null) {
            val alphaCore = if (fullHalo) 145 else 105
            sunGradient = RadialGradient(
                cx, cy, outerRadius,
                intArrayOf(
                    Color.argb(alphaCore, 255, 241, 118),
                    Color.argb(65, 255, 213, 79),
                    Color.argb(20, 255, 183, 77),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            sunPaint.shader = sunGradient
        }

        if (sunCoreGradient == null) {
            val coreAlpha = if (fullHalo) 210 else 160
            sunCoreGradient = RadialGradient(
                cx, cy, coreRadius,
                intArrayOf(
                    Color.argb(coreAlpha, 255, 253, 231),
                    Color.argb((coreAlpha * 0.5f).toInt(), 255, 238, 88),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            sunCorePaint.shader = sunCoreGradient
        }

        canvas.drawCircle(cx, cy, outerRadius, sunPaint)
        canvas.drawCircle(cx, cy, coreRadius, sunCorePaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float, withMoon: Boolean) {
        if (withMoon) {
            val mcx = w * 0.82f
            val mcy = h * 0.15f
            val mRadius = w * 0.24f
            if (moonGradient == null) {
                moonGradient = RadialGradient(
                    mcx, mcy, mRadius,
                    intArrayOf(
                        Color.argb(95, 224, 242, 254),
                        Color.argb(38, 186, 230, 253),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.45f, 1f),
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
                (star.alpha + sin(now * 0.0022 * star.speed).toFloat() * 38f).toInt().coerceIn(35, 255)
            }
            particlePaint.color = Color.argb(alpha, 245, 250, 255)
            canvas.drawCircle(sx, sy, star.size, particlePaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, density: Int) {
        cloudPaint.style = Paint.Style.FILL
        val drift = if (reduceMotion) 0f else sin(System.currentTimeMillis() * 0.00035).toFloat() * (w * 0.03f)
        val baseAlpha = if (density > 1) 26 else 18

        // Upper soft cloud layer
        cloudPaint.color = Color.argb(baseAlpha, 255, 255, 255)
        canvas.drawOval(w * 0.02f + drift, h * 0.06f, w * 0.84f + drift, h * 0.25f, cloudPaint)
        canvas.drawOval(w * 0.32f - drift * 0.7f, h * 0.10f, w * 1.08f - drift * 0.7f, h * 0.31f, cloudPaint)
        canvas.drawOval(-w * 0.16f + drift * 0.5f, h * 0.19f, w * 0.64f + drift * 0.5f, h * 0.38f, cloudPaint)

        if (density > 1) {
            cloudPaint.color = Color.argb(18, 226, 238, 248)
            canvas.drawOval(w * 0.12f - drift, h * 0.25f, w * 0.96f - drift, h * 0.45f, cloudPaint)
            canvas.drawOval(-w * 0.10f + drift * 0.8f, h * 0.32f, w * 0.72f + drift * 0.8f, h * 0.50f, cloudPaint)
        }
    }

    private fun drawDrizzle(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.STROKE
        particlePaint.strokeWidth = 1.6f
        particlePaint.strokeCap = Paint.Cap.ROUND
        val count = minOf(48, rainDrops.size)
        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h
            particlePaint.color = Color.argb((drop.alpha * 0.75f).toInt(), 186, 230, 253)
            val len = drop.length * 0.42f
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

        val slant = if (isHeavy) 8.5f else 6f
        val count = if (isHeavy) rainDrops.size else (rainDrops.size * 3 / 4)

        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h

            particlePaint.strokeWidth = (if (isHeavy) 2.8f else 2.1f) * drop.depth
            particlePaint.color = Color.argb(drop.alpha, 186, 230, 253)
            val dropLen = if (isHeavy) drop.length * 1.28f else drop.length
            canvas.drawLine(sx, sy, sx - slant * drop.depth, sy + dropLen, particlePaint)

            if (!reduceMotion) {
                val speedMultiplier = if (isHeavy) 1.32f else 1.0f
                drop.y += drop.speed * speedMultiplier
                if (drop.y > 1f) {
                    drop.y = -0.05f
                    drop.x = (drop.x + 0.13f) % 1f
                }
            }
        }

        // Subtle ground splash ripples for heavy rain
        if (isHeavy) {
            splashPaint.style = Paint.Style.STROKE
            splashPaint.strokeWidth = 1.4f
            splashPaint.color = Color.argb(34, 186, 230, 253)
            canvas.drawOval(w * 0.08f, h - 32f, w * 0.36f, h - 10f, splashPaint)
            canvas.drawOval(w * 0.52f, h - 46f, w * 0.86f, h - 20f, splashPaint)
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
                lightningAlpha = 145
                nextLightningFrame = 150 + Random.nextInt(170)
            }
        }

        if (lightningAlpha > 0) {
            lightningPaint.color = Color.argb(lightningAlpha, 224, 238, 255)
            canvas.drawRect(0f, 0f, w, h, lightningPaint)
            if (!reduceMotion) {
                lightningAlpha = (lightningAlpha - 18).coerceAtLeast(0)
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

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        cloudPaint.style = Paint.Style.FILL
        val shift = if (reduceMotion) 0f else sin(System.currentTimeMillis() * 0.0004).toFloat() * (w * 0.04f)

        cloudPaint.color = Color.argb(30, 220, 232, 242)
        canvas.drawOval(-w * 0.2f + shift, h * 0.14f, w * 1.2f + shift, h * 0.34f, cloudPaint)

        cloudPaint.color = Color.argb(24, 204, 220, 234)
        canvas.drawOval(-w * 0.15f - shift, h * 0.28f, w * 1.25f - shift, h * 0.52f, cloudPaint)

        cloudPaint.color = Color.argb(36, 190, 208, 224)
        canvas.drawOval(-w * 0.25f + shift * 0.6f, h * 0.46f, w * 1.15f + shift * 0.6f, h * 0.72f, cloudPaint)
    }
}
