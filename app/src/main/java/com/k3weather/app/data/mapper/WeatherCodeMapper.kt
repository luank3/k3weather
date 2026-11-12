package com.k3weather.app.data.mapper

import android.content.Context
import com.k3weather.app.R

enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    SLEET,
    SNOW,
    THUNDER,
    THUNDER_HAIL
}

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
        2 -> WeatherDescription("Partly cloudy", WeatherCondition.PARTLY_CLOUDY, R.string.condition_partly_cloudy)
        3 -> WeatherDescription("Overcast", WeatherCondition.CLOUDY, R.string.condition_overcast)
        45, 48 -> WeatherDescription("Fog", WeatherCondition.FOG, R.string.condition_fog)
        51, 53 -> WeatherDescription("Drizzle", WeatherCondition.DRIZZLE, R.string.condition_drizzle)
        55 -> WeatherDescription("Dense drizzle", WeatherCondition.DRIZZLE, R.string.condition_drizzle)
        56, 57 -> WeatherDescription("Freezing drizzle", WeatherCondition.SLEET, R.string.condition_freezing_drizzle)
        61, 63 -> WeatherDescription("Rain", WeatherCondition.RAIN, R.string.condition_rain)
        65 -> WeatherDescription("Heavy rain", WeatherCondition.HEAVY_RAIN, R.string.condition_heavy_rain)
        66, 67 -> WeatherDescription("Freezing rain", WeatherCondition.SLEET, R.string.condition_freezing_rain)
        71, 73 -> WeatherDescription("Snow", WeatherCondition.SNOW, R.string.condition_snow)
        75, 77 -> WeatherDescription("Heavy snow", WeatherCondition.SNOW, R.string.condition_heavy_snow)
        80, 81 -> WeatherDescription("Rain showers", WeatherCondition.RAIN, R.string.condition_rain_showers)
        82 -> WeatherDescription("Violent rain showers", WeatherCondition.HEAVY_RAIN, R.string.condition_heavy_rain)
        85, 86 -> WeatherDescription("Snow showers", WeatherCondition.SNOW, R.string.condition_snow_showers)
        95 -> WeatherDescription("Thunderstorm", WeatherCondition.THUNDER, R.string.condition_thunderstorm)
        96, 99 -> WeatherDescription("Thunderstorm with hail", WeatherCondition.THUNDER_HAIL, R.string.condition_thunderstorm_hail)
        else -> WeatherDescription("Unknown", WeatherCondition.CLOUDY, R.string.condition_unknown)
    }

    fun getLocalizedLabel(context: Context, code: Int): String {
        return context.getString(map(code).labelResId)
    }

    fun getIcon(code: Int, isDay: Boolean = true): Int {
        val condition = map(code).condition
        return when (condition) {
            WeatherCondition.CLEAR -> if (isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
            WeatherCondition.PARTLY_CLOUDY -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_partly_cloudy_night
            WeatherCondition.CLOUDY -> R.drawable.ic_weather_cloudy
            WeatherCondition.FOG -> R.drawable.ic_weather_fog
            WeatherCondition.DRIZZLE -> R.drawable.ic_weather_drizzle
            WeatherCondition.RAIN -> R.drawable.ic_weather_rain
            WeatherCondition.HEAVY_RAIN -> R.drawable.ic_weather_heavy_rain
            WeatherCondition.SLEET -> R.drawable.ic_weather_sleet
            WeatherCondition.SNOW -> R.drawable.ic_weather_snow
            WeatherCondition.THUNDER -> R.drawable.ic_weather_thunder
            WeatherCondition.THUNDER_HAIL -> R.drawable.ic_weather_thunder_hail
        }
    }
}
