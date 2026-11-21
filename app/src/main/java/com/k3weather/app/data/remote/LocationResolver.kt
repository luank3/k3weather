package com.k3weather.app.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Real device location (permission must already be granted and location services enabled):
     * freshest accurate last-known fix (unless [forceFresh] is true), otherwise a live fix from
     * enabled providers. Never touches IP geolocation.
     */
    suspend fun resolveRealLocation(
        context: Context,
        forceFresh: Boolean = false,
        preferredLanguage: String? = null
    ): LocationItem? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null

        val supported = runCatching { lm.allProviders }.getOrDefault(emptyList())
        val activeProviders = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { p -> (p in supported || p != "fused") && runCatching { lm.isProviderEnabled(p) }.getOrDefault(false) }
        val allCandidateProviders = (activeProviders + listOf("fused", LocationManager.PASSIVE_PROVIDER)).distinct()
        if (activeProviders.isEmpty() && runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull() == null) {
            return null
        }

        val fix = (if (!forceFresh) freshestLastKnown(lm, allCandidateProviders) else null)
            ?: if (activeProviders.isNotEmpty()) awaitFreshFix(context, lm, activeProviders, allCandidateProviders)
            else freshestFallbackLastKnown(lm, allCandidateProviders)
        return fix?.let { withContext(Dispatchers.IO) { toLocationItem(context, it, preferredLanguage) } }
    }

    /** IP geolocation – only for the case where the user denied the location permission. */
    suspend fun resolveIpFallback(): LocationItem = withContext(Dispatchers.IO) {
        resolveIpLocation() ?: FALLBACK_DALAT
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun freshestLastKnown(lm: LocationManager, providers: List<String>): Location? {
        val now = System.currentTimeMillis()
        val candidates = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { loc ->
                val ageMs = now - loc.time
                ageMs in 0..LAST_KNOWN_MAX_AGE_MS &&
                    (!loc.hasAccuracy() || loc.accuracy <= MAX_ACCEPTABLE_ACCURACY_METERS)
            }
        // Prefer a recent fine fix (< 5 min, <= 250 m) over a slightly newer coarse fix
        val fineRecent = candidates
            .filter { (now - it.time) <= FINE_FIX_MAX_AGE_MS && it.hasAccuracy() && it.accuracy <= GOOD_ACCURACY_METERS }
            .maxByOrNull { it.time }
        return fineRecent ?: candidates.maxByOrNull { it.time }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun freshestFallbackLastKnown(lm: LocationManager, providers: List<String>): Location? {
        val now = System.currentTimeMillis()
        return providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { (now - it.time) in 0..FALLBACK_LAST_KNOWN_MAX_AGE_MS }
            .maxByOrNull { it.time }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun awaitFreshFix(
        context: Context,
        lm: LocationManager,
        activeProviders: List<String>,
        fallbackProviders: List<String>
    ): Location? {
        var bestLiveFix: Location? = null
        val live = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
            kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                val cancelSignal = android.os.CancellationSignal()
                val mainExecutor = ContextCompat.getMainExecutor(context)
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                var remaining = activeProviders.size
                var finished = false
                var refineRunnable: Runnable? = null

                fun finish(loc: Location?) {
                    if (finished) return
                    finished = true
                    refineRunnable?.let { mainHandler.removeCallbacks(it) }
                    cancelSignal.cancel()
                    if (cont.isActive) cont.resume(loc) {}
                }

                activeProviders.forEach { provider ->
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(
                            lm,
                            provider,
                            cancelSignal,
                            mainExecutor
                        ) { loc ->
                            remaining -= 1
                            if (loc != null) {
                                val currentBest = bestLiveFix
                                if (currentBest == null || isBetterFix(loc, currentBest)) {
                                    bestLiveFix = loc
                                }
                                val isGoodEnough = !loc.hasAccuracy() || loc.accuracy <= GOOD_ACCURACY_METERS
                                if (isGoodEnough || remaining <= 0) {
                                    finish(bestLiveFix)
                                } else if (refineRunnable == null) {
                                    // Coarse fix received first; wait briefly for a finer GPS/fused fix
                                    val r = Runnable { finish(bestLiveFix) }
                                    refineRunnable = r
                                    mainHandler.postDelayed(r, REFINE_WINDOW_MS)
                                }
                            } else if (remaining <= 0) {
                                finish(bestLiveFix)
                            }
                        }
                    }.onFailure {
                        remaining -= 1
                        if (remaining <= 0) finish(bestLiveFix)
                    }
                }
                cont.invokeOnCancellation {
                    refineRunnable?.let { mainHandler.removeCallbacks(it) }
                    cancelSignal.cancel()
                }
            }
        }
        return live ?: bestLiveFix ?: freshestFallbackLastKnown(lm, fallbackProviders)
    }

    private fun isBetterFix(candidate: Location, current: Location): Boolean {
        if (candidate.hasAccuracy() && current.hasAccuracy()) {
            return candidate.accuracy < current.accuracy
        }
        return candidate.time > current.time
    }

    private suspend fun toLocationItem(
        context: Context,
        loc: Location,
        preferredLanguage: String? = null
    ): LocationItem {
        val lang = when (preferredLanguage) {
            "vi", "en" -> preferredLanguage
            else -> context.resources.configuration.locales[0]?.language
                ?.takeIf { it.isNotBlank() }
                ?: Locale.getDefault().language.ifBlank { "en" }
        }
        val locale = Locale(lang)

        // 1. Try OpenStreetMap Nominatim reverse geocoding for accurate ward/district/town resolution
        resolveNominatimReverse(loc.latitude, loc.longitude, lang)?.let { return it }

        // 2. Fallback to Android Geocoder, preferring subLocality / subAdminArea over province-wide locality
        var placeName = "Current location"
        var region = ""
        var country = ""
        try {
            val addresses = reverseGeocodeAndroid(context, locale, loc.latitude, loc.longitude)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val specificArea = listOfNotNull(
                    addr.subLocality?.takeIf { it.isNotBlank() },
                    addr.subAdminArea?.takeIf { it.isNotBlank() },
                    addr.featureName?.takeIf { it.isNotBlank() && !it.first().isDigit() }
                ).firstOrNull()
                val cityOrProvince = addr.locality?.takeIf { it.isNotBlank() }
                    ?: addr.adminArea?.takeIf { it.isNotBlank() }

                placeName = specificArea ?: cityOrProvince ?: "Current location"
                region = if (specificArea != null && cityOrProvince != null && !specificArea.equals(cityOrProvince, ignoreCase = true)) {
                    cityOrProvince
                } else {
                    addr.adminArea.orEmpty()
                }
                country = addr.countryName.orEmpty()
            }
        } catch (_: Exception) {
        }
        return LocationItem(
            id = gpsLocationId(loc.latitude, loc.longitude),
            name = placeName,
            region = region,
            country = country,
            latitude = loc.latitude,
            longitude = loc.longitude,
            timezone = "auto",
            isCurrentLocation = true
        )
    }

    private suspend fun reverseGeocodeAndroid(
        context: Context,
        locale: Locale,
        lat: Double,
        lon: Double
    ): List<android.location.Address>? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, locale)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            kotlinx.coroutines.withTimeoutOrNull(3_500L) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses) {}
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(null) {}
                        }
                    })
                }
            }
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lon, 1)
        }
    }

    private fun resolveNominatimReverse(lat: Double, lon: Double, lang: String): LocationItem? {
        return try {
            val url = URL("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=14&accept-language=$lang")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "K3Weather/1.0 (https://github.com/luank3/k3weather; Android)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode == 200) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val addr = json.optJSONObject("address") ?: return null
                    val localArea = listOf(
                        addr.optString("suburb"),
                        addr.optString("town"),
                        addr.optString("city_district"),
                        addr.optString("district"),
                        addr.optString("village"),
                        addr.optString("municipality"),
                        addr.optString("county"),
                        addr.optString("quarter")
                    ).firstOrNull { it.isNotBlank() }
                    val city = listOf(
                        addr.optString("city"),
                        addr.optString("state"),
                        addr.optString("province")
                    ).firstOrNull { it.isNotBlank() }.orEmpty()
                    val country = addr.optString("country", "")

                    val primaryName = (localArea ?: city.takeIf { it.isNotBlank() }) ?: return null
                    val cleanPrimary = cleanAdministrativeName(primaryName)
                    val regionName = if (localArea != null && city.isNotBlank() && !localArea.equals(city, ignoreCase = true)) {
                        city
                    } else {
                        ""
                    }
                    LocationItem(
                        id = gpsLocationId(lat, lon),
                        name = cleanPrimary,
                        region = regionName,
                        country = country,
                        latitude = lat,
                        longitude = lon,
                        timezone = "auto",
                        isCurrentLocation = true
                    )
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Buckets GPS coordinates to ~1.1 km (2 decimal places) for cache/identity stability
     * while preserving full precision in [LocationItem.latitude]/[LocationItem.longitude].
     */
    internal fun gpsLocationId(lat: Double, lon: Double): String =
        "gps_${String.format(Locale.US, "%.2f_%.2f", lat, lon)}"

    /**
     * Strips verbose administrative prefixes/suffixes unless doing so would leave a bare number
     * (e.g. "Phường 1", "Quận 7" remain intact, while "Phường Bến Nghé" becomes "Bến Nghé").
     */
    internal fun cleanAdministrativeName(rawName: String): String {
        val trimmed = rawName.trim()
        val stripped = trimmed
            .replace(Regex("^(Phường|Xã|Thị trấn|Thị xã|Quận|Huyện)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(Ward|Commune|District|Township|Town)$", RegexOption.IGNORE_CASE), "")
            .trim()
        if (stripped.isBlank() || stripped.first().isDigit()) {
            return trimmed
        }
        return stripped
    }

    private const val FINE_FIX_MAX_AGE_MS = 5 * 60 * 1000L
    private const val LAST_KNOWN_MAX_AGE_MS = 15 * 60 * 1000L
    private const val FALLBACK_LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 60 * 1000L
    private const val GOOD_ACCURACY_METERS = 250f
    private const val MAX_ACCEPTABLE_ACCURACY_METERS = 1500f
    private const val REFINE_WINDOW_MS = 2_500L

    fun resolveIpLocation(): LocationItem? {
        return resolvePrimaryIpLocation() ?: resolveSecondaryIpLocation()
    }

    private fun resolvePrimaryIpLocation(): LocationItem? {
        return try {
            val url = URL("https://ipwho.is/")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "K3Weather/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            try {
                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    parseIpWhoIsJson(JSONObject(responseText))
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveSecondaryIpLocation(): LocationItem? {
        return try {
            val url = URL("https://get.geojs.io/v1/ip/geo.json")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "K3Weather/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    parseGeoJsJson(JSONObject(responseText))
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun parseIpWhoIsJson(json: JSONObject): LocationItem? {
        if (!json.optBoolean("success", false)) return null
        val lat = json.optDouble("latitude", Double.NaN)
        val lon = json.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val city = json.optString("city").takeIf { it.isNotBlank() } ?: "Current location"
        val region = json.optString("region", "")
        val country = json.optString("country", "")
        val tz = json.optJSONObject("timezone")?.optString("id", "auto")?.ifBlank { "auto" } ?: "auto"
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

    internal fun parseGeoJsJson(json: JSONObject): LocationItem? {
        val lat = json.optString("latitude").toDoubleOrNull() ?: json.optDouble("latitude", Double.NaN)
        val lon = json.optString("longitude").toDoubleOrNull() ?: json.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        val city = json.optString("city").takeIf { it.isNotBlank() } ?: "Current location"
        val region = json.optString("region", "")
        val country = json.optString("country", "")
        val tz = json.optString("timezone", "auto").ifBlank { "auto" }
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
