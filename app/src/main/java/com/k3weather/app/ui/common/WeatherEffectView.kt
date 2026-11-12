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
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightningPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Cached Shaders
    private var bgGradient: LinearGradient? = null
    private var sunGradient: RadialGradient? = null

    // Particles
    private class Particle(
        var x: Float = 0f,
        var y: Float = 0f,
        var speed: Float = 0f,
        var size: Float = 0f,
        var alpha: Int = 255,
        var length: Float = 0f
    )

    private val rainDrops = Array(80) { Particle() }
    private val stars = Array(55) { Particle() }
    private val snowFlakes = Array(45) { Particle() }
    private val hailStones = Array(30) { Particle() }

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
            p.speed = 0.018f + rnd.nextFloat() * 0.026f
            p.length = 26f + rnd.nextFloat() * 38f
            p.alpha = 80 + rnd.nextInt(125)
        }
        for (p in stars) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat() * 0.72f // Upper 72% of screen
            p.size = 1.4f + rnd.nextFloat() * 2.6f
            p.alpha = 90 + rnd.nextInt(155)
            p.speed = 0.6f + rnd.nextFloat() * 1.6f
        }
        for (p in snowFlakes) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.003f + rnd.nextFloat() * 0.006f
            p.size = 2.4f + rnd.nextFloat() * 4.2f
            p.alpha = 130 + rnd.nextInt(115)
        }
        for (p in hailStones) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.025f + rnd.nextFloat() * 0.035f
            p.size = 3.5f + rnd.nextFloat() * 3.5f
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
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgGradient = null
        sunGradient = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawBackgroundGradient(canvas, w, h)

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = true) else drawStars(canvas, w, h)
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSun(canvas, w, h, fullHalo = false) else drawStars(canvas, w, h)
                drawClouds(canvas, w, h, density = 1)
            }
            WeatherCondition.CLOUDY -> {
                drawClouds(canvas, w, h, density = 2)
            }
            WeatherCondition.FOG -> {
                drawFog(canvas, w, h)
            }
            WeatherCondition.DRIZZLE -> {
                drawDrizzle(canvas, w, h)
            }
            WeatherCondition.RAIN -> {
                drawRain(canvas, w, h, isHeavy = false)
            }
            WeatherCondition.HEAVY_RAIN -> {
                drawRain(canvas, w, h, isHeavy = true)
            }
            WeatherCondition.SLEET -> {
                drawRain(canvas, w, h, isHeavy = false)
                drawHail(canvas, w, h)
            }
            WeatherCondition.SNOW -> {
                drawSnow(canvas, w, h)
            }
            WeatherCondition.THUNDER -> {
                drawThunder(canvas, w, h, withHail = false)
            }
            WeatherCondition.THUNDER_HAIL -> {
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
            WeatherCondition.PARTLY_CLOUDY -> !isDay
            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.HEAVY_RAIN,
            WeatherCondition.SLEET,
            WeatherCondition.SNOW,
            WeatherCondition.THUNDER,
            WeatherCondition.THUNDER_HAIL -> true
            else -> false
        }
    }

    private fun drawBackgroundGradient(canvas: Canvas, w: Float, h: Float) {
        if (bgGradient == null) {
            val (topColor, bottomColor) = when (condition) {
                WeatherCondition.CLEAR -> {
                    if (isDay) Pair(Color.rgb(18, 92, 168), Color.rgb(44, 134, 214))
                    else Pair(Color.rgb(7, 14, 34), Color.rgb(18, 30, 64))
                }
                WeatherCondition.PARTLY_CLOUDY -> {
                    if (isDay) Pair(Color.rgb(22, 68, 115), Color.rgb(42, 98, 150))
                    else Pair(Color.rgb(10, 18, 36), Color.rgb(20, 34, 58))
                }
                WeatherCondition.CLOUDY -> {
                    if (isDay) Pair(Color.rgb(24, 46, 68), Color.rgb(42, 74, 102))
                    else Pair(Color.rgb(10, 18, 28), Color.rgb(22, 34, 48))
                }
                WeatherCondition.FOG -> {
                    Pair(Color.rgb(32, 48, 60), Color.rgb(56, 76, 92))
                }
                WeatherCondition.DRIZZLE -> {
                    Pair(Color.rgb(18, 36, 52), Color.rgb(30, 56, 78))
                }
                WeatherCondition.RAIN -> {
                    Pair(Color.rgb(14, 28, 44), Color.rgb(24, 44, 64))
                }
                WeatherCondition.HEAVY_RAIN -> {
                    Pair(Color.rgb(10, 20, 32), Color.rgb(18, 32, 48))
                }
                WeatherCondition.SLEET -> {
                    Pair(Color.rgb(22, 34, 48), Color.rgb(38, 54, 72))
                }
                WeatherCondition.SNOW -> {
                    Pair(Color.rgb(28, 44, 62), Color.rgb(60, 84, 110))
                }
                WeatherCondition.THUNDER -> {
                    Pair(Color.rgb(10, 16, 26), Color.rgb(18, 28, 42))
                }
                WeatherCondition.THUNDER_HAIL -> {
                    Pair(Color.rgb(8, 12, 20), Color.rgb(14, 22, 34))
                }
            }

            bgGradient = LinearGradient(0f, 0f, 0f, h, topColor, bottomColor, Shader.TileMode.CLAMP)
            gradientPaint.shader = bgGradient
        }

        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, fullHalo: Boolean) {
        val cx = w * 0.82f
        val cy = h * 0.16f
        val radius = if (fullHalo) w * 0.36f else w * 0.26f

        if (sunGradient == null) {
            val alphaCore = if (fullHalo) 160 else 120
            sunGradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.argb(alphaCore, 255, 238, 88), Color.argb(55, 255, 213, 79), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            sunPaint.shader = sunGradient
        }

        canvas.drawCircle(cx, cy, radius, sunPaint)
    }

    private fun drawStars(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.FILL
        for (star in stars) {
            val sx = star.x * w
            val sy = star.y * h
            val alpha = if (reduceMotion) {
                star.alpha
            } else {
                (star.alpha + Math.sin(System.currentTimeMillis() * 0.002 * star.speed).toFloat() * 32f).toInt().coerceIn(40, 255)
            }
            particlePaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(sx, sy, star.size, particlePaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, density: Int) {
        cloudPaint.style = Paint.Style.FILL
        val baseAlpha = if (density > 1) 28 else 20
        cloudPaint.color = Color.argb(baseAlpha, 255, 255, 255)

        canvas.drawOval(w * 0.05f, h * 0.08f, w * 0.85f, h * 0.28f, cloudPaint)
        canvas.drawOval(w * 0.35f, h * 0.12f, w * 1.05f, h * 0.34f, cloudPaint)
        canvas.drawOval(-w * 0.15f, h * 0.22f, w * 0.65f, h * 0.42f, cloudPaint)

        if (density > 1) {
            cloudPaint.color = Color.argb(16, 255, 255, 255)
            canvas.drawOval(w * 0.15f, h * 0.28f, w * 0.95f, h * 0.48f, cloudPaint)
        }
    }

    private fun drawDrizzle(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.FILL
        val count = minOf(45, rainDrops.size)
        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h
            particlePaint.color = Color.argb((drop.alpha * 0.8f).toInt(), 178, 223, 248)
            canvas.drawCircle(sx, sy, 1.8f, particlePaint)

            if (!reduceMotion) {
                drop.y += drop.speed * 0.6f
                if (drop.y > 1f) {
                    drop.y = -0.04f
                    drop.x = (drop.x + 0.11f) % 1f
                }
            }
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, isHeavy: Boolean) {
        particlePaint.strokeWidth = if (isHeavy) 3.0f else 2.2f
        particlePaint.strokeCap = Paint.Cap.ROUND

        val slant = if (isHeavy) 8f else 6f
        val count = if (isHeavy) rainDrops.size else (rainDrops.size * 3 / 4)

        for (i in 0 until count) {
            val drop = rainDrops[i]
            val sx = drop.x * w
            val sy = drop.y * h

            particlePaint.color = Color.argb(drop.alpha, 178, 223, 248)
            val dropLen = if (isHeavy) drop.length * 1.25f else drop.length
            canvas.drawLine(sx, sy, sx - slant, sy + dropLen, particlePaint)

            if (!reduceMotion) {
                val speedMultiplier = if (isHeavy) 1.3f else 1.0f
                drop.y += drop.speed * speedMultiplier
                if (drop.y > 1f) {
                    drop.y = -0.05f
                    drop.x = (drop.x + 0.13f) % 1f
                }
            }
        }

        // Draw subtle bottom ground splash ripples for heavy rain
        if (isHeavy) {
            splashPaint.style = Paint.Style.STROKE
            splashPaint.strokeWidth = 1.5f
            splashPaint.color = Color.argb(30, 178, 223, 248)
            canvas.drawOval(w * 0.1f, h - 30f, w * 0.35f, h - 10f, splashPaint)
            canvas.drawOval(w * 0.55f, h - 45f, w * 0.85f, h - 20f, splashPaint)
        }
    }

    private fun drawHail(canvas: Canvas, w: Float, h: Float) {
        particlePaint.style = Paint.Style.FILL
        for (hail in hailStones) {
            val sx = hail.x * w
            val sy = hail.y * h

            particlePaint.color = Color.argb(hail.alpha, 225, 245, 255)
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
                lightningAlpha = 150
                nextLightningFrame = 150 + Random.nextInt(170)
            }
        }

        if (lightningAlpha > 0) {
            lightningPaint.color = Color.argb(lightningAlpha, 220, 235, 255)
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

            particlePaint.color = Color.argb(flake.alpha, 240, 248, 255)
            canvas.drawCircle(sx, sy, flake.size, particlePaint)

            if (!reduceMotion) {
                flake.y += flake.speed
                flake.x += Math.sin(flake.y * 12.0).toFloat() * 0.0006f
                if (flake.y > 1f) {
                    flake.y = -0.03f
                    flake.x = (flake.x + 0.17f) % 1f
                }
            }
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        cloudPaint.style = Paint.Style.FILL
        cloudPaint.color = Color.argb(35, 220, 230, 240)

        canvas.drawRect(0f, h * 0.15f, w, h * 0.35f, cloudPaint)
        cloudPaint.color = Color.argb(25, 200, 215, 230)
        canvas.drawRect(0f, h * 0.30f, w, h * 0.55f, cloudPaint)
        cloudPaint.color = Color.argb(45, 180, 200, 220)
        canvas.drawRect(0f, h * 0.50f, w, h * 0.75f, cloudPaint)
    }
}
