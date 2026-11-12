package com.k3weather.app.ui.common

import com.k3weather.app.core.model.AppColorScheme
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.mapper.WeatherCondition

object WeatherBackgroundHelper {

    fun applyWeatherEffect(
        view: WeatherEffectView,
        weatherCode: Int,
        isDay: Boolean,
        reduceMotion: Boolean = false,
        colorScheme: AppColorScheme = AppColorScheme.CYAN,
        enabled: Boolean = true
    ) {
        if (!enabled) {
            view.visibility = android.view.View.GONE
            return
        }
        view.visibility = android.view.View.VISIBLE
        val condition = WeatherCodeMapper.map(weatherCode).condition
        view.setWeather(
            condition = condition,
            isDay = isDay,
            reduceMotion = reduceMotion,
            colorScheme = colorScheme
        )
    }

    fun conditionFromText(conditionText: String): WeatherCondition {
        val lower = conditionText.lowercase()
        return when {
            lower.contains("storm") || lower.contains("thunder") -> WeatherCondition.THUNDER
            lower.contains("rain") || lower.contains("drizzle") || lower.contains("shower") -> WeatherCondition.RAIN
            lower.contains("snow") -> WeatherCondition.SNOW
            lower.contains("fog") || lower.contains("mist") -> WeatherCondition.FOG
            lower.contains("cloud") || lower.contains("overcast") -> WeatherCondition.CLOUDY
            else -> WeatherCondition.CLEAR
        }
    }
}
