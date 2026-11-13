package com.k3weather.app.core.model

import com.k3weather.app.R
import kotlin.math.ln
import kotlin.math.roundToInt

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
    MS("m/s"),
    MPH("mph");

    fun fromKmh(kmh: Double): Double = when (this) {
        KMH -> kmh
        MS -> kmh / 3.6
        MPH -> kmh * 0.621371
    }

    fun format(kmh: Double): String = when (this) {
        MS -> {
            val v = fromKmh(kmh)
            if (v < 10.0) {
                val rounded10 = (v * 10).roundToInt()
                if (rounded10 % 10 == 0) "${rounded10 / 10} $symbol"
                else String.format(java.util.Locale.US, "%.1f %s", v, symbol)
            } else {
                "${v.roundToInt()} $symbol"
            }
        }
        else -> "${fromKmh(kmh).roundToInt()} $symbol"
    }
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
        primaryAccent = 0xFF38BDF8.toInt(),
        secondaryAccent = 0xFF7DD3FC.toInt(),
        gradientStart = 0xFF38BDF8.toInt(),
        gradientEnd = 0xFFFBBF24.toInt()
    ),
    EMERALD(
        id = "emerald",
        nameResId = R.string.color_scheme_emerald,
        primaryAccent = 0xFF34D399.toInt(),
        secondaryAccent = 0xFF6EE7B7.toInt(),
        gradientStart = 0xFF34D399.toInt(),
        gradientEnd = 0xFFFDE047.toInt()
    ),
    SUNSET(
        id = "sunset",
        nameResId = R.string.color_scheme_sunset,
        primaryAccent = 0xFFFB923C.toInt(),
        secondaryAccent = 0xFFFDBA74.toInt(),
        gradientStart = 0xFFFB923C.toInt(),
        gradientEnd = 0xFFFDE047.toInt()
    ),
    PURPLE(
        id = "purple",
        nameResId = R.string.color_scheme_purple,
        primaryAccent = 0xFFC084FC.toInt(),
        secondaryAccent = 0xFFE879F9.toInt(),
        gradientStart = 0xFFC084FC.toInt(),
        gradientEnd = 0xFFF472B6.toInt()
    ),
    SKY(
        id = "sky",
        nameResId = R.string.color_scheme_sky,
        primaryAccent = 0xFF60A5FA.toInt(),
        secondaryAccent = 0xFF93C5FD.toInt(),
        gradientStart = 0xFF60A5FA.toInt(),
        gradientEnd = 0xFF38BDF8.toInt()
    );

    companion object {
        fun fromId(id: String): AppColorScheme =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CYAN
    }
}

data class CurrentWeather(
    val temperatureC: Double,
    val apparentTemperatureC: Double = temperatureC,
    val weatherCode: Int,
    val condition: String,
    val isDay: Boolean,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int = 0,
    val windGustsKmh: Double = windSpeedKmh,
    val humidityPercent: Int = 65,
    val uvIndex: Double = 0.0,
    val surfacePressureHpa: Double = 1013.0,
    val visibilityKm: Double = 10.0,
    val precipitationMm: Double = 0.0,
    val minTempC: Double,
    val maxTempC: Double,
    val sunriseIso: String = "",
    val sunsetIso: String = "",
    val timeIso: String
) {
    /** Approximate dew point in Celsius using Magnus formula */
    val dewPointC: Double
        get() {
            val rh = humidityPercent.coerceIn(1, 100) / 100.0
            val a = 17.27
            val b = 237.7
            val alpha = ((a * temperatureC) / (b + temperatureC)) + ln(rh)
            return (b * alpha) / (a - alpha)
        }

    val uvIndexInt: Int
        get() = uvIndex.roundToInt().coerceAtLeast(0)
}

data class HourlyForecast(
    val timeEpochSeconds: Long,
    val timeLabel: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val precipitationProbability: Int = 0
)

data class DailyForecast(
    val dateIso: String,
    val dayLabel: String,
    val weatherCode: Int,
    val minTempC: Double,
    val maxTempC: Double,
    val precipitationProbabilityMax: Int = 0,
    val uvIndexMax: Double = 0.0,
    val sunriseIso: String = "",
    val sunsetIso: String = ""
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
    val useCurrentLocation: Boolean = true,
    val colorScheme: AppColorScheme = AppColorScheme.CYAN,
    val language: String = "system",
    val customApiBaseUrl: String? = null
)
