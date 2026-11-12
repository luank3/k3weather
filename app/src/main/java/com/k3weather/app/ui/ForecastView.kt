package com.k3weather.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.ItemDailyForecastBinding
import com.k3weather.app.databinding.ItemHourlyForecastBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.mapper.WeatherCondition
import com.k3weather.app.ui.common.WeatherBackgroundHelper

class ForecastView(private val binding: LayoutForecastBinding) {
    fun bind(state: WeatherUiState, onSearch: () -> Unit, onRefresh: () -> Unit) {
        binding.searchButton.setOnClickListener { onSearch() }
        binding.refreshButton.setOnClickListener { onRefresh() }
        binding.retryButton.setOnClickListener { onRefresh() }
        binding.forecastProgress.visibility = if (state.loading) View.VISIBLE else View.GONE
        binding.retryButton.visibility = if (state.error != null && state.forecast == null) View.VISIBLE else View.GONE
        binding.staleBadge.visibility = if (state.forecast?.isStale == true) View.VISIBLE else View.GONE

        val loc = state.forecast?.location ?: state.selectedLocation
        if (loc != null) {
            binding.cityName.text = loc.name
            binding.citySubtitle.text = loc.subtitle
        }

        val forecast = state.forecast ?: return
        val unit = state.settings.tempUnit
        binding.cityName.text = forecast.location.name
        binding.citySubtitle.text = forecast.location.subtitle.ifBlank { forecast.location.country }
        binding.temperature.text = unit.format(forecast.current.temperatureC)
        binding.condition.text = forecast.current.condition
        binding.highLow.text = "H ${unit.format(forecast.current.maxTempC)} • L ${unit.format(forecast.current.minTempC)}"

        // Top text color: dark navy on Đà Lạt daytime sunny sky matching mockup, otherwise white
        val isDalatDay = forecast.current.isDay && (forecast.location.name.contains("Đà Lạt") || forecast.location.name.contains("Da Lat"))
        val topTextColor = if (isDalatDay) 0xFF0E1E2F.toInt() else 0xFFFFFFFF.toInt()
        val topSubtitleColor = if (isDalatDay) 0xCC1E293B.toInt() else 0xCCFFFFFF.toInt()
        binding.cityName.setTextColor(topTextColor)
        binding.citySubtitle.setTextColor(topSubtitleColor)
        binding.temperature.setTextColor(topTextColor)
        binding.condition.setTextColor(topTextColor)
        binding.highLow.setTextColor(topSubtitleColor)
        binding.ivLocationPin.imageTintList = android.content.res.ColorStateList.valueOf(topTextColor)
        binding.searchButton.imageTintList = android.content.res.ColorStateList.valueOf(topTextColor)

        // Hourly forecast
        binding.hourlyList.adapter = HourlyAdapter(forecast.hourly, unit)

        // Next 3 days forecast
        val minGlobal = forecast.daily.minOfOrNull { it.minTempC } ?: 0.0
        val maxGlobal = forecast.daily.maxOfOrNull { it.maxTempC } ?: (minGlobal + 1.0)
        val tempRange = (maxGlobal - minGlobal).coerceAtLeast(1.0)

        binding.dailyRows.removeAllViews()
        forecast.daily.take(3).forEachIndexed { index, day ->
            val row = ItemDailyForecastBinding.inflate(LayoutInflater.from(binding.root.context), binding.dailyRows, false)
            val dayName = if (index == 0) "Today" else {
                runCatching {
                    java.time.LocalDate.parse(day.dateIso).format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.US))
                }.getOrDefault(day.dayLabel)
            }
            row.dayLabel.text = dayName
            row.dayIcon.setImageResource(weatherIcon(day.weatherCode))
            row.minTemperature.text = unit.format(day.minTempC)
            row.maxTemperature.text = unit.format(day.maxTempC)

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
                    lp.width = barWidth
                    row.temperatureRange.layoutParams = lp
                }
            }

            binding.dailyRows.addView(row.root)
        }
    }
}

class ForecastBinder(private val view: ForecastView) {
    fun bind(state: WeatherUiState, onSearch: () -> Unit, onRefresh: () -> Unit) = view.bind(state, onSearch, onRefresh)
}

private class HourlyAdapter(private val items: List<HourlyForecast>, private val unit: TemperatureUnit) : RecyclerView.Adapter<HourlyAdapter.Holder>() {
    class Holder(val binding: ItemHourlyForecastBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemHourlyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = minOf(5, items.size)
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.hourTime.text = if (position == 0) "Now" else {
            runCatching {
                val instant = java.time.Instant.ofEpochSecond(item.timeEpochSeconds)
                val zone = java.time.ZoneId.systemDefault()
                instant.atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("h a", java.util.Locale.US))
            }.getOrDefault(item.timeLabel)
        }
        holder.binding.hourIcon.setImageResource(weatherIcon(item.weatherCode))
        holder.binding.hourTemperature.text = unit.format(item.temperatureC)
    }
}

private fun weatherIcon(code: Int): Int = when (WeatherCodeMapper.map(code).condition) {
    WeatherCondition.CLEAR -> R.drawable.ic_weather_clear_day
    WeatherCondition.CLOUDY -> R.drawable.ic_weather_partly_cloudy
    WeatherCondition.FOG -> R.drawable.ic_weather_fog
    WeatherCondition.RAIN -> R.drawable.ic_weather_rain
    WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    WeatherCondition.THUNDER -> R.drawable.ic_weather_thunder
}
