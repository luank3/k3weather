package com.k3weather.app.core.model

import com.k3weather.app.R

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

enum class AppColorScheme(
    val id: String,
    val nameResId: Int,
    val primaryAccent: Int,
    val secondaryAccent: Int,
    val gradientStart: Int,
    val gradientEnd: Int
) {
    CYAN(
        id = "cyan",
        nameResId = R.string.color_scheme_cyan,
        primaryAccent = 0xFF00E5FF.toInt(),
        secondaryAccent = 0xFF18FFFF.toInt(),
        gradientStart = 0xFF00E5FF.toInt(),
        gradientEnd = 0xFFFACC15.toInt()
    ),
    EMERALD(
        id = "emerald",
        nameResId = R.string.color_scheme_emerald,
        primaryAccent = 0xFF00E676.toInt(),
        secondaryAccent = 0xFF69F0AE.toInt(),
        gradientStart = 0xFF00E676.toInt(),
        gradientEnd = 0xFFEEFF41.toInt()
    ),
    SUNSET(
        id = "sunset",
        nameResId = R.string.color_scheme_sunset,
        primaryAccent = 0xFFFF9100.toInt(),
        secondaryAccent = 0xFFFFB74D.toInt(),
        gradientStart = 0xFFFF9100.toInt(),
        gradientEnd = 0xFFFFD54F.toInt()
    ),
    PURPLE(
        id = "purple",
        nameResId = R.string.color_scheme_purple,
        primaryAccent = 0xFFB388FF.toInt(),
        secondaryAccent = 0xFFE040FB.toInt(),
        gradientStart = 0xFFB388FF.toInt(),
        gradientEnd = 0xFFFF80AB.toInt()
    ),
    SKY(
        id = "sky",
        nameResId = R.string.color_scheme_sky,
        primaryAccent = 0xFF40C4FF.toInt(),
        secondaryAccent = 0xFF80D8FF.toInt(),
        gradientStart = 0xFF40C4FF.toInt(),
        gradientEnd = 0xFF80D8FF.toInt()
    );

    companion object {
        fun fromId(id: String): AppColorScheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CYAN
    }
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
    val colorScheme: AppColorScheme = AppColorScheme.CYAN,
    val language: String = "system",
    val customApiBaseUrl: String? = null
)
