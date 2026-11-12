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

    // Shaders cached per size
    private var lastW = 0
    private var lastH = 0
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

    private val rainDrops = Array(65) { Particle() }
    private val stars = Array(50) { Particle() }
    private val snowFlakes = Array(40) { Particle() }

    private var lightningAlpha = 0
    private var nextLightningFrame = 180

    init {
        initParticles()
    }

    private fun initParticles() {
        val rnd = Random(42)
        for (p in rainDrops) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.015f + rnd.nextFloat() * 0.025f
            p.length = 25f + rnd.nextFloat() * 35f
            p.alpha = 80 + rnd.nextInt(120)
        }
        for (p in stars) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat() * 0.75f // Top 75% of screen
            p.size = 1.5f + rnd.nextFloat() * 2.5f
            p.alpha = 90 + rnd.nextInt(150)
            p.speed = 0.5f + rnd.nextFloat() * 1.5f
        }
        for (p in snowFlakes) {
            p.x = rnd.nextFloat()
            p.y = rnd.nextFloat()
            p.speed = 0.002f + rnd.nextFloat() * 0.005f
            p.size = 2.5f + rnd.nextFloat() * 4f
            p.alpha = 120 + rnd.nextInt(120)
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
        lastW = w
        lastH = h
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
                if (isDay) {
                    drawSun(canvas, w, h)
                } else {
                    drawStars(canvas, w, h)
                }
            }
            WeatherCondition.CLOUDY -> {
                drawClouds(canvas, w, h)
            }
            WeatherCondition.RAIN -> {
                drawRain(canvas, w, h)
            }
            WeatherCondition.THUNDER -> {
                drawThunder(canvas, w, h)
            }
            WeatherCondition.SNOW -> {
                drawSnow(canvas, w, h)
            }
            WeatherCondition.FOG -> {
                drawFog(canvas, w, h)
            }
        }

        if (!reduceMotion && (condition == WeatherCondition.RAIN || condition == WeatherCondition.THUNDER || condition == WeatherCondition.SNOW || (!isDay && condition == WeatherCondition.CLEAR))) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawBackgroundGradient(canvas: Canvas, w: Float, h: Float) {
        if (bgGradient == null) {
            val (topColor, bottomColor) = when (condition) {
                WeatherCondition.CLEAR -> {
                    if (isDay) {
                        Pair(Color.rgb(18, 92, 168), Color.rgb(44, 134, 214))
                    } else {
                        Pair(Color.rgb(7, 14, 34), Color.rgb(18, 30, 64))
                    }
                }
                WeatherCondition.CLOUDY -> {
                    if (isDay) {
                        Pair(Color.rgb(24, 46, 68), Color.rgb(42, 74, 102))
                    } else {
                        Pair(Color.rgb(10, 18, 28), Color.rgb(22, 34, 48))
                    }
                }
                WeatherCondition.RAIN -> {
                    Pair(Color.rgb(14, 28, 44), Color.rgb(24, 44, 64))
                }
                WeatherCondition.THUNDER -> {
                    Pair(Color.rgb(10, 16, 26), Color.rgb(18, 28, 42))
                }
                WeatherCondition.SNOW -> {
                    Pair(Color.rgb(28, 44, 62), Color.rgb(60, 84, 110))
                }
                WeatherCondition.FOG -> {
                    Pair(Color.rgb(32, 48, 60), Color.rgb(56, 76, 92))
                }
            }

            bgGradient = LinearGradient(0f, 0f, 0f, h, topColor, bottomColor, Shader.TileMode.CLAMP)
            gradientPaint.shader = bgGradient
        }

        canvas.drawRect(0f, 0f, w, h, gradientPaint)
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float) {
        val cx = w * 0.82f
        val cy = h * 0.16f
        val radius = w * 0.35f

        if (sunGradient == null) {
            sunGradient = RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.argb(160, 255, 238, 88), Color.argb(60, 255, 213, 79), Color.TRANSPARENT),
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
                (star.alpha + Math.sin(System.currentTimeMillis() * 0.002 * star.speed).toFloat() * 30f).toInt().coerceIn(40, 255)
            }
            particlePaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawCircle(sx, sy, star.size, particlePaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float) {
        cloudPaint.style = Paint.Style.FILL
        cloudPaint.color = Color.argb(22, 255, 255, 255)

        // Soft atmospheric cloud banks
        canvas.drawOval(w * 0.05f, h * 0.08f, w * 0.85f, h * 0.28f, cloudPaint)
        canvas.drawOval(w * 0.35f, h * 0.12f, w * 1.05f, h * 0.34f, cloudPaint)
        canvas.drawOval(-w * 0.15f, h * 0.22f, w * 0.65f, h * 0.42f, cloudPaint)
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float) {
        particlePaint.strokeWidth = 2.2f
        particlePaint.strokeCap = Paint.Cap.ROUND

        val slant = 6f
        for (drop in rainDrops) {
            val sx = drop.x * w
            val sy = drop.y * h

            particlePaint.color = Color.argb(drop.alpha, 178, 223, 248)
            canvas.drawLine(sx, sy, sx - slant, sy + drop.length, particlePaint)

            if (!reduceMotion) {
                drop.y += drop.speed
                if (drop.y > 1f) {
                    drop.y = -0.05f
                    drop.x = (drop.x + 0.13f) % 1f
                }
            }
        }
    }

    private fun drawThunder(canvas: Canvas, w: Float, h: Float) {
        drawRain(canvas, w, h)

        if (!reduceMotion) {
            nextLightningFrame--
            if (nextLightningFrame <= 0) {
                lightningAlpha = 140
                nextLightningFrame = 160 + Random.nextInt(180)
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
