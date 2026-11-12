package com.k3weather.app.ui

import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.LayoutSettingsBinding

class SettingsBinder(private val binding: LayoutSettingsBinding, private val onSettingsChanged: (AppSettings) -> Unit) {
    fun bind(settings: AppSettings) {
        binding.unitCelsius.isChecked = settings.tempUnit == TemperatureUnit.CELSIUS
        binding.unitFahrenheit.isChecked = settings.tempUnit == TemperatureUnit.FAHRENHEIT
        binding.unitKmh.isChecked = settings.windSpeedUnit == WindSpeedUnit.KMH
        binding.unitMph.isChecked = settings.windSpeedUnit == WindSpeedUnit.MPH
        binding.weatherBackgroundsSwitch.isChecked = settings.weatherBackgroundsEnabled
        binding.reduceMotionSwitch.isChecked = settings.reduceMotion
        binding.currentLocationSwitch.isChecked = settings.useCurrentLocation
        binding.apiEndpoint.setText(settings.customApiBaseUrl.orEmpty())
        binding.unitCelsius.setOnClickListener { send(settings.copy(tempUnit = TemperatureUnit.CELSIUS)) }
        binding.unitFahrenheit.setOnClickListener { send(settings.copy(tempUnit = TemperatureUnit.FAHRENHEIT)) }
        binding.unitKmh.setOnClickListener { send(settings.copy(windSpeedUnit = WindSpeedUnit.KMH)) }
        binding.unitMph.setOnClickListener { send(settings.copy(windSpeedUnit = WindSpeedUnit.MPH)) }
        binding.weatherBackgroundsSwitch.setOnCheckedChangeListener { _, checked -> send(settings.copy(weatherBackgroundsEnabled = checked)) }
        binding.reduceMotionSwitch.setOnCheckedChangeListener { _, checked -> send(settings.copy(reduceMotion = checked)) }
        binding.currentLocationSwitch.setOnCheckedChangeListener { _, checked -> send(settings.copy(useCurrentLocation = checked)) }
        binding.apiEndpoint.setOnFocusChangeListener { _, focused -> if (!focused) send(settings.copy(customApiBaseUrl = binding.apiEndpoint.text?.toString()?.trim()?.takeIf(String::isNotBlank)) ) }
    }
    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
