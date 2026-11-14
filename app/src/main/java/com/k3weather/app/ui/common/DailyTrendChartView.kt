package com.k3weather.app.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.k3weather.app.R
import com.k3weather.app.core.model.DailyForecast
import com.k3weather.app.core.model.TemperatureUnit
import com.k3weather.app.data.mapper.WeatherCodeMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders a multi-day dual-curve temperature trend chart with day headers, weather icons,
 * precipitation probability, and smooth high/low cubic curves.
 */
class DailyTrendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class ChartColumn(
        val dayName: String,
        val dateText: String,
        val icon: Drawable?,
        val popText: String?,
        val maxTempC: Double,
        val minTempC: Double,
        val maxLabel: String,
        val minLabel: String
    )

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private var columns: List<ChartColumn> = emptyList()

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 12.5f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textSize = 10.5f * scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7DD3FC.toInt()
        textSize = 10f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private val tempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 13f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val minTempPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        textSize = 12.5f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val highLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = 0xCCFDE047.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val lowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = 0xCC7DD3FC.toInt()
        strokeCap = Paint.Cap.ROUND
    }

    private val highDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFDE047.toInt()
    }

    private val lowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF7DD3FC.toInt()
    }

    private val dotHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33FFFFFF
    }

    private val highPath = Path()
    private val lowPath = Path()

    fun setForecastDays(days: List<DailyForecast>, unit: TemperatureUnit) {
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val dayFmt = DateTimeFormatter.ofPattern("EEE", locale)
        val dateFmt = DateTimeFormatter.ofPattern("d/M", locale)
        val todayLabel = context.getString(R.string.day_today)

        columns = days.take(7).mapIndexed { index, day ->
            val parsedDate = runCatching { LocalDate.parse(day.dateIso) }.getOrNull()
            val dayName = if (index == 0) todayLabel else parsedDate?.format(dayFmt) ?: day.dayLabel
            val dateText = parsedDate?.format(dateFmt).orEmpty()
            val iconRes = WeatherCodeMapper.getIcon(day.weatherCode, isDay = true)
            ChartColumn(
                dayName = dayName,
                dateText = dateText,
                icon = ContextCompat.getDrawable(context, iconRes)?.mutate(),
                popText = if (day.precipitationProbabilityMax >= 20) "${day.precipitationProbabilityMax}%" else null,
                maxTempC = day.maxTempC,
                minTempC = day.minTempC,
                maxLabel = unit.format(day.maxTempC),
                minLabel = unit.format(day.minTempC)
            )
        }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availWidth = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - (64 * density).toInt())
        val visibleCols = 5f
        val baseColWidth = maxOf(availWidth / visibleCols, 62f * density)
        val desiredWidth = if (columns.isEmpty()) availWidth else (columns.size * baseColWidth).toInt()
        val desiredHeight = (206f * density).toInt()
        setMeasuredDimension(desiredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (columns.isEmpty()) return

        val colW = width.toFloat() / columns.size
        val dayY = 18f * density
        val dateY = 34f * density
        val iconTop = 44f * density
        val iconSize = (24f * density).toInt()
        val popY = 82f * density

        val chartTop = 112f * density
        val chartBottom = 176f * density
        val chartSpan = (chartBottom - chartTop).coerceAtLeast(1f)

        val globalMax = columns.maxOf { it.maxTempC }
        val globalMin = columns.minOf { it.minTempC }
        val tempSpan = (globalMax - globalMin).coerceAtLeast(2.0)

        val highX = FloatArray(columns.size)
        val highY = FloatArray(columns.size)
        val lowX = FloatArray(columns.size)
        val lowY = FloatArray(columns.size)

        columns.forEachIndexed { i, col ->
            val cx = (i + 0.5f) * colW
            dayPaint.color = if (i == 0) 0xFFFFFFFF.toInt() else 0xE6FFFFFF.toInt()
            canvas.drawText(col.dayName, cx, dayY, dayPaint)
            if (col.dateText.isNotEmpty()) {
                canvas.drawText(col.dateText, cx, dateY, datePaint)
            }

            col.icon?.let { d ->
                val half = iconSize / 2
                d.setBounds((cx - half).toInt(), iconTop.toInt(), (cx + half).toInt(), (iconTop + iconSize).toInt())
                d.draw(canvas)
            }

            col.popText?.let { pop ->
                canvas.drawText(pop, cx, popY, popPaint)
            }

            val maxNorm = ((globalMax - col.maxTempC) / tempSpan).toFloat()
            val minNorm = ((globalMax - col.minTempC) / tempSpan).toFloat()
            highX[i] = cx
            highY[i] = chartTop + maxNorm * chartSpan
            lowX[i] = cx
            lowY[i] = chartTop + minNorm * chartSpan
        }

        buildSmoothCurve(highPath, highX, highY)
        buildSmoothCurve(lowPath, lowX, lowY)
        canvas.drawPath(highPath, highLinePaint)
        canvas.drawPath(lowPath, lowLinePaint)

        val dotRadius = 3.2f * density
        val haloRadius = 6f * density
        for (i in columns.indices) {
            if (i == 0) {
                canvas.drawCircle(highX[i], highY[i], haloRadius, dotHaloPaint)
                canvas.drawCircle(lowX[i], lowY[i], haloRadius, dotHaloPaint)
            }
            canvas.drawCircle(highX[i], highY[i], dotRadius, highDotPaint)
            canvas.drawCircle(lowX[i], lowY[i], dotRadius, lowDotPaint)

            canvas.drawText(columns[i].maxLabel, highX[i], highY[i] - 9f * density, tempPaint)
            canvas.drawText(columns[i].minLabel, lowX[i], lowY[i] + 17f * density, minTempPaint)
        }
    }

    private fun buildSmoothCurve(path: Path, xs: FloatArray, ys: FloatArray) {
        path.reset()
        if (xs.isEmpty()) return
        path.moveTo(xs[0], ys[0])
        for (i in 0 until xs.size - 1) {
            val midX = (xs[i] + xs[i + 1]) / 2f
            path.cubicTo(midX, ys[i], midX, ys[i + 1], xs[i + 1], ys[i + 1])
        }
    }
}
