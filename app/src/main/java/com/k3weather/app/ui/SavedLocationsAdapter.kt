package com.k3weather.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.databinding.ItemSavedLocationBinding
import com.k3weather.app.ui.common.WeatherBackgroundHelper

class SavedLocationsAdapter(
    private val onSelect: (LocationItem) -> Unit,
    private val onDelete: (LocationItem) -> Unit
) : RecyclerView.Adapter<SavedLocationsAdapter.Holder>() {
    private var locations: List<LocationItem> = emptyList()
    private var forecasts: Map<String, ForecastData> = emptyMap()
    private var unit = TemperatureUnit.CELSIUS
    private var backgroundsEnabled = true
    class Holder(val binding: ItemSavedLocationBinding) : RecyclerView.ViewHolder(binding.root)
    fun submit(locations: List<LocationItem>, forecasts: Map<String, ForecastData>, unit: TemperatureUnit, backgroundsEnabled: Boolean) {
        this.locations = locations; this.forecasts = forecasts; this.unit = unit; this.backgroundsEnabled = backgroundsEnabled; notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemSavedLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = locations.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val location = locations[position]
        val forecast = forecasts[location.id]
        holder.binding.cityName.text = location.name
        holder.binding.condition.text = forecast?.current?.condition ?: if (location.subtitle.isNotBlank()) location.subtitle else "Vietnam"
        holder.binding.temperature.text = forecast?.let { unit.format(it.current.temperatureC) } ?: "--°"
        
        // Current location badge
        holder.binding.ivCurrentLocationBadge.visibility = if (location.isCurrentLocation) android.view.View.VISIBLE else android.view.View.GONE

        // Weather icon
        val code = forecast?.current?.weatherCode ?: 1
        val isDay = forecast?.current?.isDay ?: true
        holder.binding.ivWeatherIcon.setImageResource(weatherIcon(code, isDay))

        WeatherBackgroundHelper.apply(holder.binding.root, location, forecast?.current?.condition.orEmpty(), forecast?.current?.isDay ?: true, backgroundsEnabled)
        holder.binding.cityBackground.setImageDrawable(WeatherBackgroundHelper.cityDrawable(holder.binding.root.context, location))
        holder.binding.root.setOnClickListener { onSelect(location) }
        holder.binding.deleteButton.setOnClickListener { onDelete(location) }
    }
}

private fun weatherIcon(code: Int, isDay: Boolean = true): Int = when (WeatherCodeMapper.map(code).condition) {
    com.k3weather.app.data.mapper.WeatherCondition.CLEAR -> if (isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
    com.k3weather.app.data.mapper.WeatherCondition.CLOUDY -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_cloudy
    com.k3weather.app.data.mapper.WeatherCondition.FOG -> R.drawable.ic_weather_fog
    com.k3weather.app.data.mapper.WeatherCondition.RAIN -> R.drawable.ic_weather_rain
    com.k3weather.app.data.mapper.WeatherCondition.SNOW -> R.drawable.ic_weather_snow
    com.k3weather.app.data.mapper.WeatherCondition.THUNDER -> R.drawable.ic_weather_thunder
}

class SavedLocationsBinder(private val adapter: SavedLocationsAdapter) {
    fun bind(state: WeatherUiState, forecasts: Map<String, ForecastData>) {
        adapter.submit(state.savedLocations, forecasts, state.settings.tempUnit, state.settings.weatherBackgroundsEnabled)
    }
}
