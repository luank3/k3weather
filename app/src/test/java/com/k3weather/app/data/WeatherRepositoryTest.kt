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

    @Test fun parsesForecastJsonAndSelectsFiveHoursAndThreeDays() {
        val hourlyTimes = (0..8).joinToString(",") { "\"2024-01-01T${String.format("%02d", it)}:00\"" }
        val temperatures = (0..8).joinToString(",") { (20 + it).toString() }
        val json = JSONObject("""{"timezone":"Asia/Ho_Chi_Minh","current":{"time":"2024-01-01T02:00","temperature_2m":24.5,"weather_code":2,"is_day":1,"wind_speed_10m":12},"hourly":{"time":[$hourlyTimes],"temperature_2m":[$temperatures],"weather_code":[0,1,2,3,45,61,71,95,0],"is_day":[1,1,1,1,1,1,1,1,1]},"daily":{"time":["2024-01-01","2024-01-02","2024-01-03"],"weather_code":[2,3,61],"temperature_2m_min":[18,19,17],"temperature_2m_max":[28,29,27]}}""")
        val parsed = ForecastJsonParser.parse(LocationItem("x", "Test", latitude = 0.0, longitude = 0.0), json, 100L)
        assertEquals(24.5, parsed.current.temperatureC, 0.0)
        assertEquals("Partly cloudy", parsed.current.condition)
        assertEquals(5, parsed.hourly.size)
        assertEquals(3, parsed.daily.size)
        assertEquals(18.0, parsed.current.minTempC, 0.0)
        assertEquals(28.0, parsed.current.maxTempC, 0.0)
    }

    @Test fun convertsDisplayUnitsFromStoredMetricValues() {
        assertEquals(77.0, TemperatureUnit.FAHRENHEIT.fromCelsius(25.0), 0.0001)
        assertEquals(6.21371, WindSpeedUnit.MPH.fromKmh(10.0), 0.0001)
    }
}
