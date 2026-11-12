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
        val query = "latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day&daily=weather_code,temperature_2m_min,temperature_2m_max" +
            "&timezone=auto&forecast_days=3"
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
            connection.setRequestProperty("User-Agent", "K3Weather/1.0 (Android)")
            connection.setRequestProperty("Accept", "application/json")
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
