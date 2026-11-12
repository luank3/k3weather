package com.k3weather.app.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.k3weather.app.core.model.LocationItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object LocationResolver {

    private val FALLBACK_DALAT = LocationItem(
        id = "1584071",
        name = "Đà Lạt",
        region = "Lâm Đồng",
        country = "Vietnam",
        latitude = 11.94646,
        longitude = 108.44193,
        timezone = "Asia/Ho_Chi_Minh",
        isCurrentLocation = false
    )

    suspend fun resolveDefaultLocation(context: Context): LocationItem = withContext(Dispatchers.IO) {
        // Step 1: Try System Location Services
        val systemLoc = resolveSystemLocation(context)
        if (systemLoc != null) {
            return@withContext systemLoc
        }

        // Step 2: Fallback to IP address geolocation
        val ipLoc = resolveIpLocation()
        if (ipLoc != null) {
            return@withContext ipLoc
        }

        // Step 3: Ultimate Fallback
        FALLBACK_DALAT
    }

    private fun resolveSystemLocation(context: Context): LocationItem? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!hasGps && !hasNetwork) {
            return null
        }

        var bestLocation: Location? = null
        if (hasGps) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (bestLocation == null && hasNetwork) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }

        val loc = bestLocation ?: return null

        var placeName = "Current location"
        var region = ""
        var country = ""

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                placeName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Current location"
                region = addr.adminArea.orEmpty()
                country = addr.countryName.orEmpty()
            }
        } catch (_: Exception) {
            // Geocoder service may not be available on some ROMs
        }

        return LocationItem(
            id = "gps_${String.format(Locale.US, "%.4f_%.4f", loc.latitude, loc.longitude)}",
            name = placeName,
            region = region,
            country = country,
            latitude = loc.latitude,
            longitude = loc.longitude,
            timezone = "auto",
            isCurrentLocation = true
        )
    }

    fun resolveIpLocation(): LocationItem? {
        return try {
            val url = URL("https://ipwho.is/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "K3Weather/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                if (json.optBoolean("success", false)) {
                    val city = json.optString("city").takeIf { it.isNotBlank() } ?: "Current location"
                    val region = json.optString("region", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("latitude", Double.NaN)
                    val lon = json.optDouble("longitude", Double.NaN)
                    val tz = json.optJSONObject("timezone")?.optString("id", "auto") ?: "auto"

                    if (!lat.isNaN() && !lon.isNaN()) {
                        return LocationItem(
                            id = "ip_${String.format(Locale.US, "%.4f_%.4f", lat, lon)}",
                            name = city,
                            region = region,
                            country = country,
                            latitude = lat,
                            longitude = lon,
                            timezone = tz,
                            isCurrentLocation = true
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            // Fallback to secondary endpoint if ipwho.is is unreachable
            resolveSecondaryIpLocation()
        }
    }

    private fun resolveSecondaryIpLocation(): LocationItem? {
        return try {
            val url = URL("http://ip-api.com/json/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "K3Weather/1.0")
            }
            if (connection.responseCode == 200) {
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                if (json.optString("status") == "success") {
                    val city = json.optString("city").takeIf { it.isNotBlank() } ?: "Current location"
                    val region = json.optString("regionName", "")
                    val country = json.optString("country", "")
                    val lat = json.optDouble("lat", Double.NaN)
                    val lon = json.optDouble("lon", Double.NaN)
                    val tz = json.optString("timezone", "auto")
                    if (!lat.isNaN() && !lon.isNaN()) {
                        return LocationItem(
                            id = "ip_${String.format(Locale.US, "%.4f_%.4f", lat, lon)}",
                            name = city,
                            region = region,
                            country = country,
                            latitude = lat,
                            longitude = lon,
                            timezone = tz,
                            isCurrentLocation = true
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
