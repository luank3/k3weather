package com.k3weather.app.data

import android.content.SharedPreferences
import com.k3weather.app.core.model.AppSettings
import com.k3weather.app.core.model.CurrentWeather
import com.k3weather.app.core.model.ForecastData
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.core.model.WindSpeedUnit
import com.k3weather.app.core.repository.WeatherRepository
import com.k3weather.app.data.local.WeatherPreferences
import com.k3weather.app.data.remote.LocationResolver
import com.k3weather.app.ui.ForecastView
import com.k3weather.app.ui.WeatherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BugFixRegressionTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun slowOlderForecastRequestDoesNotOverwriteNewerSelection() = runTest(testDispatcher) {
        val cityA = LocationItem("city_a", "Hanoi", latitude = 21.0, longitude = 105.8)
        val cityB = LocationItem("city_b", "Tokyo", latitude = 35.6, longitude = 139.7)
        val repo = FakeWeatherRepository(initialSelected = cityA).apply {
            delaysMs[cityA.id] = 400L
            delaysMs[cityB.id] = 40L
            responses[cityA.id] = Result.success(sampleForecast(cityA, temp = 30.0))
            responses[cityB.id] = Result.success(sampleForecast(cityB, temp = 15.0))
        }

        val vm = WeatherViewModel(repo)
        vm.selectLocation(cityA)
        testScheduler.advanceTimeBy(20L)
        vm.selectLocation(cityB)
        advanceUntilIdle()

        assertEquals(cityB.id, vm.state.value.selectedLocation?.id)
        assertEquals(cityB.id, vm.state.value.forecast?.location?.id)
        assertEquals(15.0, vm.state.value.forecast!!.current.temperatureC, 0.0)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun selectingUncachedFailingCityClearsPreviousCityForecastAndSetsError() = runTest(testDispatcher) {
        val cityA = LocationItem("city_a", "Hanoi", latitude = 21.0, longitude = 105.8)
        val cityB = LocationItem("city_b", "London", latitude = 51.5, longitude = -0.1)
        val repo = FakeWeatherRepository(initialSelected = cityA).apply {
            responses[cityA.id] = Result.success(sampleForecast(cityA, temp = 28.0))
            responses[cityB.id] = Result.failure(IllegalStateException("Network down"))
        }

        val vm = WeatherViewModel(repo)
        advanceUntilIdle()
        assertEquals(cityA.id, vm.state.value.forecast?.location?.id)

        vm.selectLocation(cityB)
        advanceUntilIdle()

        assertEquals(cityB.id, vm.state.value.selectedLocation?.id)
        assertNull(vm.state.value.forecast)
        assertEquals("Network down", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun staleCachedForecastKeepsIsStaleFlagOnInitialLoad() = runTest(testDispatcher) {
        val cityA = LocationItem("city_a", "Da Nang", latitude = 16.0, longitude = 108.2)
        val stale = sampleForecast(cityA, temp = 25.0, isStale = true, fetchedAt = 1000L)
        val repo = FakeWeatherRepository(initialSelected = cityA).apply {
            cached[cityA.id] = stale
            responses[cityA.id] = Result.success(stale)
        }

        val vm = WeatherViewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.state.value.forecast?.isStale == true)
    }

    @Test
    fun changingDetectedLocationNeverPairsOldForecastWithNewLocation() = runTest(testDispatcher) {
        val gpsA = LocationItem("gps_10.0000_106.0000", "District 1", latitude = 10.0, longitude = 106.0, isCurrentLocation = true)
        val gpsB = LocationItem("gps_21.0000_105.0000", "Ba Dinh", latitude = 21.0, longitude = 105.0, isCurrentLocation = true)
        val repo = FakeWeatherRepository(initialSelected = gpsA).apply {
            delaysMs[gpsA.id] = 300L
            delaysMs[gpsB.id] = 50L
            responses[gpsA.id] = Result.success(sampleForecast(gpsA, temp = 33.0))
            responses[gpsB.id] = Result.success(sampleForecast(gpsB, temp = 22.0))
        }

        val vm = WeatherViewModel(repo)
        advanceUntilIdle()

        vm.setDetectedCurrentLocation(gpsA)
        testScheduler.advanceTimeBy(10L)
        vm.setDetectedCurrentLocation(gpsB)
        // Immediately after changing detected location, old forecast A must not remain under currentLocationForecast
        assertNull(vm.state.value.currentLocationForecast)

        advanceUntilIdle()
        assertEquals(gpsB.id, vm.state.value.detectedCurrentLocation?.id)
        assertEquals(gpsB.id, vm.state.value.currentLocationForecast?.location?.id)
    }

    @Test
    fun visibilityFormatsSmallDistancesWithoutClampingToOne() {
        assertEquals("100 m", ForecastView.formatVisibility(0.1, WindSpeedUnit.KMH))
        assertEquals("450 m", ForecastView.formatVisibility(0.45, WindSpeedUnit.MS))
        assertEquals("10 km", ForecastView.formatVisibility(10.0, WindSpeedUnit.KMH))
        assertEquals("0.1 mi", ForecastView.formatVisibility(0.1, WindSpeedUnit.MPH))
        assertEquals("0.3 mi", ForecastView.formatVisibility(0.45, WindSpeedUnit.MPH))
        assertEquals("6 mi", ForecastView.formatVisibility(10.0, WindSpeedUnit.MPH))
    }

    @Test
    fun reduceMotionRoundTripsAndCacheIsBoundedAndCleanedOnLocationRemoval() {
        val memPrefs = InMemorySharedPreferences()
        val prefs = WeatherPreferences(memPrefs)

        val settings = AppSettings(reduceMotion = true, language = "vi")
        prefs.setSettings(settings)
        assertTrue(prefs.getSettings().reduceMotion)
        assertEquals("vi", prefs.getSettings().language)

        // Superseded GPS cache is removed when detected location changes
        val gpsOld = LocationItem("gps_1.0000_2.0000", "Old", latitude = 1.0, longitude = 2.0, isCurrentLocation = true)
        val gpsNew = LocationItem("gps_3.0000_4.0000", "New", latitude = 3.0, longitude = 4.0, isCurrentLocation = true)
        prefs.setDetectedCurrentLocation(gpsOld)
        prefs.saveCachedForecast(gpsOld, timestamp = 1_000_000L, data = JSONObject().put("k", 1))
        assertTrue(memPrefs.contains("forecast_${gpsOld.id}"))

        prefs.setDetectedCurrentLocation(gpsNew)
        assertFalse(memPrefs.contains("forecast_${gpsOld.id}"))

        // Cache count stays bounded at <= 16 entries even across many locations
        val now = 10_000_000_000L
        for (i in 1..25) {
            val loc = LocationItem("tmp_$i", "City $i", latitude = i.toDouble(), longitude = i.toDouble())
            prefs.saveCachedForecast(loc, timestamp = now + i * 1000L, data = JSONObject().put("i", i))
        }
        val cacheKeys = memPrefs.all.keys.filter { it.startsWith("forecast_") }
        assertTrue("Expected <= 16 cache keys, was ${cacheKeys.size}", cacheKeys.size <= 16)

        // Removing a saved city removes its cached forecast too
        val saved = LocationItem("custom_city", "Hue", latitude = 16.46, longitude = 107.59)
        prefs.addLocation(saved)
        prefs.saveCachedForecast(saved, timestamp = now + 50_000L, data = JSONObject().put("h", 1))
        assertTrue(memPrefs.contains("forecast_${saved.id}"))
        assertTrue(prefs.removeLocation(saved.id))
        assertFalse(memPrefs.contains("forecast_${saved.id}"))
    }

    @Test
    fun ipLocationParsersHandleFailureAndSecondaryHttpsPayload() {
        val failedPrimary = JSONObject("""{"success":false,"message":"rate limit"}""")
        assertNull(LocationResolver.parseIpWhoIsJson(failedPrimary))

        val secondaryGeoJs = JSONObject(
            """
            {
              "city": "Ho Chi Minh City",
              "region": "Ho Chi Minh",
              "country": "Vietnam",
              "latitude": "10.8231",
              "longitude": "106.6297",
              "timezone": "Asia/Ho_Chi_Minh"
            }
            """.trimIndent()
        )
        val parsed = LocationResolver.parseGeoJsJson(secondaryGeoJs)!!
        assertEquals("Ho Chi Minh City", parsed.name)
        assertEquals("Asia/Ho_Chi_Minh", parsed.timezone)
        assertEquals("ip_10.8231_106.6297", parsed.id)
        assertTrue(parsed.isCurrentLocation)
    }

    private fun sampleForecast(
        location: LocationItem,
        temp: Double,
        isStale: Boolean = false,
        fetchedAt: Long = 1_700_000_000_000L
    ) = ForecastData(
        location = location,
        current = CurrentWeather(
            temperatureC = temp,
            apparentTemperatureC = temp,
            weatherCode = 0,
            condition = "Clear sky",
            isDay = true,
            windSpeedKmh = 10.0,
            humidityPercent = 60,
            minTempC = temp - 3.0,
            maxTempC = temp + 3.0,
            timeIso = "2026-11-14T10:00"
        ),
        hourly = emptyList(),
        daily = emptyList(),
        fetchedAtEpochMs = fetchedAt,
        isStale = isStale
    )

    private class FakeWeatherRepository(
        initialSelected: LocationItem
    ) : WeatherRepository {
        var selected: LocationItem = initialSelected
        var detected: LocationItem? = null
        var settings: AppSettings = AppSettings()
        val saved = mutableListOf(initialSelected)
        val cached = mutableMapOf<String, ForecastData>()
        val responses = mutableMapOf<String, Result<ForecastData>>()
        val delaysMs = mutableMapOf<String, Long>()

        override suspend fun getForecast(location: LocationItem, forceRefresh: Boolean): Result<ForecastData> {
            val d = delaysMs[location.id] ?: 0L
            if (d > 0) delay(d)
            return responses[location.id] ?: Result.failure(IllegalStateException("Missing fake response"))
        }

        override suspend fun getCachedForecast(locationId: String): ForecastData? = cached[locationId]
        override suspend fun searchLocations(query: String): Result<List<LocationItem>> = Result.success(emptyList())
        override suspend fun getSavedLocations(): List<LocationItem> = saved.toList()
        override suspend fun saveLocation(location: LocationItem): Boolean = saved.add(location)
        override suspend fun removeLocation(locationId: String): Boolean = saved.removeAll { it.id == locationId }
        override suspend fun getSelectedLocation(): LocationItem = selected
        override suspend fun setSelectedLocation(location: LocationItem) { selected = location }
        override suspend fun hasSelectedLocation(): Boolean = true
        override suspend fun getDetectedCurrentLocation(): LocationItem? = detected
        override suspend fun setDetectedCurrentLocation(location: LocationItem) { detected = location }
        override suspend fun getSettings(): AppSettings = settings
        override suspend fun updateSettings(settings: AppSettings) { this.settings = settings }
        override suspend fun clearCache() { cached.clear() }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val target: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = REMOVED }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) target.clear()
                for ((k, v) in pending) {
                    if (v === REMOVED || v == null) target.remove(k) else target[k] = v
                }
            }

            private object REMOVED
        }
    }
}
