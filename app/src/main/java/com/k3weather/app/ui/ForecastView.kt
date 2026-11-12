package com.k3weather.app.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.ItemDailyForecastBinding
import com.k3weather.app.databinding.ItemHourlyForecastBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.data.mapper.WeatherCodeMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        binding.refreshButton.setOnClickListener { onRefresh() }
        binding.retryButton.setOnClickListener { onRefresh() }
        binding.btnCurrentLocation.setOnClickListener { onReturnToCurrentLocation() }
        binding.btnReturnCurrentLocation.setOnClickListener { onReturnToCurrentLocation() }

        binding.forecastProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.retryButton.visibility = if (state.error != null && state.forecast == null) View.VISIBLE else View.GONE
        binding.staleBadge.visibility = if (state.forecast?.isStale == true) View.VISIBLE else View.GONE

        // Accent styling for return buttons
        binding.btnCurrentLocation.imageTintList = accentTintList
        binding.ivReturnIcon.imageTintList = accentTintList
        binding.tvReturnLabel.setTextColor(accentColor)

        val loc = state.forecast?.location ?: state.selectedLocation
        if (loc != null) {
            binding.cityName.text = loc.name
            binding.citySubtitle.text = loc.subtitle.ifBlank { loc.country }
        }

        // Return to current location button visibility
        val isCurrentLocation = loc?.isCurrentLocation == true ||
                (state.detectedCurrentLocation != null && loc?.id == state.detectedCurrentLocation.id)

        val showReturnAction = !isCurrentLocation && state.detectedCurrentLocation != null
        binding.btnCurrentLocation.visibility = if (showReturnAction) View.VISIBLE else View.GONE
        binding.btnReturnCurrentLocation.visibility = if (showReturnAction) View.VISIBLE else View.GONE

        val forecast = state.forecast ?: return
        val tempUnit = state.settings.tempUnit
        val windUnit = state.settings.windSpeedUnit

        binding.cityName.text = forecast.location.name
        binding.citySubtitle.text = forecast.location.subtitle.ifBlank { forecast.location.country }
        binding.temperature.text = tempUnit.format(forecast.current.temperatureC)
        binding.condition.text = WeatherCodeMapper.getLocalizedLabel(context, forecast.current.weatherCode)
        binding.highLow.text = "H ${tempUnit.format(forecast.current.maxTempC)} • L ${tempUnit.format(forecast.current.minTempC)}"
        binding.tvWindSpeed.text = windUnit.format(forecast.current.windSpeedKmh)

        // Text styling
        binding.cityName.setTextColor(0xFFFFFFFF.toInt())
        binding.citySubtitle.setTextColor(0xCCFFFFFF.toInt())
        binding.temperature.setTextColor(0xFFFFFFFF.toInt())
        binding.condition.setTextColor(0xFFFFFFFF.toInt())
        binding.highLow.setTextColor(0xCCFFFFFF.toInt())
        binding.tvWindSpeed.setTextColor(0xCCFFFFFF.toInt())
        binding.ivLocationPin.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        binding.searchButton.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())

        // Hourly forecast
        val locationZone = runCatching { ZoneId.of(forecast.location.timezone) }.getOrDefault(ZoneId.systemDefault())
        binding.hourlyList.adapter = HourlyAdapter(
            items = forecast.hourly,
            unit = tempUnit,
            nowLabel = context.getString(R.string.time_now),
            accentColor = accentColor,
            zoneId = locationZone
        )

        // Next 3 days forecast
        val minGlobal = forecast.daily.minOfOrNull { it.minTempC } ?: 0.0
        val maxGlobal = forecast.daily.maxOfOrNull { it.maxTempC } ?: (minGlobal + 1.0)
        val tempRange = (maxGlobal - minGlobal).coerceAtLeast(1.0)

        binding.dailyRows.removeAllViews()
        val currentLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()

        val daysToRender = forecast.daily.take(3)
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
            row.minTemperature.text = tempUnit.format(day.minTempC)
            row.maxTemperature.text = tempUnit.format(day.maxTempC)

            // Dynamic gradient bar with current color scheme
            val barGradient = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(colorScheme.gradientStart, colorScheme.gradientEnd)
            ).apply {
                cornerRadius = 999f
            }
            row.temperatureRange.background = barGradient

            // Dynamic gradient temperature bar width & left margin
            row.temperatureRangeContainer.post {
                val totalWidth = row.temperatureRangeContainer.width.toFloat()
                if (totalWidth > 0) {
                    val minRatio = ((day.minTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val maxRatio = ((day.maxTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val leftMargin = (totalWidth * minRatio).toInt()
                    val barWidth = (totalWidth * (maxRatio - minRatio)).toInt().coerceAtLeast(24)

                    val lp = row.temperatureRange.layoutParams as android.widget.FrameLayout.LayoutParams
                    lp.leftMargin = leftMargin
                    lp.marginStart = leftMargin
                    lp.width = barWidth
                    row.temperatureRange.layoutParams = lp
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

    override fun getItemCount(): Int = minOf(5, items.size)

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
        holder.binding.hourTemperature.text = unit.format(item.temperatureC)
    }
}
