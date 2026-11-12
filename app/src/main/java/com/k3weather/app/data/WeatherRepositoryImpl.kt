package com.k3weather.app.data

import com.k3weather.app.core.model.*
import com.k3weather.app.core.repository.WeatherRepository
import com.k3weather.app.data.local.WeatherPreferences
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.remote.OpenMeteoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherRepositoryImpl(private val preferences: WeatherPreferences, private val clientFactory: (String?) -> OpenMeteoClient = { OpenMeteoClient(it) }) : WeatherRepository {
    override suspend fun getForecast(location: LocationItem, forceRefresh: Boolean): Result<ForecastData> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = preferences.getSettings()
            val json = clientFactory(settings.customApiBaseUrl).fetchForecast(location.latitude, location.longitude)
            val forecast = ForecastJsonParser.parse(location, json, System.currentTimeMillis())
            preferences.saveCachedForecast(location.id, JSONObject().put("timestamp", forecast.fetchedAtEpochMs).put("data", json))
            forecast
        }.recoverCatching { error ->
            val cached = getCachedForecast(location.id) ?: throw error
            cached.copy(isStale = true)
        }
    }

    override suspend fun getCachedForecast(locationId: String): ForecastData? = withContext(Dispatchers.IO) {
        val wrapper = preferences.getCachedForecast(locationId) ?: return@withContext null
        val loc = (preferences.getSavedLocations() + preferences.getSelectedLocation()).firstOrNull { it.id == locationId } ?: return@withContext null
        runCatching { ForecastJsonParser.parse(loc, wrapper.getJSONObject("data"), wrapper.optLong("timestamp")).copy(isStale = true) }.getOrNull()
    }

    override suspend fun searchLocations(query: String): Result<List<LocationItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = preferences.getSettings()
            val client = clientFactory(settings.customApiBaseUrl)
            val q = query.trim()
            if (q.isEmpty()) return@runCatching emptyList()
            val first = client.search(q)
            var results = parseLocations(first)
            if (results.isEmpty()) {
                val normalized = normalizeSearchQuery(q)
                if (normalized != q) results = parseLocations(client.search(normalized))
            }
            results
        }
    }

    private fun parseLocations(json: JSONObject): List<LocationItem> {
        val array = json.optJSONArray("results") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i -> runCatching {
            val o = array.getJSONObject(i)
            LocationItem(o.optString("id", "${o.optDouble("latitude")},${o.optDouble("longitude")}"), o.getString("name"), o.optString("admin1"), o.optString("country"),
                o.getDouble("latitude"), o.getDouble("longitude"), o.optString("timezone", "Asia/Ho_Chi_Minh"))
        }.getOrNull() }
    }

    override suspend fun getSavedLocations() = preferences.getSavedLocations()
    override suspend fun saveLocation(location: LocationItem) = preferences.addLocation(location)
    override suspend fun removeLocation(locationId: String) = preferences.removeLocation(locationId)
    override suspend fun getSelectedLocation() = preferences.getSelectedLocation()
    override suspend fun setSelectedLocation(location: LocationItem) = preferences.setSelectedLocation(location)
    override suspend fun hasSelectedLocation(): Boolean = preferences.hasSelectedLocation()
    override suspend fun getSettings() = preferences.getSettings()
    override suspend fun updateSettings(settings: AppSettings) = preferences.setSettings(settings)
}

internal object ForecastJsonParser {
    fun parse(location: LocationItem, json: JSONObject, fetchedAt: Long): ForecastData {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val currentCode = current.getInt("weather_code")
        val nowText = current.getString("time")
        val zone = runCatching { ZoneId.of(json.optString("timezone", location.timezone)) }.getOrDefault(ZoneId.of("UTC"))
        val now = runCatching { LocalDateTime.parse(nowText) }.getOrElse { LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0) }
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val start = (0 until times.length()).firstOrNull { runCatching { !LocalDateTime.parse(times.getString(it)).isBefore(now) }.getOrDefault(false) } ?: 0
        val hours = (start until minOf(times.length(), start + 5)).map { i ->
            val local = LocalDateTime.parse(times.getString(i))
            HourlyForecast(local.atZone(zone).toEpochSecond(), local.format(DateTimeFormatter.ofPattern("ha")), hourly.getJSONArray("temperature_2m").getDouble(i),
                hourly.getJSONArray("weather_code").getInt(i), hourly.getJSONArray("is_day").optInt(i, 1) == 1)
        }
        val days = (0 until daily.getJSONArray("time").length()).map { i ->
            val date = daily.getJSONArray("time").getString(i)
            DailyForecast(date, java.time.LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE")), daily.getJSONArray("weather_code").getInt(i),
                daily.getJSONArray("temperature_2m_min").getDouble(i), daily.getJSONArray("temperature_2m_max").getDouble(i))
        }
        val todayIndex = (0 until daily.getJSONArray("time").length()).firstOrNull { daily.getJSONArray("time").getString(it) == now.toLocalDate().toString() } ?: 0
        return ForecastData(location, CurrentWeather(current.getDouble("temperature_2m"), currentCode, WeatherCodeMapper.map(currentCode).label,
            current.optInt("is_day", 1) == 1, current.getDouble("wind_speed_10m"), daily.getJSONArray("temperature_2m_min").getDouble(todayIndex),
            daily.getJSONArray("temperature_2m_max").getDouble(todayIndex), nowText), hours, days, fetchedAt)
    }
}

internal fun normalizeSearchQuery(value: String): String = Normalizer.normalize(value.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "").trim()
