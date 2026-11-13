package com.k3weather.app.data.remote

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

class OpenMeteoClient(private val customBaseUrl: String? = null) {
    fun fetchForecast(latitude: Double, longitude: Double): JSONObject {
        val base = customBaseUrl?.trimEnd('/') ?: "https://api.open-meteo.com/v1/forecast"
        val currentParams = "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,wind_gusts_10m,surface_pressure,precipitation,uv_index"
        val hourlyParams = "temperature_2m,weather_code,is_day,precipitation_probability,visibility"
        val dailyParams = "weather_code,temperature_2m_min,temperature_2m_max,precipitation_probability_max,uv_index_max,sunrise,sunset"
        val query = "latitude=$latitude&longitude=$longitude&current=$currentParams&hourly=$hourlyParams&daily=$dailyParams&timezone=auto&forecast_days=7"
        return request("$base?$query")
    }

    fun search(query: String): JSONObject {
        val base = customBaseUrl?.trimEnd('/') ?: "https://geocoding-api.open-meteo.com/v1/search"
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return request("$base?name=$encoded&count=10&language=en&format=json")
    }

    private fun request(urlStr: String): JSONObject {
        Log.d(TAG, "Requesting: $urlStr")
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "K3Weather/2.0 (Android)")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "gzip")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                ?: throw IllegalStateException("HTTP request failed with status $status")
            val encoding = connection.contentEncoding
            val decoded = if (encoding != null && encoding.contains("gzip", ignoreCase = true)) {
                GZIPInputStream(stream)
            } else {
                stream
            }
            val body = BufferedReader(InputStreamReader(decoded, Charsets.UTF_8)).use { it.readText() }
            if (status !in 200..299) {
                Log.e(TAG, "HTTP $status error: $body")
                throw IllegalStateException("HTTP $status: $body")
            }
            return JSONObject(body)
        } catch (e: Exception) {
            Log.e(TAG, "Network error on $urlStr", e)
            throw e
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "OpenMeteoClient"
        private const val TIMEOUT_MS = 10_000
    }
}
