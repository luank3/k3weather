package com.k3weather.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.k3weather.app.core.model.*
import org.json.JSONArray
import org.json.JSONObject

class WeatherPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_preferences", Context.MODE_PRIVATE)

    fun getSettings(): AppSettings = runCatching {
        JSONObject(prefs.getString(KEY_SETTINGS, null) ?: "{}").let {
            AppSettings(
                tempUnit = runCatching { TemperatureUnit.valueOf(it.optString("tempUnit", "CELSIUS")) }.getOrDefault(TemperatureUnit.CELSIUS),
                windSpeedUnit = runCatching { WindSpeedUnit.valueOf(it.optString("windSpeedUnit", "KMH")) }.getOrDefault(WindSpeedUnit.KMH),
                precipitationUnit = runCatching { PrecipitationUnit.valueOf(it.optString("precipitationUnit", "MILLIMETERS")) }.getOrDefault(PrecipitationUnit.MILLIMETERS),
                pressureUnit = runCatching { PressureUnit.valueOf(it.optString("pressureUnit", "HPA")) }.getOrDefault(PressureUnit.HPA),
                weatherBackgroundsEnabled = it.optBoolean("weatherBackgroundsEnabled", true),
                reduceMotion = false,
                useCurrentLocation = it.optBoolean("useCurrentLocation", true),
                colorScheme = AppColorScheme.fromId(it.optString("colorScheme", "cyan")),
                language = it.optString("language", "system"),
                customApiBaseUrl = it.optString("customApiBaseUrl").takeIf(String::isNotBlank)
            )
        }
    }.getOrDefault(AppSettings())

    fun setSettings(settings: AppSettings) {
        prefs.edit().putString(
            KEY_SETTINGS,
            JSONObject()
                .put("tempUnit", settings.tempUnit.name)
                .put("windSpeedUnit", settings.windSpeedUnit.name)
                .put("precipitationUnit", settings.precipitationUnit.name)
                .put("pressureUnit", settings.pressureUnit.name)
                .put("weatherBackgroundsEnabled", settings.weatherBackgroundsEnabled)
                .put("reduceMotion", settings.reduceMotion)
                .put("useCurrentLocation", settings.useCurrentLocation)
                .put("colorScheme", settings.colorScheme.id)
                .put("language", settings.language)
                .put("customApiBaseUrl", settings.customApiBaseUrl ?: "")
                .toString()
        ).apply()
    }

    fun clearCache() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_CACHE_PREFIX) }.forEach {
            editor.remove(it)
        }
        editor.apply()
    }

    fun hasSelectedLocation(): Boolean {
        return prefs.contains(KEY_SELECTED)
    }

    fun getDetectedCurrentLocation(): LocationItem? {
        val raw = prefs.getString(KEY_CURRENT_LOCATION, null) ?: return null
        return runCatching { JSONObject(raw).toLocation() }.getOrNull()
    }

    fun setDetectedCurrentLocation(location: LocationItem) {
        prefs.edit().putString(KEY_CURRENT_LOCATION, location.toJson()).apply()
    }

    fun getSavedLocations(): List<LocationItem> {
        val saved = runCatching { JSONArray(prefs.getString(KEY_LOCATIONS, null) ?: "[]").toLocations() }.getOrDefault(emptyList())
        return if (saved.isEmpty()) DEFAULT_LOCATIONS.also { saveLocations(it) } else saved
    }

    fun saveLocations(locations: List<LocationItem>) {
        prefs.edit().putString(KEY_LOCATIONS, locations.toJson()).apply()
    }

    fun getSelectedLocation(): LocationItem {
        val raw = prefs.getString(KEY_SELECTED, null) ?: return getDetectedCurrentLocation() ?: DEFAULT_LOCATIONS.first()
        return runCatching { JSONObject(raw).toLocation() }.getOrNull() ?: DEFAULT_LOCATIONS.first()
    }

    fun setSelectedLocation(location: LocationItem) {
        prefs.edit().putString(KEY_SELECTED, location.toJson()).apply()
    }

    fun getCachedForecast(locationId: String): JSONObject? = runCatching {
        JSONObject(prefs.getString(KEY_CACHE_PREFIX + locationId, null) ?: return null)
    }.getOrNull()

    fun saveCachedForecast(location: LocationItem, timestamp: Long, data: JSONObject) {
        val wrapper = JSONObject()
            .put("timestamp", timestamp)
            .put("location", location.toJsonObject())
            .put("data", data)
        prefs.edit().putString(KEY_CACHE_PREFIX + location.id, wrapper.toString()).apply()
    }

    fun parseLocationFromJson(obj: JSONObject?): LocationItem? = obj?.toLocation()

    fun addLocation(location: LocationItem): Boolean {
        val locations = getSavedLocations()
        if (locations.any { it.id == location.id || (kotlin.math.abs(it.latitude - location.latitude) < 0.01 && kotlin.math.abs(it.longitude - location.longitude) < 0.01) }) {
            return false
        }
        saveLocations(locations + location)
        return true
    }

    fun removeLocation(id: String): Boolean {
        val locations = getSavedLocations()
        val updated = locations.filterNot { it.id == id }
        if (updated.size == locations.size) return false
        saveLocations(updated)
        return true
    }

    private fun List<LocationItem>.toJson() = JSONArray().also { array -> forEach { array.put(it.toJsonObject()) } }.toString()
    private fun LocationItem.toJson() = toJsonObject().toString()
    private fun LocationItem.toJsonObject() = JSONObject().put("id", id).put("name", name).put("region", region).put("country", country)
        .put("latitude", latitude).put("longitude", longitude).put("timezone", timezone).put("isCurrentLocation", isCurrentLocation)
    private fun JSONArray.toLocations(): List<LocationItem> = (0 until length()).mapNotNull { runCatching { getJSONObject(it).toLocation() }.getOrNull() }
    private fun JSONObject.toLocation(): LocationItem? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val name = optString("name").takeIf { it.isNotBlank() } ?: return null
        val lat = optDouble("latitude")
        val lon = optDouble("longitude")
        if (lat.isNaN() || lon.isNaN()) return null
        return LocationItem(
            id = id,
            name = name,
            region = optString("region", ""),
            country = optString("country", ""),
            latitude = lat,
            longitude = lon,
            timezone = optString("timezone", "Asia/Ho_Chi_Minh").ifBlank { "Asia/Ho_Chi_Minh" },
            isCurrentLocation = optBoolean("isCurrentLocation", false)
        )
    }

    companion object {
        private const val KEY_SETTINGS = "settings"
        private const val KEY_LOCATIONS = "saved_locations"
        private const val KEY_SELECTED = "selected_location"
        private const val KEY_CURRENT_LOCATION = "detected_current_location"
        private const val KEY_CACHE_PREFIX = "forecast_"
        val DEFAULT_LOCATIONS = listOf(
            LocationItem("1584071", "Đà Lạt", "Lâm Đồng", "Vietnam", 11.94646, 108.44193),
            LocationItem("1583992", "Đà Nẵng", "Đà Nẵng", "Vietnam", 16.06778, 108.22083),
            LocationItem("1581130", "Hà Nội", "Hà Nội", "Vietnam", 21.0245, 105.84117)
        )
    }
}
