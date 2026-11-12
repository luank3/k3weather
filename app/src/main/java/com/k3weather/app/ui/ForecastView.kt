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
        binding.citySubtitle.text = forecast.location.subtitle
        binding.temperature.text = unit.format(forecast.current.temperatureC)
        binding.condition.text = forecast.current.condition
        binding.highLow.text = "H: ${unit.format(forecast.current.maxTempC)}   L: ${unit.format(forecast.current.minTempC)}"
        binding.hourlyList.adapter = HourlyAdapter(forecast.hourly, unit)

        val min = forecast.daily.minOfOrNull { it.minTempC } ?: 0.0
        val max = forecast.daily.maxOfOrNull { it.maxTempC } ?: 1.0
        binding.dailyRows.removeAllViews()
        forecast.daily.forEachIndexed { index, day ->
            val row = ItemDailyForecastBinding.inflate(LayoutInflater.from(binding.root.context), binding.dailyRows, false)
            row.dayLabel.text = if (index == 0) "Today" else day.dayLabel
            row.dayIcon.setImageResource(weatherIcon(day.weatherCode))
            row.minTemperature.text = unit.format(day.minTempC)
            row.maxTemperature.text = unit.format(day.maxTempC)
            val width = (row.temperatureRange.layoutParams as ViewGroup.MarginLayoutParams)
            width.width = ViewGroup.LayoutParams.MATCH_PARENT
            row.temperatureRange.layoutParams = width
            row.temperatureRange.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00E5FF.toInt())
            row.temperatureRange.contentDescription = "${(day.minTempC - min) / (max - min).coerceAtLeast(1.0)}, ${(day.maxTempC - min) / (max - min).coerceAtLeast(1.0)}"
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
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.hourTime.text = item.timeLabel
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
