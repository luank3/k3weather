package com.k3weather.app.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
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
    private var reduceMotion = false
    private var colorScheme = AppColorScheme.CYAN
    private var selectedLocationId: String? = null

    class Holder(val binding: ItemSavedLocationBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(
        locations: List<LocationItem>,
        forecasts: Map<String, ForecastData>,
        unit: TemperatureUnit,
        backgroundsEnabled: Boolean,
        reduceMotion: Boolean = false,
        colorScheme: AppColorScheme = AppColorScheme.CYAN,
        selectedLocationId: String? = null
    ) {
        this.locations = locations
        this.forecasts = forecasts
        this.unit = unit
        this.backgroundsEnabled = backgroundsEnabled
        this.reduceMotion = reduceMotion
        this.colorScheme = colorScheme
        this.selectedLocationId = selectedLocationId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSavedLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.root.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 24f * view.resources.displayMetrics.density)
            }
        }
        binding.root.clipToOutline = true
        return Holder(binding)
    }

    override fun getItemCount(): Int = locations.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val location = locations[position]
        val forecast = forecasts[location.id]
        val context = holder.binding.root.context

        holder.binding.cityName.text = location.name

        val weatherCode = forecast?.current?.weatherCode ?: 1
        val isDay = forecast?.current?.isDay ?: true

        val conditionText = if (forecast != null) {
            val label = WeatherCodeMapper.getLocalizedLabel(context, weatherCode)
            if (location.subtitle.isNotBlank()) "$label • ${location.subtitle}" else label
        } else if (location.subtitle.isNotBlank()) {
            location.subtitle
        } else {
            location.country
        }
        holder.binding.condition.text = conditionText
        holder.binding.temperature.text = forecast?.let { unit.format(it.current.temperatureC) } ?: "--°"
        holder.binding.tvCardHighLow.text = forecast?.let {
            context.getString(
                R.string.high_low_short_format,
                unit.format(it.current.maxTempC),
                unit.format(it.current.minTempC)
            )
        } ?: ""

        // Weather icon
        holder.binding.ivWeatherIcon.setImageResource(WeatherCodeMapper.getIcon(weatherCode, isDay))

        holder.binding.activeBorderOverlay.visibility = View.GONE

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
        if (location.isCurrentLocation) {
            holder.binding.deleteButton.setImageResource(R.drawable.ic_my_location)
            holder.binding.deleteButton.imageTintList = ColorStateList.valueOf(colorScheme.primaryAccent)
            holder.binding.deleteButton.contentDescription = context.getString(R.string.current_location)
            holder.binding.deleteButton.setOnClickListener { onSelect(location) }
        } else {
            holder.binding.deleteButton.setImageResource(R.drawable.ic_delete)
            holder.binding.deleteButton.imageTintList = ColorStateList.valueOf(0xD9FFFFFF.toInt())
            holder.binding.deleteButton.contentDescription = context.getString(R.string.remove_location)
            holder.binding.deleteButton.setOnClickListener { onDelete(location) }
        }
    }
}

class SavedLocationsBinder(private val adapter: SavedLocationsAdapter) {
    fun bind(state: WeatherUiState, forecasts: Map<String, ForecastData>) {
        val combinedLocations = buildList {
            state.detectedCurrentLocation?.let { cur ->
                add(cur.copy(isCurrentLocation = true))
            }
            state.savedLocations.forEach { loc ->
                if (none { it.id == loc.id }) add(loc)
            }
        }
        adapter.submit(
            locations = combinedLocations,
            forecasts = forecasts,
            unit = state.settings.tempUnit,
            backgroundsEnabled = state.settings.weatherBackgroundsEnabled,
            reduceMotion = state.settings.reduceMotion,
            colorScheme = state.settings.colorScheme,
            selectedLocationId = state.selectedLocation?.id
        )
    }
}
