package com.k3weather.app.core.model

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F");

    fun fromCelsius(tempC: Double): Double = when (this) {
        CELSIUS -> tempC
        FAHRENHEIT -> (tempC * 9.0 / 5.0) + 32.0
    }

    fun format(tempC: Double): String = "${Math.round(fromCelsius(tempC))}°"
}

enum class WindSpeedUnit(val symbol: String) {
    KMH("km/h"),
    MPH("mph");

    fun fromKmh(kmh: Double): Double = when (this) {
        KMH -> kmh
        MPH -> kmh * 0.621371
    }

    fun format(kmh: Double): String = "${Math.round(fromKmh(kmh))} $symbol"
}

data class CurrentWeather(
    val temperatureC: Double,
    val weatherCode: Int,
    val condition: String,
    val isDay: Boolean,
    val windSpeedKmh: Double,
    val minTempC: Double,
    val maxTempC: Double,
    val timeIso: String
)

data class HourlyForecast(
    val timeEpochSeconds: Long,
    val timeLabel: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean
)

data class DailyForecast(
    val dateIso: String,
    val dayLabel: String,
    val weatherCode: Int,
    val minTempC: Double,
    val maxTempC: Double
)

data class ForecastData(
    val location: LocationItem,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val fetchedAtEpochMs: Long,
    val isStale: Boolean = false
)

data class AppSettings(
    val tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KMH,
    val weatherBackgroundsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val useCurrentLocation: Boolean = false,
    val customApiBaseUrl: String? = null
)
