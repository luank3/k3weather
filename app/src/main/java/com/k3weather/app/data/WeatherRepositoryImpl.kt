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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class WeatherRepositoryImpl(
    private val preferences: WeatherPreferences,
    private val clientFactory: (String?) -> OpenMeteoClient = { OpenMeteoClient(it) }
) : WeatherRepository {

    override suspend fun getForecast(location: LocationItem, forceRefresh: Boolean): Result<ForecastData> = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = getCachedForecast(location.id)
            if (cached != null &&
                (nowMs - cached.fetchedAtEpochMs) < CACHE_TTL_MS &&
                cached.daily.size >= 7 &&
                cached.hourly.size >= 12
            ) {
                return@withContext Result.success(cached.copy(isStale = false))
            }
        }

        runCatching {
            val settings = preferences.getSettings()
            val json = clientFactory(settings.customApiBaseUrl).fetchForecast(location.latitude, location.longitude)
            val resolvedTz = json.optString("timezone", location.timezone).ifBlank { location.timezone }
            val updatedLocation = if (location.timezone == "auto" || location.timezone.isBlank()) {
                location.copy(timezone = resolvedTz)
            } else {
                location
            }
            val fetchedAt = System.currentTimeMillis()
            val forecast = ForecastJsonParser.parse(updatedLocation, json, fetchedAt)
            preferences.saveCachedForecast(updatedLocation, fetchedAt, json)
            forecast
        }.recoverCatching { error ->
            val cached = getCachedForecast(location.id) ?: throw error
            cached.copy(isStale = true)
        }
    }

    override suspend fun getCachedForecast(locationId: String): ForecastData? = withContext(Dispatchers.IO) {
        val wrapper = preferences.getCachedForecast(locationId) ?: return@withContext null
        val loc = preferences.parseLocationFromJson(wrapper.optJSONObject("location"))
            ?: (preferences.getSavedLocations() + listOfNotNull(preferences.getDetectedCurrentLocation(), preferences.getSelectedLocation()))
                .firstOrNull { it.id == locationId }
            ?: return@withContext null
        runCatching {
            val timestamp = wrapper.optLong("timestamp")
            val isOld = (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS
            ForecastJsonParser.parse(loc, wrapper.getJSONObject("data"), timestamp).copy(isStale = isOld)
        }.getOrNull()
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
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                LocationItem(
                    id = o.optString("id", "${o.optDouble("latitude")},${o.optDouble("longitude")}"),
                    name = o.getString("name"),
                    region = o.optString("admin1"),
                    country = o.optString("country"),
                    latitude = o.getDouble("latitude"),
                    longitude = o.getDouble("longitude"),
                    timezone = o.optString("timezone", "Asia/Ho_Chi_Minh")
                )
            }.getOrNull()
        }
    }

    override suspend fun getSavedLocations() = preferences.getSavedLocations()
    override suspend fun saveLocation(location: LocationItem) = preferences.addLocation(location)
    override suspend fun removeLocation(locationId: String) = preferences.removeLocation(locationId)
    override suspend fun getSelectedLocation() = preferences.getSelectedLocation()
    override suspend fun setSelectedLocation(location: LocationItem) = preferences.setSelectedLocation(location)
    override suspend fun hasSelectedLocation(): Boolean = preferences.hasSelectedLocation()
    override suspend fun getDetectedCurrentLocation(): LocationItem? = preferences.getDetectedCurrentLocation()
    override suspend fun setDetectedCurrentLocation(location: LocationItem) = preferences.setDetectedCurrentLocation(location)
    override suspend fun getSettings() = preferences.getSettings()
    override suspend fun updateSettings(settings: AppSettings) = preferences.setSettings(settings)

    companion object {
        private const val CACHE_TTL_MS = 15 * 60 * 1000L // 15 minutes
    }
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
        val hourlyTemps = hourly.getJSONArray("temperature_2m")
        val hourlyCodes = hourly.getJSONArray("weather_code")
        val hourlyIsDay = hourly.optJSONArray("is_day")
        val hourlyPop = hourly.optJSONArray("precipitation_probability")
        val hourlyVis = hourly.optJSONArray("visibility")

        val start = (0 until times.length()).firstOrNull {
            runCatching { !LocalDateTime.parse(times.getString(it)).isBefore(now) }.getOrDefault(false)
        } ?: 0

        val hours = (start until minOf(times.length(), start + 24)).map { i ->
            val local = LocalDateTime.parse(times.getString(i))
            HourlyForecast(
                timeEpochSeconds = local.atZone(zone).toEpochSecond(),
                timeLabel = local.format(DateTimeFormatter.ofPattern("ha")),
                temperatureC = hourlyTemps.getDouble(i),
                weatherCode = hourlyCodes.getInt(i),
                isDay = hourlyIsDay?.optInt(i, 1) != 0,
                precipitationProbability = hourlyPop?.optInt(i, 0) ?: 0
            )
        }

        val dailyTimes = daily.getJSONArray("time")
        val dailyCodes = daily.getJSONArray("weather_code")
        val dailyMin = daily.getJSONArray("temperature_2m_min")
        val dailyMax = daily.getJSONArray("temperature_2m_max")
        val dailyPopMax = daily.optJSONArray("precipitation_probability_max")
        val dailyUvMax = daily.optJSONArray("uv_index_max")
        val dailySunrise = daily.optJSONArray("sunrise")
        val dailySunset = daily.optJSONArray("sunset")

        val days = (0 until dailyTimes.length()).map { i ->
            val date = dailyTimes.getString(i)
            DailyForecast(
                dateIso = date,
                dayLabel = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("EEE")),
                weatherCode = dailyCodes.getInt(i),
                minTempC = dailyMin.getDouble(i),
                maxTempC = dailyMax.getDouble(i),
                precipitationProbabilityMax = dailyPopMax?.optInt(i, 0) ?: 0,
                uvIndexMax = dailyUvMax?.optDouble(i, 0.0) ?: 0.0,
                sunriseIso = dailySunrise?.optString(i, "") ?: "",
                sunsetIso = dailySunset?.optString(i, "") ?: ""
            )
        }

        val todayIndex = (0 until dailyTimes.length()).firstOrNull { dailyTimes.getString(it) == now.toLocalDate().toString() } ?: 0
        val tempC = current.getDouble("temperature_2m")
        val windSpeed = current.getDouble("wind_speed_10m")
        val visMeters = hourlyVis?.optDouble(start, 10000.0) ?: 10000.0
        val currentUv = current.optDouble("uv_index", Double.NaN).let {
            if (it.isNaN()) (dailyUvMax?.optDouble(todayIndex, 0.0) ?: 0.0) else it
        }

        return ForecastData(
            location = location,
            current = CurrentWeather(
                temperatureC = tempC,
                apparentTemperatureC = current.optDouble("apparent_temperature", tempC),
                weatherCode = currentCode,
                condition = WeatherCodeMapper.map(currentCode).label,
                isDay = current.optInt("is_day", 1) == 1,
                windSpeedKmh = windSpeed,
                windDirectionDeg = current.optInt("wind_direction_10m", 0),
                windGustsKmh = current.optDouble("wind_gusts_10m", windSpeed),
                humidityPercent = current.optInt("relative_humidity_2m", 65),
                uvIndex = currentUv,
                surfacePressureHpa = current.optDouble("surface_pressure", 1013.0),
                visibilityKm = (visMeters / 1000.0).coerceIn(0.1, 50.0),
                precipitationMm = current.optDouble("precipitation", 0.0),
                minTempC = dailyMin.getDouble(todayIndex),
                maxTempC = dailyMax.getDouble(todayIndex),
                sunriseIso = dailySunrise?.optString(todayIndex, "") ?: "",
                sunsetIso = dailySunset?.optString(todayIndex, "") ?: "",
                timeIso = nowText
            ),
            hourly = hours,
            daily = days,
            fetchedAtEpochMs = fetchedAt
        )
    }
}

internal fun normalizeSearchQuery(value: String): String =
    Normalizer.normalize(value.replace('Đ', 'D').replace('đ', 'd'), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "").trim()
