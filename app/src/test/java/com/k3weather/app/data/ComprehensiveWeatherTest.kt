package com.k3weather.app.data

import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.core.model.TemperatureUnit
import com.k3weather.app.core.model.WindSpeedUnit
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.mapper.WeatherCondition
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ComprehensiveWeatherTest {
    @Test
    fun mapsEveryWmoCodeAndUsesUnknownFallbackOutsideRange() {
        val expected = mapOf(
            0 to ("Clear sky" to WeatherCondition.CLEAR),
            1 to ("Mainly clear" to WeatherCondition.CLEAR),
            2 to ("Partly cloudy" to WeatherCondition.PARTLY_CLOUDY),
            3 to ("Overcast" to WeatherCondition.CLOUDY),
            45 to ("Fog" to WeatherCondition.FOG), 48 to ("Fog" to WeatherCondition.FOG),
            51 to ("Drizzle" to WeatherCondition.DRIZZLE), 53 to ("Drizzle" to WeatherCondition.DRIZZLE),
            55 to ("Dense drizzle" to WeatherCondition.DRIZZLE),
            56 to ("Freezing drizzle" to WeatherCondition.SLEET),
            57 to ("Freezing drizzle" to WeatherCondition.SLEET),
            61 to ("Rain" to WeatherCondition.RAIN),
            63 to ("Rain" to WeatherCondition.RAIN),
            65 to ("Heavy rain" to WeatherCondition.HEAVY_RAIN),
            66 to ("Freezing rain" to WeatherCondition.SLEET),
            67 to ("Freezing rain" to WeatherCondition.SLEET),
            71 to ("Snow" to WeatherCondition.SNOW), 73 to ("Snow" to WeatherCondition.SNOW),
            75 to ("Heavy snow" to WeatherCondition.SNOW), 77 to ("Heavy snow" to WeatherCondition.SNOW),
            80 to ("Rain showers" to WeatherCondition.RAIN), 81 to ("Rain showers" to WeatherCondition.RAIN),
            82 to ("Violent rain showers" to WeatherCondition.HEAVY_RAIN),
            85 to ("Snow showers" to WeatherCondition.SNOW),
            86 to ("Snow showers" to WeatherCondition.SNOW),
            95 to ("Thunderstorm" to WeatherCondition.THUNDER),
            96 to ("Thunderstorm with hail" to WeatherCondition.THUNDER_HAIL),
            99 to ("Thunderstorm with hail" to WeatherCondition.THUNDER_HAIL)
        )
        for (code in 0..99) {
            val mapped = WeatherCodeMapper.map(code)
            val known = expected[code]
            if (known == null) {
                assertEquals("code $code", "Unknown", mapped.label)
                assertEquals("code $code", WeatherCondition.CLOUDY, mapped.condition)
            } else {
                assertEquals("label for $code", known.first, mapped.label)
                assertEquals("condition for $code", known.second, mapped.condition)
            }
        }
        listOf(-1, 100, Int.MIN_VALUE, Int.MAX_VALUE).forEach { code ->
            assertEquals("Unknown", WeatherCodeMapper.map(code).label)
            assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(code).condition)
        }
    }

    @Test
    fun temperatureConversionRoundTripsAndFormatsPositiveAndNegativeValues() {
        listOf(-273.15, -40.0, -1.5, 0.0, 25.0, 100.0).forEach { celsius ->
            val fahrenheit = TemperatureUnit.FAHRENHEIT.fromCelsius(celsius)
            val roundTrip = (fahrenheit - 32.0) * 5.0 / 9.0
            assertEquals("round trip for $celsius°C", celsius, roundTrip, 1e-9)
        }
        assertEquals(-4.0, TemperatureUnit.FAHRENHEIT.fromCelsius(-20.0), 1e-9)
        assertEquals("-4°", TemperatureUnit.FAHRENHEIT.format(-20.0))
        assertEquals("-1°", TemperatureUnit.CELSIUS.format(-1.5))
        assertEquals("25°", TemperatureUnit.CELSIUS.format(25.0))
        assertEquals("77°", TemperatureUnit.FAHRENHEIT.format(25.0))
    }

    @Test
    fun windConversionRoundTripsAndFormattingUsesSelectedUnit() {
        listOf(0.0, 1.0, 10.0, 100.0).forEach { kmh ->
            val mph = WindSpeedUnit.MPH.fromKmh(kmh)
            assertEquals("round trip for $kmh km/h", kmh, mph / 0.621371, 1e-9)
        }
        assertEquals(-6.21371, WindSpeedUnit.MPH.fromKmh(-10.0), 1e-9)
        assertEquals("6 mph", WindSpeedUnit.MPH.format(10.0))
        assertEquals("10 km/h", WindSpeedUnit.KMH.format(10.0))
    }

    @Test
    fun parsesFullForecastAndSelectsCurrentFiveHoursAndThreeDays() {
        val json = forecastJson(
            timezone = "Asia/Ho_Chi_Minh",
            now = "2024-01-01T02:30",
            hourlyTimes = (0..8).map { "2024-01-01T${it.toString().padStart(2, '0')}:00" },
            dailyTimes = listOf("2024-01-01", "2024-01-02", "2024-01-03")
        )
        val parsed = ForecastJsonParser.parse(location("Asia/Ho_Chi_Minh"), json, 1234L)

        assertEquals(24.5, parsed.current.temperatureC, 0.0)
        assertEquals(2, parsed.current.weatherCode)
        assertEquals("Partly cloudy", parsed.current.condition)
        assertTrue(parsed.current.isDay)
        assertEquals(12.0, parsed.current.windSpeedKmh, 0.0)
        assertEquals(18.0, parsed.current.minTempC, 0.0)
        assertEquals(28.0, parsed.current.maxTempC, 0.0)
        assertEquals("2024-01-01T02:30", parsed.current.timeIso)
        assertEquals(5, parsed.hourly.size)
        assertEquals("3AM", parsed.hourly.first().timeLabel)
        assertEquals(23.0, parsed.hourly.first().temperatureC, 0.0)
        assertEquals(3, parsed.daily.size)
        assertEquals("2024-01-01", parsed.daily.first().dateIso)
        assertEquals(18.0, parsed.daily.first().minTempC, 0.0)
        assertEquals(28.0, parsed.daily.first().maxTempC, 0.0)
        assertEquals(1234L, parsed.fetchedAtEpochMs)
    }

    @Test
    fun hourlyEpochsUseForecastTimezoneAndSurviveMidnightTransition() {
        val json = forecastJson(
            timezone = "America/Los_Angeles",
            now = "2024-01-01T23:30",
            hourlyTimes = listOf("2024-01-01T23:00", "2024-01-02T00:00", "2024-01-02T01:00", "2024-01-02T02:00", "2024-01-02T03:00", "2024-01-02T04:00"),
            dailyTimes = listOf("2024-01-01", "2024-01-02", "2024-01-03")
        )
        val parsed = ForecastJsonParser.parse(location("UTC"), json, 0L)

        assertEquals(5, parsed.hourly.size)
        assertEquals("12AM", parsed.hourly.first().timeLabel)
        assertEquals("1AM", parsed.hourly[1].timeLabel)
        assertEquals(
            Instant.parse("2024-01-02T08:00:00Z").epochSecond,
            parsed.hourly.first().timeEpochSeconds
        )
        assertEquals("2AM", parsed.hourly[2].timeLabel)
        assertEquals(18.0, parsed.current.minTempC, 0.0)
    }

    @Test
    fun hourlySelectionIncludesExactCurrentHourAndFallsBackWhenNoTimeCanBeParsed() {
        val exact = ForecastJsonParser.parse(
            location("UTC"),
            forecastJson("UTC", "2024-01-01T02:00", (0..6).map { "2024-01-01T${it.toString().padStart(2, '0')}:00" }, listOf("2024-01-01", "2024-01-02", "2024-01-03")),
            1L
        )
        assertEquals("2AM", exact.hourly.first().timeLabel)

        val invalidNow = JSONObject(forecastJson("UTC", "not-a-time", (0..4).map { "2024-01-01T${it.toString().padStart(2, '0')}:00" }, listOf("2024-01-01", "2024-01-02", "2024-01-03")).toString())
        val fallback = ForecastJsonParser.parse(location("UTC"), invalidNow, 2L)
        assertEquals(5, fallback.hourly.size)
        assertFalse(fallback.current.timeIso.isBlank())
    }

    @Test
    fun normalizesVietnameseQueryIncludingDAndCombiningAccentsForFallbackSearch() {
        assertEquals("Da Lat", normalizeSearchQuery("Đà Lạt"))
        assertEquals("da nang", normalizeSearchQuery("đà nẵng").lowercase())
        assertEquals("ha noi", normalizeSearchQuery("Hà Nội").lowercase())
        assertEquals("Da", normalizeSearchQuery("Đa"))
        assertEquals("Da Lat", normalizeSearchQuery("Đa\u0300 La\u0323t"))
    }

    @Test
    fun colorSchemeResolutionAndFallbacks() {
        assertEquals(com.k3weather.app.core.model.AppColorScheme.CYAN, com.k3weather.app.core.model.AppColorScheme.fromId("cyan"))
        assertEquals(com.k3weather.app.core.model.AppColorScheme.EMERALD, com.k3weather.app.core.model.AppColorScheme.fromId("emerald"))
        assertEquals(com.k3weather.app.core.model.AppColorScheme.SUNSET, com.k3weather.app.core.model.AppColorScheme.fromId("sunset"))
        assertEquals(com.k3weather.app.core.model.AppColorScheme.PURPLE, com.k3weather.app.core.model.AppColorScheme.fromId("purple"))
        assertEquals(com.k3weather.app.core.model.AppColorScheme.SKY, com.k3weather.app.core.model.AppColorScheme.fromId("sky"))
        assertEquals(com.k3weather.app.core.model.AppColorScheme.CYAN, com.k3weather.app.core.model.AppColorScheme.fromId("nonexistent"))
    }

    private fun location(timezone: String) = LocationItem("test", "Test", latitude = 0.0, longitude = 0.0, timezone = timezone)

    private fun forecastJson(timezone: String, now: String, hourlyTimes: List<String>, dailyTimes: List<String>): JSONObject {
        val hourlyTemps = hourlyTimes.indices.map { 20.0 + it }
        val hourlyCodes = hourlyTimes.indices.map { it % 100 }
        val hourlyIsDay = hourlyTimes.indices.map { if (it in 6..17) 1 else 0 }
        return JSONObject().put("timezone", timezone)
            .put("current", JSONObject().put("time", now).put("temperature_2m", 24.5).put("weather_code", 2).put("is_day", 1).put("wind_speed_10m", 12.0))
            .put("hourly", JSONObject().put("time", org.json.JSONArray(hourlyTimes)).put("temperature_2m", org.json.JSONArray(hourlyTemps)).put("weather_code", org.json.JSONArray(hourlyCodes)).put("is_day", org.json.JSONArray(hourlyIsDay)))
            .put("daily", JSONObject().put("time", org.json.JSONArray(dailyTimes)).put("weather_code", org.json.JSONArray(listOf(2, 3, 61).take(dailyTimes.size)))
                .put("temperature_2m_min", org.json.JSONArray(dailyTimes.indices.map { 18.0 + it })).put("temperature_2m_max", org.json.JSONArray(dailyTimes.indices.map { 28.0 + it })))
    }

}
