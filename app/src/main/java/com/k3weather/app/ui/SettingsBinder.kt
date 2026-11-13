package com.k3weather.app.ui

import android.content.res.ColorStateList
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.LayoutSettingsBinding

class SettingsBinder(
    private val binding: LayoutSettingsBinding,
    private val onSettingsChanged: (AppSettings) -> Unit
) {
    fun bind(settings: AppSettings) {
        val context = binding.root.context

        // Units
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

        // Stateful theme color for switches
        val accentColor = settings.colorScheme.primaryAccent
        val activeTrackColor = (accentColor and 0x00FFFFFF) or 0x73000000
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbTintList = ColorStateList(
            states,
            intArrayOf(accentColor, 0xFFB0BEC5.toInt())
        )
        val trackTintList = ColorStateList(
            states,
            intArrayOf(activeTrackColor, 0x3DFFFFFF)
        )

        binding.reduceMotionSwitch.thumbTintList = thumbTintList
        binding.reduceMotionSwitch.trackTintList = trackTintList
        binding.currentLocationSwitch.thumbTintList = thumbTintList
        binding.currentLocationSwitch.trackTintList = trackTintList

        binding.reduceMotionSwitch.setOnCheckedChangeListener(null)
        binding.reduceMotionSwitch.isChecked = settings.reduceMotion
        binding.reduceMotionSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(reduceMotion = checked))
        }
        binding.rowReduceMotion.setOnClickListener {
            binding.reduceMotionSwitch.toggle()
        }

        // Language: simple click to toggle directly like wind speed and temperature
        val langLabel = if (settings.language == "vi") "Tiếng Việt" else "English"
        binding.tvLanguageValue.text = "$langLabel  ›"
        binding.rowLanguage.setOnClickListener {
            val next = if (settings.language == "vi") "en" else "vi"
            send(settings.copy(language = next))
        }

        // Location
        binding.currentLocationSwitch.setOnCheckedChangeListener(null)
        binding.currentLocationSwitch.isChecked = settings.useCurrentLocation
        binding.currentLocationSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(useCurrentLocation = checked))
        }
        binding.rowCurrentLocation.setOnClickListener {
            binding.currentLocationSwitch.toggle()
        }
    }

    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
