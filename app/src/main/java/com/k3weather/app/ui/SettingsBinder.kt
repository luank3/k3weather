package com.k3weather.app.ui

import android.content.res.ColorStateList
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.LayoutSettingsBinding

class SettingsBinder(
    private val binding: LayoutSettingsBinding,
    private val onClearCache: () -> Unit = {},
    private val onSettingsChanged: (AppSettings) -> Unit
) {
    fun bind(settings: AppSettings) {
        val context = binding.root.context

        // Units
        binding.tvTemperatureUnitValue.text = if (settings.tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F"
        binding.tvWindSpeedUnitValue.text = when (settings.windSpeedUnit) {
            WindSpeedUnit.KMH -> "km/h"
            WindSpeedUnit.MS -> "m/s"
            WindSpeedUnit.MPH -> "mph"
        }

        binding.rowTemperature.setOnClickListener {
            val next = if (settings.tempUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            send(settings.copy(tempUnit = next))
        }

        binding.rowWindSpeed.setOnClickListener {
            val next = when (settings.windSpeedUnit) {
                WindSpeedUnit.KMH -> WindSpeedUnit.MS
                WindSpeedUnit.MS -> WindSpeedUnit.MPH
                WindSpeedUnit.MPH -> WindSpeedUnit.KMH
            }
            send(settings.copy(windSpeedUnit = next))
        }

        binding.tvPrecipitationUnitValue.text = settings.precipitationUnit.symbol
        binding.rowPrecipitation.setOnClickListener {
            val next = if (settings.precipitationUnit == PrecipitationUnit.MILLIMETERS) PrecipitationUnit.INCHES else PrecipitationUnit.MILLIMETERS
            send(settings.copy(precipitationUnit = next))
        }
        binding.tvPressureUnitValue.text = settings.pressureUnit.symbol
        binding.rowPressure.setOnClickListener {
            val next = when (settings.pressureUnit) {
                PressureUnit.HPA -> PressureUnit.INHG
                PressureUnit.INHG -> PressureUnit.MMHG
                PressureUnit.MMHG -> PressureUnit.HPA
            }
            send(settings.copy(pressureUnit = next))
        }
        binding.tvVersionValue.text = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"

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


        // Language: simple click to toggle directly like wind speed and temperature
        val langLabel = if (settings.language == "vi") "Tiếng Việt" else "English"
        binding.tvLanguageValue.text = langLabel
        binding.rowLanguage.setOnClickListener {
            val next = if (settings.language == "vi") "en" else "vi"
            send(settings.copy(language = next))
        }
    }

    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
