package com.k3weather.app.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.databinding.ItemDailyForecastBinding
import com.k3weather.app.databinding.ItemHourlyForecastBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ForecastView(private val binding: LayoutForecastBinding) {

    fun bind(
        state: WeatherUiState,
        onSearch: () -> Unit,
        onRefresh: () -> Unit,
        onReturnToCurrentLocation: () -> Unit
    ) {
        val context = binding.root.context
        val colorScheme = state.settings.colorScheme
        val accentColor = colorScheme.primaryAccent
        val accentTintList = ColorStateList.valueOf(accentColor)

        binding.searchButton.setOnClickListener { onSearch() }
        binding.staleBadge.setOnClickListener { onRefresh() }
        binding.btnReturnCurrentLocation.setOnClickListener { onReturnToCurrentLocation() }

        binding.swipeRefresh.setColorSchemeColors(accentColor)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(0xE60A1E2E.toInt())
        binding.swipeRefresh.setOnRefreshListener { onRefresh() }
        binding.swipeRefresh.isRefreshing = state.loading
        binding.forecastProgress.visibility = View.GONE
        binding.staleBadge.visibility = if (state.forecast?.isStale == true) View.VISIBLE else View.GONE

        // Accent styling
        binding.ivLocationPin.imageTintList = accentTintList
        binding.tvCurrentLocationTag.setTextColor(accentColor)
        binding.ivReturnIcon.imageTintList = accentTintList
        binding.tvReturnLabel.setTextColor(accentColor)
        binding.tvWindDirection.setTextColor(accentColor)

        val loc = state.forecast?.location ?: state.selectedLocation
        if (loc != null) {
            binding.cityName.text = loc.name
            binding.citySubtitle.text = loc.subtitle.ifBlank { loc.country }
        }

        val isCurrentLocation = loc?.isCurrentLocation == true ||
                (state.detectedCurrentLocation != null && loc?.id == state.detectedCurrentLocation.id)
        val showReturnAction = !isCurrentLocation && state.detectedCurrentLocation != null

        binding.badgeCurrentLocation.visibility = if (isCurrentLocation) View.VISIBLE else View.GONE
        binding.btnReturnCurrentLocation.visibility = if (showReturnAction) View.VISIBLE else View.GONE

        val forecast = state.forecast ?: return
        val current = forecast.current
        val tempUnit = state.settings.tempUnit
        val windUnit = state.settings.windSpeedUnit
        val locationZone = runCatching { ZoneId.of(forecast.location.timezone) }.getOrDefault(ZoneId.systemDefault())

        // City header & live local time in location's timezone
        binding.cityName.text = forecast.location.name
        val baseSubtitle = forecast.location.subtitle.ifBlank { forecast.location.country }
        val localTimeStr = runCatching {
            ZonedDateTime.now(locationZone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        }.getOrNull()
        binding.citySubtitle.text = if (!localTimeStr.isNullOrBlank() && baseSubtitle.isNotBlank()) {
            "$baseSubtitle  •  $localTimeStr"
        } else {
            baseSubtitle
        }

        // Hero section
        binding.temperature.text = tempUnit.format(current.temperatureC)
        binding.condition.text = WeatherCodeMapper.getLocalizedLabel(context, current.weatherCode)
        binding.highLow.text = context.getString(
            R.string.high_low_feels_format,
            tempUnit.format(current.maxTempC),
            tempUnit.format(current.minTempC),
            tempUnit.format(current.apparentTemperatureC)
        )

        // 24-Hour Forecast
        binding.hourlyList.adapter = HourlyAdapter(
            items = forecast.hourly,
            unit = tempUnit,
            nowLabel = context.getString(R.string.time_now),
            accentColor = accentColor,
            zoneId = locationZone
        )

        // 7-Day Weekly Forecast
        val daysToRender = forecast.daily.take(7)
        val minGlobal = daysToRender.minOfOrNull { it.minTempC } ?: 0.0
        val maxGlobal = daysToRender.maxOfOrNull { it.maxTempC } ?: (minGlobal + 1.0)
        val tempRange = (maxGlobal - minGlobal).coerceAtLeast(1.0)

        binding.dailyRows.removeAllViews()
        val currentLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()

        daysToRender.forEachIndexed { index, day ->
            val row = ItemDailyForecastBinding.inflate(LayoutInflater.from(context), binding.dailyRows, false)

            val dayName = if (index == 0) {
                context.getString(R.string.day_today)
            } else {
                runCatching {
                    LocalDate.parse(day.dateIso).format(DateTimeFormatter.ofPattern("EEE", currentLocale))
                }.getOrDefault(day.dayLabel)
            }

            row.dayLabel.text = dayName
            row.dayLabel.setTextColor(if (index == 0) accentColor else 0xFFFFFFFF.toInt())
            row.dayIcon.setImageResource(WeatherCodeMapper.getIcon(day.weatherCode, isDay = true))

            if (day.precipitationProbabilityMax >= 20) {
                row.dayPop.visibility = View.VISIBLE
                row.dayPop.text = "${day.precipitationProbabilityMax}%"
                row.dayPop.setTextColor(accentColor)
            } else {
                row.dayPop.visibility = View.GONE
            }

            row.minTemperature.text = tempUnit.format(day.minTempC)
            row.maxTemperature.text = tempUnit.format(day.maxTempC)

            val barGradient = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(colorScheme.gradientStart, colorScheme.gradientEnd)
            ).apply {
                cornerRadius = 999f
            }
            row.temperatureRange.background = barGradient

            row.temperatureRangeContainer.post {
                val totalWidth = row.temperatureRangeContainer.width.toFloat()
                if (totalWidth > 0) {
                    val minRatio = ((day.minTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val maxRatio = ((day.maxTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val leftMargin = (totalWidth * minRatio).toInt()
                    val barWidth = (totalWidth * (maxRatio - minRatio)).toInt().coerceAtLeast(20)

                    val lp = row.temperatureRange.layoutParams as FrameLayout.LayoutParams
                    lp.leftMargin = leftMargin
                    lp.marginStart = leftMargin
                    lp.width = barWidth
                    row.temperatureRange.layoutParams = lp

                    if (index == 0) {
                        row.currentTempDot.visibility = View.VISIBLE
                        val curRatio = ((current.temperatureC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                        val dotSize = row.currentTempDot.width.takeIf { it > 0 } ?: (8 * context.resources.displayMetrics.density).toInt()
                        val dotLeft = ((totalWidth - dotSize) * curRatio).toInt().coerceAtLeast(0)
                        val dotLp = row.currentTempDot.layoutParams as FrameLayout.LayoutParams
                        dotLp.leftMargin = dotLeft
                        dotLp.marginStart = dotLeft
                        row.currentTempDot.layoutParams = dotLp
                    } else {
                        row.currentTempDot.visibility = View.GONE
                    }
                }
            }

            binding.dailyRows.addView(row.root)

            if (index < daysToRender.lastIndex) {
                val divider = View(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(0x14FFFFFF)
                }
                binding.dailyRows.addView(divider)
            }
        }

        // Bento Card 1: UV Index
        val uvInt = current.uvIndexInt
        binding.tvUvValue.text = uvInt.toString()
        val (uvCatRes, uvAdvRes) = when {
            uvInt <= 2 -> R.string.uv_low to R.string.uv_advice_low
            uvInt <= 5 -> R.string.uv_moderate to R.string.uv_advice_moderate
            uvInt <= 7 -> R.string.uv_high to R.string.uv_advice_high
            uvInt <= 10 -> R.string.uv_very_high to R.string.uv_advice_very_high
            else -> R.string.uv_extreme to R.string.uv_advice_very_high
        }
        binding.tvUvCategory.text = context.getString(uvCatRes)
        binding.tvUvAdvice.text = context.getString(uvAdvRes)

        val uvGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(colorScheme.gradientStart, colorScheme.gradientEnd)
        ).apply {
            cornerRadius = 999f
        }
        binding.uvProgressFill.background = uvGradient
        binding.uvBarContainer.post {
            val w = binding.uvBarContainer.width
            if (w > 0) {
                val ratio = (current.uvIndex / 11.0).coerceIn(0.08, 1.0).toFloat()
                val lp = binding.uvProgressFill.layoutParams
                lp.width = (w * ratio).toInt().coerceAtLeast(14)
                binding.uvProgressFill.layoutParams = lp
            }
        }

        // Bento Card 2: Feels Like
        binding.tvFeelsLikeValue.text = tempUnit.format(current.apparentTemperatureC)
        val diff = current.apparentTemperatureC - current.temperatureC
        binding.tvFeelsLikeNote.text = when {
            diff >= 1.8 -> context.getString(R.string.feels_warmer)
            diff <= -1.8 -> context.getString(R.string.feels_cooler)
            else -> context.getString(R.string.feels_similar)
        }

        // Bento Card 3: Wind
        binding.tvWindValue.text = windUnit.format(current.windSpeedKmh)
        val compass = degreesToCompass(current.windDirectionDeg)
        binding.tvWindDirection.text = context.getString(R.string.wind_dir_format, compass, current.windDirectionDeg)
        binding.tvWindGusts.text = context.getString(R.string.wind_gusts_format, windUnit.format(current.windGustsKmh))

        // Bento Card 4: Humidity & Dew Point
        binding.tvHumidityValue.text = "${current.humidityPercent}%"
        binding.tvDewPointNote.text = if (current.precipitationMm > 0.0) {
            context.getString(R.string.precip_today_format, current.precipitationMm)
        } else {
            context.getString(R.string.dew_point_format, tempUnit.format(current.dewPointC))
        }

        // Bento Card 5: Sunrise & Sunset
        val sunriseTime = formatIsoTime(current.sunriseIso, "05:55")
        val sunsetTime = formatIsoTime(current.sunsetIso, "17:30")
        if (current.isDay) {
            binding.tvSunPrimaryValue.text = sunsetTime
            binding.tvSunSecondaryNote.text = context.getString(R.string.sunrise_at_format, sunriseTime)
        } else {
            binding.tvSunPrimaryValue.text = sunriseTime
            binding.tvSunSecondaryNote.text = context.getString(R.string.sunset_at_format, sunsetTime)
        }

        // Bento Card 6: Visibility & Surface Pressure
        binding.tvVisibilityValue.text = if (windUnit == WindSpeedUnit.MPH) {
            "${(current.visibilityKm * 0.621371).roundToInt().coerceAtLeast(1)} mi"
        } else {
            "${current.visibilityKm.roundToInt().coerceAtLeast(1)} km"
        }
        val pressureStr = context.getString(R.string.pressure_format, current.surfacePressureHpa.roundToInt())
        val visQuality = if (current.visibilityKm >= 9.0) {
            context.getString(R.string.visibility_good)
        } else {
            context.getString(R.string.visibility_moderate)
        }
        binding.tvPressureNote.text = "$pressureStr  •  $visQuality"
    }

    private fun degreesToCompass(deg: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((deg % 360) + 360) % 360 + 22) / 45 % 8
        return dirs[idx]
    }

    private fun formatIsoTime(iso: String, fallback: String): String {
        if (iso.isBlank()) return fallback
        return runCatching {
            LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse {
            iso.substringAfter('T', fallback).take(5).ifBlank { fallback }
        }
    }
}

class ForecastBinder(private val view: ForecastView) {
    fun bind(
        state: WeatherUiState,
        onSearch: () -> Unit,
        onRefresh: () -> Unit,
        onReturnToCurrentLocation: () -> Unit
    ) = view.bind(state, onSearch, onRefresh, onReturnToCurrentLocation)
}

private class HourlyAdapter(
    private val items: List<HourlyForecast>,
    private val unit: TemperatureUnit,
    private val nowLabel: String,
    private val accentColor: Int,
    private val zoneId: ZoneId
) : RecyclerView.Adapter<HourlyAdapter.Holder>() {

    class Holder(val binding: ItemHourlyForecastBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemHourlyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = minOf(24, items.size)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isNow = position == 0
        holder.binding.hourTime.text = if (isNow) {
            nowLabel
        } else {
            runCatching {
                val instant = Instant.ofEpochSecond(item.timeEpochSeconds)
                instant.atZone(zoneId).format(DateTimeFormatter.ofPattern("h a", Locale.US))
            }.getOrDefault(item.timeLabel)
        }
        holder.binding.hourTime.setTextColor(if (isNow) accentColor else 0xFFD9F0FF.toInt())
        holder.binding.hourIcon.setImageResource(WeatherCodeMapper.getIcon(item.weatherCode, item.isDay))
        if (item.precipitationProbability >= 15) {
            holder.binding.hourPop.text = "${item.precipitationProbability}%"
            holder.binding.hourPop.setTextColor(accentColor)
        } else {
            holder.binding.hourPop.text = ""
        }
        holder.binding.hourTemperature.text = unit.format(item.temperatureC)
    }
}
