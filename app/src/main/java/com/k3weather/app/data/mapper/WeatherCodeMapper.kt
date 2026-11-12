package com.k3weather.app.data.mapper

enum class WeatherCondition { CLEAR, CLOUDY, FOG, RAIN, SNOW, THUNDER }

data class WeatherDescription(val label: String, val condition: WeatherCondition)

object WeatherCodeMapper {
    fun map(code: Int): WeatherDescription = when (code) {
        0 -> WeatherDescription("Clear sky", WeatherCondition.CLEAR)
        1 -> WeatherDescription("Mainly clear", WeatherCondition.CLEAR)
        2 -> WeatherDescription("Partly cloudy", WeatherCondition.CLOUDY)
        3 -> WeatherDescription("Overcast", WeatherCondition.CLOUDY)
        45, 48 -> WeatherDescription("Fog", WeatherCondition.FOG)
        51, 53, 55 -> WeatherDescription("Drizzle", WeatherCondition.RAIN)
        56, 57 -> WeatherDescription("Freezing drizzle", WeatherCondition.RAIN)
        61, 63, 65 -> WeatherDescription("Rain", WeatherCondition.RAIN)
        66, 67 -> WeatherDescription("Freezing rain", WeatherCondition.RAIN)
        71, 73, 75, 77 -> WeatherDescription("Snow", WeatherCondition.SNOW)
        80, 81, 82 -> WeatherDescription("Rain showers", WeatherCondition.RAIN)
        85, 86 -> WeatherDescription("Snow showers", WeatherCondition.SNOW)
        in 95..99 -> WeatherDescription("Thunderstorm", WeatherCondition.THUNDER)
        else -> WeatherDescription("Unknown", WeatherCondition.CLOUDY)
    }
}
