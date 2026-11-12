package com.k3weather.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
    private var reduceMotion = false
    private var colorScheme = AppColorScheme.CYAN

    class Holder(val binding: ItemSavedLocationBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(
        locations: List<LocationItem>,
        forecasts: Map<String, ForecastData>,
        unit: TemperatureUnit,
        backgroundsEnabled: Boolean,
        reduceMotion: Boolean = false,
        colorScheme: AppColorScheme = AppColorScheme.CYAN
    ) {
        this.locations = locations
        this.forecasts = forecasts
        this.unit = unit
        this.backgroundsEnabled = backgroundsEnabled
        this.reduceMotion = reduceMotion
        this.colorScheme = colorScheme
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemSavedLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = locations.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val location = locations[position]
        val forecast = forecasts[location.id]
        val context = holder.binding.root.context

        holder.binding.cityName.text = location.name

        val weatherCode = forecast?.current?.weatherCode ?: 1
        val isDay = forecast?.current?.isDay ?: true

        val conditionText = if (forecast != null) {
            WeatherCodeMapper.getLocalizedLabel(context, weatherCode)
        } else if (location.subtitle.isNotBlank()) {
            location.subtitle
        } else {
            "Vietnam"
        }
        holder.binding.condition.text = conditionText
        holder.binding.temperature.text = forecast?.let { unit.format(it.current.temperatureC) } ?: "--°"

        // Current location badge
        holder.binding.ivCurrentLocationBadge.visibility =
            if (location.isCurrentLocation) android.view.View.VISIBLE else android.view.View.GONE

        // Weather icon
        holder.binding.ivWeatherIcon.setImageResource(WeatherCodeMapper.getIcon(weatherCode, isDay))

        // Card weather effect background
        WeatherBackgroundHelper.applyWeatherEffect(
            view = holder.binding.weatherEffectBackground,
            weatherCode = weatherCode,
            isDay = isDay,
            reduceMotion = reduceMotion,
            colorScheme = colorScheme,
            enabled = backgroundsEnabled
        )

        holder.binding.root.setOnClickListener { onSelect(location) }
        holder.binding.deleteButton.setOnClickListener { onDelete(location) }
    }
}

class SavedLocationsBinder(private val adapter: SavedLocationsAdapter) {
    fun bind(state: WeatherUiState, forecasts: Map<String, ForecastData>) {
        adapter.submit(
            locations = state.savedLocations,
            forecasts = forecasts,
            unit = state.settings.tempUnit,
            backgroundsEnabled = state.settings.weatherBackgroundsEnabled,
            reduceMotion = state.settings.reduceMotion,
            colorScheme = state.settings.colorScheme
        )
    }
}
