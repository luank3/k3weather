package com.k3weather.app.ui

import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.LayoutSettingsBinding

class SettingsBinder(private val binding: LayoutSettingsBinding, private val onSettingsChanged: (AppSettings) -> Unit) {
    fun bind(settings: AppSettings) {
        binding.tvTemperatureUnitValue.text = if (settings.tempUnit == TemperatureUnit.CELSIUS) "°C  ›" else "°F  ›"
        binding.tvWindSpeedUnitValue.text = if (settings.windSpeedUnit == WindSpeedUnit.KMH) "km/h  ›" else "mph  ›"

        binding.rowTemperature.setOnClickListener {
            val next = if (settings.tempUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            send(settings.copy(tempUnit = next))
        }

        binding.rowWindSpeed.setOnClickListener {
            val next = if (settings.windSpeedUnit == WindSpeedUnit.KMH) WindSpeedUnit.MPH else WindSpeedUnit.KMH
            send(settings.copy(windSpeedUnit = next))
        }

        binding.weatherBackgroundsSwitch.setOnCheckedChangeListener(null)
        binding.weatherBackgroundsSwitch.isChecked = settings.weatherBackgroundsEnabled
        binding.weatherBackgroundsSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(weatherBackgroundsEnabled = checked))
        }

        binding.reduceMotionSwitch.setOnCheckedChangeListener(null)
        binding.reduceMotionSwitch.isChecked = settings.reduceMotion
        binding.reduceMotionSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(reduceMotion = checked))
        }

        binding.currentLocationSwitch.setOnCheckedChangeListener(null)
        binding.currentLocationSwitch.isChecked = settings.useCurrentLocation
        binding.currentLocationSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(useCurrentLocation = checked))
        }
    }
    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
