package com.k3weather.app.ui

import android.content.res.ColorStateList
import android.view.HapticFeedbackConstants
import com.k3weather.app.BuildConfig
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.LayoutSettingsBinding
import com.k3weather.app.ui.common.MotionHelper

class SettingsBinder(
    private val binding: LayoutSettingsBinding,
    private val onClearCache: () -> Unit = {},
    private val onSettingsChanged: (AppSettings) -> Unit
) {
    fun bind(settings: AppSettings) {
        val context = binding.root.context
        val reduceMotion = settings.reduceMotion

        // Units
        MotionHelper.pulseTextChange(
            binding.tvTemperatureUnitValue,
            if (settings.tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F",
            reduceMotion
        )
        val windLabel = when (settings.windSpeedUnit) {
            WindSpeedUnit.KMH -> "km/h"
            WindSpeedUnit.MS -> "m/s"
            WindSpeedUnit.MPH -> "mph"
        }
        MotionHelper.pulseTextChange(binding.tvWindSpeedUnitValue, windLabel, reduceMotion)

        binding.rowTemperature.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val next = if (settings.tempUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            send(settings.copy(tempUnit = next))
        }

        binding.rowWindSpeed.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val next = when (settings.windSpeedUnit) {
                WindSpeedUnit.KMH -> WindSpeedUnit.MS
                WindSpeedUnit.MS -> WindSpeedUnit.MPH
                WindSpeedUnit.MPH -> WindSpeedUnit.KMH
            }
            send(settings.copy(windSpeedUnit = next))
        }

        MotionHelper.pulseTextChange(binding.tvPrecipitationUnitValue, settings.precipitationUnit.symbol, reduceMotion)
        binding.rowPrecipitation.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val next = if (settings.precipitationUnit == PrecipitationUnit.MILLIMETERS) PrecipitationUnit.INCHES else PrecipitationUnit.MILLIMETERS
            send(settings.copy(precipitationUnit = next))
        }
        MotionHelper.pulseTextChange(binding.tvPressureUnitValue, settings.pressureUnit.symbol, reduceMotion)
        binding.rowPressure.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            val next = when (settings.pressureUnit) {
                PressureUnit.HPA -> PressureUnit.INHG
                PressureUnit.INHG -> PressureUnit.MMHG
                PressureUnit.MMHG -> PressureUnit.HPA
            }
            send(settings.copy(pressureUnit = next))
        }
        binding.tvVersionValue.text = "${BuildConfig.VERSION_SEMVER} (${BuildConfig.GIT_HASH})"

        // Language toggle
        val effective = if (settings.language == "system") {
            if (context.resources.configuration.locales[0]?.language == "vi") "vi" else "en"
        } else settings.language
        binding.tvLanguageValue.text = if (effective == "vi") "Tiếng Việt" else "English"
        binding.rowLanguage.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            send(settings.copy(language = if (effective == "vi") "en" else "vi"))
        }

        MotionHelper.attachPressScale(binding.rowTemperature, pressedScale = 0.985f)
        MotionHelper.attachPressScale(binding.rowWindSpeed, pressedScale = 0.985f)
        MotionHelper.attachPressScale(binding.rowPrecipitation, pressedScale = 0.985f)
        MotionHelper.attachPressScale(binding.rowPressure, pressedScale = 0.985f)
        MotionHelper.attachPressScale(binding.rowLanguage, pressedScale = 0.985f)
    }

    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
