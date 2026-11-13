package com.k3weather.app.data

import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.core.model.TemperatureUnit
import com.k3weather.app.core.model.WindSpeedUnit
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.mapper.WeatherCondition
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherRepositoryTest {
    @Test fun mapsWmoCodesToLabelsAndConditions() {
        assertEquals("Clear sky", WeatherCodeMapper.map(0).label)
        assertEquals(WeatherCondition.CLEAR, WeatherCodeMapper.map(1).condition)
        assertEquals(WeatherCondition.CLOUDY, WeatherCodeMapper.map(3).condition)
        assertEquals(WeatherCondition.FOG, WeatherCodeMapper.map(45).condition)
        assertEquals(WeatherCondition.RAIN, WeatherCodeMapper.map(63).condition)
        assertEquals(WeatherCondition.SNOW, WeatherCodeMapper.map(73).condition)
        assertEquals(WeatherCondition.THUNDER, WeatherCodeMapper.map(95).condition)
    }

    @Test fun parsesExpandedForecastJsonWithHourlyWeeklyAndRichMetrics() {
        val hourlyTimes = (0..26).joinToString(",") {
            val day = 1 + (it / 24)
            val hr = it % 24
            "\"2024-01-0${day}T${String.format("%02d", hr)}:00\""
        }
        val temperatures = (0..26).joinToString(",") { (20 + (it % 10)).toString() }
        val codes = (0..26).joinToString(",") { (it % 4).toString() }
        val isDays = (0..26).joinToString(",") { "1" }
        val pops = (0..26).joinToString(",") { (it * 3).toString() }
        val json = JSONObject(
            """
            {
              "timezone":"Asia/Ho_Chi_Minh",
              "current":{
                "time":"2024-01-01T02:00",
                "temperature_2m":24.5,
                "apparent_temperature":27.0,
                "relative_humidity_2m":78,
                "weather_code":2,
                "is_day":1,
                "wind_speed_10m":12.0,
                "wind_direction_10m":135,
                "wind_gusts_10m":22.5,
                "surface_pressure":1014.2,
                "precipitation":0.4,
                "uv_index":6.2
              },
              "hourly":{
                "time":[$hourlyTimes],
                "temperature_2m":[$temperatures],
                "weather_code":[$codes],
                "is_day":[$isDays],
                "precipitation_probability":[$pops],
                "visibility":[${(0..26).joinToString(",") { "24000" }}]
              },
              "daily":{
                "time":["2024-01-01","2024-01-02","2024-01-03","2024-01-04","2024-01-05","2024-01-06","2024-01-07"],
                "weather_code":[2,3,61,1,0,80,95],
                "temperature_2m_min":[18,19,17,18,20,19,17],
                "temperature_2m_max":[28,29,27,30,31,28,26],
                "precipitation_probability_max":[10,25,80,5,0,65,90],
                "uv_index_max":[6.2,5.0,3.1,7.5,8.0,4.5,2.5],
                "sunrise":["2024-01-01T06:05","2024-01-02T06:05","2024-01-03T06:06","2024-01-04T06:06","2024-01-05T06:06","2024-01-06T06:07","2024-01-07T06:07"],
                "sunset":["2024-01-01T17:25","2024-01-02T17:26","2024-01-03T17:26","2024-01-04T17:27","2024-01-05T17:27","2024-01-06T17:28","2024-01-07T17:28"]
              }
            }
            """.trimIndent()
        )
        val parsed = ForecastJsonParser.parse(LocationItem("x", "Test", latitude = 0.0, longitude = 0.0), json, 100L)
        assertEquals(24.5, parsed.current.temperatureC, 0.0)
        assertEquals(27.0, parsed.current.apparentTemperatureC, 0.0)
        assertEquals(78, parsed.current.humidityPercent)
        assertEquals(135, parsed.current.windDirectionDeg)
        assertEquals(22.5, parsed.current.windGustsKmh, 0.0)
        assertEquals(1014.2, parsed.current.surfacePressureHpa, 0.0)
        assertEquals(24.0, parsed.current.visibilityKm, 0.01)
        assertEquals(6, parsed.current.uvIndexInt)
        assertEquals("2024-01-01T06:05", parsed.current.sunriseIso)
        assertEquals("2024-01-01T17:25", parsed.current.sunsetIso)
        assertEquals("Partly cloudy", parsed.current.condition)
        assertEquals(24, parsed.hourly.size)
        assertEquals(7, parsed.daily.size)
        assertEquals(80, parsed.daily[2].precipitationProbabilityMax)
        assertEquals(18.0, parsed.current.minTempC, 0.0)
        assertEquals(28.0, parsed.current.maxTempC, 0.0)
    }

    @Test fun convertsDisplayUnitsFromStoredMetricValues() {
        assertEquals(77.0, TemperatureUnit.FAHRENHEIT.fromCelsius(25.0), 0.0001)
        assertEquals(6.21371, WindSpeedUnit.MPH.fromKmh(10.0), 0.0001)
    }
}
