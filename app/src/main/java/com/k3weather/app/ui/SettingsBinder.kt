package com.k3weather.app.ui

import android.content.res.ColorStateList
import androidx.appcompat.app.AlertDialog
import com.k3weather.app.R
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

        // Apply theme color to switches
        val accentColor = settings.colorScheme.primaryAccent
        val trackColor = (accentColor and 0x00FFFFFF) or 0x66000000
        val thumbTintList = ColorStateList.valueOf(accentColor)
        val trackTintList = ColorStateList.valueOf(trackColor)

        binding.weatherBackgroundsSwitch.thumbTintList = thumbTintList
        binding.weatherBackgroundsSwitch.trackTintList = trackTintList
        binding.reduceMotionSwitch.thumbTintList = thumbTintList
        binding.reduceMotionSwitch.trackTintList = trackTintList
        binding.currentLocationSwitch.thumbTintList = thumbTintList
        binding.currentLocationSwitch.trackTintList = trackTintList

        // Switches
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

        // Color scheme
        val colorSchemeName = context.getString(settings.colorScheme.nameResId)
        binding.tvColorSchemeValue.text = "$colorSchemeName  ›"
        binding.tvColorSchemeValue.setTextColor(accentColor)
        binding.rowColorScheme.setOnClickListener {
            showColorSchemeDialog(settings)
        }

        // Language
        val langLabel = when (settings.language) {
            "en" -> context.getString(R.string.lang_en)
            "vi" -> context.getString(R.string.lang_vi)
            else -> context.getString(R.string.lang_system)
        }
        binding.tvLanguageValue.text = "$langLabel  ›"
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog(settings)
        }

        // Location
        binding.currentLocationSwitch.setOnCheckedChangeListener(null)
        binding.currentLocationSwitch.isChecked = settings.useCurrentLocation
        binding.currentLocationSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(useCurrentLocation = checked))
        }
    }

    private fun showColorSchemeDialog(settings: AppSettings) {
        val context = binding.root.context
        val schemes = AppColorScheme.entries.toTypedArray()
        val names = schemes.map { context.getString(it.nameResId) }.toTypedArray()
        val currentIndex = schemes.indexOf(settings.colorScheme).coerceAtLeast(0)

        AlertDialog.Builder(context)
            .setTitle(R.string.settings_color_scheme)
            .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                val selectedScheme = schemes[which]
                dialog.dismiss()
                send(settings.copy(colorScheme = selectedScheme))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLanguageDialog(settings: AppSettings) {
        val context = binding.root.context
        val langCodes = arrayOf("system", "en", "vi")
        val langNames = arrayOf(
            context.getString(R.string.lang_system),
            context.getString(R.string.lang_en),
            context.getString(R.string.lang_vi)
        )
        val currentIndex = langCodes.indexOf(settings.language).coerceAtLeast(0)

        AlertDialog.Builder(context)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(langNames, currentIndex) { dialog, which ->
                val selectedLang = langCodes[which]
                dialog.dismiss()
                send(settings.copy(language = selectedLang))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
