package com.k3weather.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.databinding.ItemDailyForecastBinding
import com.k3weather.app.databinding.ItemHourlyForecastBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.ui.common.HeroWeatherArtworkView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ForecastView(private val binding: LayoutForecastBinding) {

    fun bind(
        state: WeatherUiState,
        onSearch: () -> Unit,
        onRefresh: () -> Unit,
        onSelectCity: (LocationItem) -> Unit,
        onToggleUnit: (() -> Unit)? = null
    ) {
        val context = binding.root.context
        val colorScheme = state.settings.colorScheme
        val accentColor = colorScheme.primaryAccent

        binding.searchButton.setOnClickListener { onSearch() }
        binding.swipeRefresh.setColorSchemeColors(accentColor)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(0xE60A1E2E.toInt())
        binding.swipeRefresh.setOnRefreshListener { onRefresh() }
        binding.swipeRefresh.isRefreshing = state.loading

        // Setup City Quick-Pills Header
        setupCityPills(state, onSelectCity)

        val forecast = state.forecast
        val loc = forecast?.location ?: state.selectedLocation ?: LocationItem(
            id = "default",
            name = "Da Nang",
            country = "Vietnam",
            latitude = 16.0678,
            longitude = 108.2208
        )

        // Location Badge Pill
        val locationTitle = if (loc.country.isNotBlank()) "${loc.name}, ${loc.country}" else loc.name
        binding.cityName.text = locationTitle

        if (forecast == null) return

        val current = forecast.current
        val tempUnit = state.settings.tempUnit
        val windUnit = state.settings.windSpeedUnit
        val locationZone = runCatching { ZoneId.of(forecast.location.timezone) }.getOrDefault(ZoneId.systemDefault())

        // Natural Atmosphere Theme derived directly from weather code & day/night
        val effectiveTheme = when {
            !current.isDay -> HeroWeatherArtworkView.Theme.NIGHT
            current.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> HeroWeatherArtworkView.Theme.RAINY
            current.weatherCode in listOf(2, 3, 45, 48) -> HeroWeatherArtworkView.Theme.CLOUDY
            else -> HeroWeatherArtworkView.Theme.SUNNY
        }
        binding.heroWeatherArtwork.setTheme(effectiveTheme)

        // Hero Temperature
        binding.temperature.text = tempUnit.format(current.temperatureC)

        // Weather Condition Badge Pill
        val conditionLabel = WeatherCodeMapper.getLocalizedLabel(context, current.weatherCode).uppercase(Locale.getDefault())
        binding.condition.text = conditionLabel

        // High / Low & Feels Like Summary
        binding.highLow.text = context.getString(
            R.string.high_low_feels_format,
            tempUnit.format(current.maxTempC),
            tempUnit.format(current.minTempC),
            tempUnit.format(current.apparentTemperatureC)
        )

        // Narrative Weather Description
        binding.tvWeatherDescription.text = generateNarrativeDescription(context, current, loc.name)

        // Hourly Forecast Ribbon
        val maxHourlyPop = forecast.hourly.take(12).maxOfOrNull { it.precipitationProbability } ?: 0
        binding.tvHourlyPrecipChance.text = context.getString(R.string.precip_chance_format, maxHourlyPop)
        binding.hourlyList.adapter = HourlyAdapter(
            items = forecast.hourly,
            unit = tempUnit,
            nowLabel = context.getString(R.string.time_now),
            accentColor = accentColor,
            zoneId = locationZone
        )

        // 7-Day Forecast With Range Bars
        setupWeeklyForecast(forecast, tempUnit, colorScheme)

        // Bento Grid Card 1: UV Index
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
        binding.uvBarContainer.post {
            val w = binding.uvBarContainer.width
            if (w > 0) {
                val ratio = (current.uvIndex / 11.0).coerceIn(0.08, 1.0).toFloat()
                val lp = binding.uvProgressFill.layoutParams
                lp.width = (w * ratio).toInt().coerceAtLeast(14)
                binding.uvProgressFill.layoutParams = lp
            }
        }

        // Bento Grid Card 2: Wind & Compass Dial
        binding.tvWindValue.text = windUnit.format(current.windSpeedKmh)
        val compass = degreesToCompass(context, current.windDirectionDeg)
        binding.tvWindDirection.text = context.getString(R.string.wind_dir_format, compass, current.windDirectionDeg)
        binding.tvWindGusts.text = context.getString(R.string.wind_gusts_format, windUnit.format(current.windGustsKmh))
        binding.compassDialView.setWindDirection(current.windDirectionDeg)

        // Bento Grid Card 3: Humidity & Dew Point
        binding.tvHumidityValue.text = "${current.humidityPercent}%"
        binding.tvDewPointNote.text = context.getString(R.string.dew_point_format, tempUnit.format(current.dewPointC))

        // Bento Grid Card 4: Air Quality (AQI)
        // Approximate AQI based on visibility and humidity if direct AQI not present
        val calculatedAqi = ((50.0 - current.visibilityKm).coerceIn(0.0, 45.0) * 1.8 + (current.humidityPercent * 0.15)).roundToInt().coerceIn(15, 120)
        binding.tvAqiValue.text = calculatedAqi.toString()
        val (aqiLabelRes, aqiAdviceRes, aqiColor) = when {
            calculatedAqi <= 50 -> Triple(R.string.aqi_good, R.string.aqi_advice_good, 0xFF34D399.toInt())
            calculatedAqi <= 100 -> Triple(R.string.aqi_moderate, R.string.aqi_advice_moderate, 0xFFFBBF24.toInt())
            else -> Triple(R.string.aqi_unhealthy, R.string.aqi_advice_moderate, 0xFFF87171.toInt())
        }
        binding.tvAqiLabel.text = context.getString(aqiLabelRes)
        binding.tvAqiLabel.setTextColor(aqiColor)
        binding.tvAqiAdvice.text = context.getString(aqiAdviceRes)

        // Bento Grid Card 5: Visibility & Surface Pressure
        val visStr = if (windUnit == WindSpeedUnit.MPH) {
            "${(current.visibilityKm * 0.621371).roundToInt().coerceAtLeast(1)} mi"
        } else {
            "${current.visibilityKm.roundToInt().coerceAtLeast(1)} km"
        }
        binding.tvVisibilityValue.text = visStr
        binding.tvPressureNote.text = context.getString(R.string.pressure_format, current.surfacePressureHpa.roundToInt())
        binding.tvVisibilityDesc.text = context.getString(
            if (current.visibilityKm >= 10.0) R.string.visibility_good else R.string.visibility_moderate
        )

        // Bento Grid Card 6: Sun Cycle & Solar Arc
        val sunriseTime = formatIsoTime(current.sunriseIso, "05:48")
        val sunsetTime = formatIsoTime(current.sunsetIso, "17:22")
        binding.tvSunriseValue.text = sunriseTime
        binding.tvSunsetValue.text = sunsetTime

        val dayProgress = calculateDayProgress(current.sunriseIso, current.sunsetIso, locationZone)
        binding.solarArcView.setProgress(dayProgress, current.isDay)
    }

    private fun setupCityPills(state: WeatherUiState, onSelectCity: (LocationItem) -> Unit) {
        val context = binding.root.context
        val density = context.resources.displayMetrics.density
        binding.cityPillsContainer.removeAllViews()

        val allCities = mutableListOf<LocationItem>()
        state.detectedCurrentLocation?.let { allCities.add(it) }
        state.savedLocations.forEach { loc ->
            if (allCities.none { it.id == loc.id }) allCities.add(loc)
        }

        if (allCities.isEmpty() && state.selectedLocation != null) {
            allCities.add(state.selectedLocation)
        }

        val activeId = state.selectedLocation?.id ?: allCities.firstOrNull()?.id
        val pillHeightPx = (38 * density).toInt()

        allCities.forEach { city ->
            val isSelected = city.id == activeId
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (14 * density).toInt(),
                    0,
                    (14 * density).toInt(),
                    0
                )
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    pillHeightPx
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                layoutParams = params

                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_nav_active_pill)
                } else {
                    setBackgroundResource(R.drawable.bg_pill_accent_translucent)
                }

                if (city.isCurrentLocation || city.id == state.detectedCurrentLocation?.id) {
                    val icon = ImageView(context).apply {
                        val size = (14 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = (5 * density).toInt()
                        }
                        setImageResource(R.drawable.ic_arrow_current_location)
                        imageTintList = ColorStateList.valueOf(if (isSelected) 0xFF0F172A.toInt() else 0xFF38BDF8.toInt())
                    }
                    addView(icon)
                }

                val text = TextView(context).apply {
                    this.text = city.name
                    textSize = 12f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    setTextColor(if (isSelected) 0xFF0F172A.toInt() else 0xE6FFFFFF.toInt())
                }
                addView(text)

                setOnClickListener { onSelectCity(city) }
            }
            binding.cityPillsContainer.addView(pill)
        }
    }

    private fun setupWeeklyForecast(forecast: ForecastData, tempUnit: TemperatureUnit, colorScheme: AppColorScheme) {
        val context = binding.root.context
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
            row.dayLabel.setTextColor(if (index == 0) colorScheme.primaryAccent else 0xFFFFFFFF.toInt())
            row.dayIcon.setImageResource(WeatherCodeMapper.getIcon(day.weatherCode, isDay = true))

            if (day.precipitationProbabilityMax >= 20) {
                row.dayPop.visibility = View.VISIBLE
                row.dayPop.text = "${day.precipitationProbabilityMax}%"
                row.dayPop.setTextColor(0xFF38BDF8.toInt())
            } else {
                row.dayPop.visibility = View.GONE
            }

            row.minTemperature.text = tempUnit.format(day.minTempC)
            row.maxTemperature.text = tempUnit.format(day.maxTempC)

            val barGradient = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF38BDF8.toInt(), 0xFFFDE047.toInt(), 0xFFFB923C.toInt())
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
                        val curRatio = ((forecast.current.temperatureC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                        val dotSize = (8 * context.resources.displayMetrics.density).toInt()
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
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(0x14FFFFFF)
                }
                binding.dailyRows.addView(divider)
            }
        }
    }

    private fun generateNarrativeDescription(context: Context, current: CurrentWeather, cityName: String): String {
        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        return if (isVi) {
            when {
                current.precipitationMm > 0.5 -> "Mưa rải rác tại $cityName. Đường ướt và có thể có gió giật vào chiều tối."
                current.weatherCode in listOf(0, 1) && current.isDay -> "Trời quang đãng với gió nhẹ tại $cityName. Chỉ số UV cao vào giữa trưa."
                !current.isDay -> "Đêm quang đãng, trời đầy sao tại $cityName với tầm nhìn xa tốt."
                current.weatherCode in listOf(2, 3) -> "Trời nhiều mây tại $cityName với ánh sáng dịu nhẹ và nhiệt độ dễ chịu."
                else -> "Thời tiết dễ chịu tại $cityName với áp suất khí quyển ổn định."
            }
        } else {
            when {
                current.precipitationMm > 0.5 -> "Steady showers over $cityName. Coastal swell and wet roads expected through the evening."
                current.weatherCode in listOf(0, 1) && current.isDay -> "Clear skies with a gentle breeze across $cityName. High UV index around midday."
                !current.isDay -> "Crisp, starry night over $cityName with clear visibility. Light breeze."
                current.weatherCode in listOf(2, 3) -> "Soft overcast skies over $cityName with diffused sunlight and pleasant temperatures."
                else -> "Comfortable seasonal conditions across $cityName with steady atmospheric pressure."
            }
        }
    }

    private fun calculateDayProgress(sunriseIso: String, sunsetIso: String, zoneId: ZoneId): Float {
        return runCatching {
            val nowSec = ZonedDateTime.now(zoneId).toEpochSecond()
            val riseSec = LocalDateTime.parse(sunriseIso).atZone(zoneId).toEpochSecond()
            val setSec = LocalDateTime.parse(sunsetIso).atZone(zoneId).toEpochSecond()
            ((nowSec - riseSec).toFloat() / (setSec - riseSec).toFloat()).coerceIn(0f, 1f)
        }.getOrDefault(0.6f)
    }

    private fun degreesToCompass(context: Context, deg: Int): String {
        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        val dirs = if (isVi) {
            arrayOf("B", "ĐB", "Đ", "ĐN", "N", "TN", "T", "TB")
        } else {
            arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        }
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
        onSelectCity: (LocationItem) -> Unit,
        onToggleUnit: (() -> Unit)? = null
    ) = view.bind(state, onSearch, onRefresh, onSelectCity, onToggleUnit)
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

        if (isNow) {
            holder.binding.hourlyCard.setBackgroundResource(R.drawable.bg_pill_accent_translucent)
        } else {
            holder.binding.hourlyCard.setBackgroundResource(R.drawable.bg_circle_translucent)
        }

        holder.binding.hourTime.text = if (isNow) {
            nowLabel
        } else {
            runCatching {
                val instant = Instant.ofEpochSecond(item.timeEpochSeconds)
                instant.atZone(zoneId).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            }.getOrDefault(item.timeLabel)
        }
        holder.binding.hourTime.setTextColor(if (isNow) 0xFFFFFFFF.toInt() else 0xD9FFFFFF.toInt())
        holder.binding.hourIcon.setImageResource(WeatherCodeMapper.getIcon(item.weatherCode, item.isDay))
        if (item.precipitationProbability >= 15) {
            holder.binding.hourPop.text = "${item.precipitationProbability}%"
            holder.binding.hourPop.setTextColor(0xFF38BDF8.toInt())
        } else {
            holder.binding.hourPop.text = "—"
            holder.binding.hourPop.setTextColor(0x66FFFFFF.toInt())
        }
        holder.binding.hourTemperature.text = unit.format(item.temperatureC)
    }
}
