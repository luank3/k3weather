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

    val FALLBACK_DALAT = LocationItem(
        id = "1584071",
        name = "Đà Lạt",
        region = "Lâm Đồng",
        country = "Vietnam",
        latitude = 11.94646,
        longitude = 108.44193,
        timezone = "Asia/Ho_Chi_Minh",
        isCurrentLocation = false
    )

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Real device location (permission must already be granted): freshest last-known fix,
     * otherwise a live fix from the enabled providers. Never touches IP geolocation.
     */
    suspend fun resolveRealLocation(context: Context): LocationItem? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val activeProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val allCandidateProviders = (activeProviders + listOf(LocationManager.PASSIVE_PROVIDER, "fused")).distinct()
        if (activeProviders.isEmpty() && lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) == null) return null

        val fix = freshestLastKnown(lm, allCandidateProviders)
            ?: if (activeProviders.isNotEmpty()) awaitFreshFix(lm, activeProviders, allCandidateProviders) else null
        return fix?.let { withContext(Dispatchers.IO) { toLocationItem(context, it) } }
    }

    /** IP geolocation – only for the case where the user denied the location permission. */
    suspend fun resolveIpFallback(): LocationItem = withContext(Dispatchers.IO) {
        resolveIpLocation() ?: FALLBACK_DALAT
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun freshestLastKnown(lm: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < 30 * 60 * 1000L }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun awaitFreshFix(
        lm: LocationManager,
        activeProviders: List<String>,
        fallbackProviders: List<String>
    ): Location? =
        kotlinx.coroutines.withTimeoutOrNull(10_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                val listeners = mutableListOf<android.location.LocationListener>()
                fun finish(loc: Location?) {
                    listeners.forEach { runCatching { lm.removeUpdates(it) } }
                    if (cont.isActive) cont.resume(loc) {}
                }
                activeProviders.forEach { provider ->
                    val l = android.location.LocationListener { loc -> finish(loc) }
                    listeners.add(l)
                    runCatching {
                        lm.requestSingleUpdate(provider, l, android.os.Looper.getMainLooper())
                    }
                }
                cont.invokeOnCancellation { listeners.forEach { runCatching { lm.removeUpdates(it) } } }
            }
        } ?: fallbackProviders.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }

    private fun toLocationItem(context: Context, loc: Location): LocationItem {
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
