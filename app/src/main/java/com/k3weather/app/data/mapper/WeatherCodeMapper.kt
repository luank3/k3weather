package com.k3weather.app.data.mapper

import android.content.Context
import com.k3weather.app.R

enum class WeatherCondition { CLEAR, CLOUDY, FOG, RAIN, SNOW, THUNDER }

data class WeatherDescription(
    val label: String,
    val condition: WeatherCondition,
    val labelResId: Int = R.string.condition_unknown
) {
    fun localized(context: Context): String =
        context.getString(labelResId)
}

object WeatherCodeMapper {
    fun map(code: Int): WeatherDescription = when (code) {
        0 -> WeatherDescription("Clear sky", WeatherCondition.CLEAR, R.string.condition_clear_sky)
        1 -> WeatherDescription("Mainly clear", WeatherCondition.CLEAR, R.string.condition_mainly_clear)
        2 -> WeatherDescription("Partly cloudy", WeatherCondition.CLOUDY, R.string.condition_partly_cloudy)
        3 -> WeatherDescription("Overcast", WeatherCondition.CLOUDY, R.string.condition_overcast)
        45, 48 -> WeatherDescription("Fog", WeatherCondition.FOG, R.string.condition_fog)
        51, 53, 55 -> WeatherDescription("Drizzle", WeatherCondition.RAIN, R.string.condition_drizzle)
        56, 57 -> WeatherDescription("Freezing drizzle", WeatherCondition.RAIN, R.string.condition_freezing_drizzle)
        61, 63, 65 -> WeatherDescription("Rain", WeatherCondition.RAIN, R.string.condition_rain)
        66, 67 -> WeatherDescription("Freezing rain", WeatherCondition.RAIN, R.string.condition_freezing_rain)
        71, 73, 75, 77 -> WeatherDescription("Snow", WeatherCondition.SNOW, R.string.condition_snow)
        80, 81, 82 -> WeatherDescription("Rain showers", WeatherCondition.RAIN, R.string.condition_rain_showers)
        85, 86 -> WeatherDescription("Snow showers", WeatherCondition.SNOW, R.string.condition_snow_showers)
        in 95..99 -> WeatherDescription("Thunderstorm", WeatherCondition.THUNDER, R.string.condition_thunderstorm)
        else -> WeatherDescription("Unknown", WeatherCondition.CLOUDY, R.string.condition_unknown)
    }

    fun getLocalizedLabel(context: Context, code: Int): String {
        return context.getString(map(code).labelResId)
    }
}
