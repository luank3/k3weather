package com.k3weather.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.mapper.WeatherCondition
import kotlin.random.Random

/**
 * Renders subtle, battery-free static atmospheric weather effects (sun glow, stars/moon,
 * volumetric clouds, mist bands, rain streaks, snow, lightning) onto a cached Bitmap for
 * home-screen RemoteViews widgets.
 */
object WidgetWeatherEffectRenderer {

    private val cache = LruCache<String, Bitmap>(18)

    fun render(context: Context, size: WidgetSize, weatherCode: Int, isDay: Boolean): Bitmap {
        val condition = WeatherCodeMapper.map(weatherCode).condition
        val key = "${size.name}_${condition.name}_$isDay"
        cache.get(key)?.let { if (!it.isRecycled) return it }

        val (w, h) = when (size) {
            WidgetSize.SMALL -> 260 to 260
            WidgetSize.MEDIUM -> 480 to 220
            WidgetSize.LARGE -> 480 to 460
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clip to the 24dp widget corner radius proportionally
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val cornerScale = when (size) {
            WidgetSize.SMALL -> w / (150f * density)
            WidgetSize.MEDIUM, WidgetSize.LARGE -> w / (310f * density)
        }
        val radius = (24f * density * cornerScale).coerceIn(18f, 48f)
        val clipPath = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val wf = w.toFloat()
        val hf = h.toFloat()

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) drawSunGlow(canvas, wf, hf, full = true)
                else drawStarsAndMoon(canvas, wf, hf, withMoon = true)
            }
            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) drawSunGlow(canvas, wf, hf, full = false)
                else drawStarsAndMoon(canvas, wf, hf, withMoon = true)
                drawClouds(canvas, wf, hf, count = 3, alphaScale = 0.85f)
            }
            WeatherCondition.CLOUDY -> {
                if (!isDay) drawStarsAndMoon(canvas, wf, hf, withMoon = false)
                drawClouds(canvas, wf, hf, count = 5, alphaScale = 1.0f)
            }
            WeatherCondition.FOG -> {
                drawClouds(canvas, wf, hf, count = 3, alphaScale = 0.7f)
                drawFog(canvas, wf, hf)
            }
            WeatherCondition.DRIZZLE -> {
                drawClouds(canvas, wf, hf, count = 3, alphaScale = 0.8f)
                drawRain(canvas, wf, hf, count = 26, heavy = false, drizzle = true)
            }
            WeatherCondition.RAIN -> {
                drawClouds(canvas, wf, hf, count = 4, alphaScale = 0.9f)
                drawRain(canvas, wf, hf, count = 38, heavy = false, drizzle = false)
            }
            WeatherCondition.HEAVY_RAIN -> {
                drawClouds(canvas, wf, hf, count = 5, alphaScale = 0.95f)
                drawRain(canvas, wf, hf, count = 54, heavy = true, drizzle = false)
            }
            WeatherCondition.SLEET -> {
                drawClouds(canvas, wf, hf, count = 4, alphaScale = 0.9f)
                drawRain(canvas, wf, hf, count = 32, heavy = false, drizzle = false)
                drawSnow(canvas, wf, hf, count = 16)
            }
            WeatherCondition.SNOW -> {
                drawClouds(canvas, wf, hf, count = 3, alphaScale = 0.85f)
                drawSnow(canvas, wf, hf, count = 34)
            }
            WeatherCondition.THUNDER -> {
                drawClouds(canvas, wf, hf, count = 5, alphaScale = 0.95f)
                drawRain(canvas, wf, hf, count = 48, heavy = true, drizzle = false)
                drawLightning(canvas, wf, hf)
            }
            WeatherCondition.THUNDER_HAIL -> {
                drawClouds(canvas, wf, hf, count = 5, alphaScale = 0.95f)
                drawRain(canvas, wf, hf, count = 48, heavy = true, drizzle = false)
                drawSnow(canvas, wf, hf, count = 14)
                drawLightning(canvas, wf, hf)
            }
        }

        cache.put(key, bitmap)
        return bitmap
    }

    private fun drawSunGlow(canvas: Canvas, w: Float, h: Float, full: Boolean) {
        val cx = w * 0.84f
        val cy = h * 0.16f
        val r = w * if (full) 0.48f else 0.34f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    Color.argb(if (full) 95 else 65, 255, 240, 150),
                    Color.argb(30, 255, 210, 100),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, r, paint)
    }

    private fun drawStarsAndMoon(canvas: Canvas, w: Float, h: Float, withMoon: Boolean) {
        if (withMoon) {
            val cx = w * 0.84f
            val cy = h * 0.18f
            val r = w * 0.28f
            val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, r,
                    intArrayOf(
                        Color.argb(85, 224, 242, 254),
                        Color.argb(28, 147, 197, 253),
                        Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, r, moonPaint)
        }
        val rnd = Random(17)
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until 26) {
            val sx = rnd.nextFloat() * w
            val sy = rnd.nextFloat() * h * 0.72f
            val sr = 1.0f + rnd.nextFloat() * 1.8f
            starPaint.color = Color.argb(70 + rnd.nextInt(120), 240, 249, 255)
            canvas.drawCircle(sx, sy, sr, starPaint)
        }
    }

    private fun drawClouds(canvas: Canvas, w: Float, h: Float, count: Int, alphaScale: Float) {
        val centers = arrayOf(
            floatArrayOf(0.20f, 0.14f, 0.40f, 0.22f),
            floatArrayOf(0.76f, 0.18f, 0.44f, 0.24f),
            floatArrayOf(0.46f, 0.28f, 0.48f, 0.25f),
            floatArrayOf(0.14f, 0.36f, 0.36f, 0.20f),
            floatArrayOf(0.84f, 0.40f, 0.40f, 0.22f)
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in 0 until minOf(count, centers.size)) {
            val c = centers[i]
            val cx = c[0] * w
            val cy = c[1] * h
            val r = maxOf(c[2] * w, c[3] * h)
            paint.shader = RadialGradient(
                cx, cy, r,
                intArrayOf(
                    Color.argb((52 * alphaScale).toInt(), 240, 248, 255),
                    Color.argb((22 * alphaScale).toInt(), 210, 232, 250),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, r, paint)
        }
    }

    private fun drawFog(canvas: Canvas, w: Float, h: Float) {
        val bands = floatArrayOf(0.24f, 0.48f, 0.72f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (b in bands) {
            val cy = b * h
            val halfH = h * 0.16f
            paint.shader = LinearGradient(
                0f, cy - halfH, 0f, cy + halfH,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(46, 224, 242, 254),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, cy - halfH, w, cy + halfH, paint)
        }
    }

    private fun drawRain(canvas: Canvas, w: Float, h: Float, count: Int, heavy: Boolean, drizzle: Boolean) {
        val rnd = Random(if (heavy) 99 else 42)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val slant = if (heavy) 6f else if (drizzle) 2.5f else 4f
        for (i in 0 until count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val len = (if (drizzle) 10f else if (heavy) 24f else 16f) + rnd.nextFloat() * 14f
            paint.strokeWidth = if (drizzle) 1.3f else if (heavy) 2.1f else 1.6f
            paint.color = Color.argb(55 + rnd.nextInt(70), 196, 235, 255)
            canvas.drawLine(x, y, x - slant, y + len, paint)
        }
    }

    private fun drawSnow(canvas: Canvas, w: Float, h: Float, count: Int) {
        val rnd = Random(77)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (i in 0 until count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val r = 1.6f + rnd.nextFloat() * 2.4f
            paint.color = Color.argb(85 + rnd.nextInt(95), 240, 249, 255)
            canvas.drawCircle(x, y, r, paint)
        }
    }

    private fun drawLightning(canvas: Canvas, w: Float, h: Float) {
        val path = Path().apply {
            val bx = w * 0.68f
            moveTo(bx, 0f)
            lineTo(bx - w * 0.04f, h * 0.14f)
            lineTo(bx + w * 0.02f, h * 0.22f)
            lineTo(bx - w * 0.05f, h * 0.40f)
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 6f
            color = Color.argb(55, 125, 211, 252)
        }
        val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = 2f
            color = Color.argb(145, 240, 249, 255)
        }
        canvas.drawPath(path, glow)
        canvas.drawPath(path, core)
    }
}
