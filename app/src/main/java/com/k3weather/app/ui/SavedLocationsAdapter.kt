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
        holder.binding.condition.text = forecast?.current?.condition ?: location.subtitle
        holder.binding.temperature.text = forecast?.let { unit.format(it.current.temperatureC) } ?: "--°"
        WeatherBackgroundHelper.apply(holder.binding.root, location, forecast?.current?.condition.orEmpty(), forecast?.current?.isDay ?: true, backgroundsEnabled)
        holder.binding.cityBackground.setImageDrawable(WeatherBackgroundHelper.cityDrawable(holder.binding.root.context, location))
        holder.binding.root.setOnClickListener { onSelect(location) }
        holder.binding.deleteButton.setOnClickListener { onDelete(location) }
    }
}

class SavedLocationsBinder(private val adapter: SavedLocationsAdapter) {
    fun bind(state: WeatherUiState, forecasts: Map<String, ForecastData>) {
        adapter.submit(state.savedLocations, forecasts, state.settings.tempUnit, state.settings.weatherBackgroundsEnabled)
    }
}
