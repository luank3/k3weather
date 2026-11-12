package com.k3weather.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.k3weather.app.core.model.AppColorScheme
import com.k3weather.app.core.model.ForecastData
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.remote.LocationResolver
import com.k3weather.app.databinding.ActivityMainBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.databinding.LayoutSavedLocationsBinding
import com.k3weather.app.databinding.LayoutSearchBinding
import com.k3weather.app.databinding.LayoutSettingsBinding
import com.k3weather.app.ui.ForecastBinder
import com.k3weather.app.ui.ForecastView
import com.k3weather.app.ui.SavedLocationsAdapter
import com.k3weather.app.ui.SavedLocationsBinder
import com.k3weather.app.ui.SearchBinder
import com.k3weather.app.ui.SearchView
import com.k3weather.app.ui.SettingsBinder
import com.k3weather.app.ui.WeatherUiState
import com.k3weather.app.ui.WeatherViewModel
import com.k3weather.app.ui.common.WeatherBackgroundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WeatherViewModel

    private lateinit var forecastBinding: LayoutForecastBinding
    private lateinit var savedBinding: LayoutSavedLocationsBinding
    private lateinit var settingsBinding: LayoutSettingsBinding
    private lateinit var searchBinding: LayoutSearchBinding

    private lateinit var forecastBinder: ForecastBinder
    private lateinit var savedAdapter: SavedLocationsAdapter
    private lateinit var savedBinder: SavedLocationsBinder
    private lateinit var searchBinder: SearchBinder
    private lateinit var settingsBinder: SettingsBinder

    private val savedForecasts = mutableMapOf<String, ForecastData>()

    private enum class Tab { FORECAST, SAVED, SETTINGS }
    private var currentTab = Tab.FORECAST

    private enum class SearchSource { FORECAST, SAVED }
    private var searchSource = SearchSource.FORECAST

    private var activeColorScheme: AppColorScheme? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            obtainCurrentLocation()
        } else {
            Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show()
            val currentSettings = viewModel.state.value.settings
            viewModel.updateSettings(currentSettings.copy(useCurrentLocation = false))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repository = (application as WeatherApplication).repository
        viewModel = WeatherViewModel(repository)

        setupInsets()
        setupChildViews()
        setupBottomNavigation()
        setupBackPress()
        observeState()
        resolveInitialLocation()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            binding.searchContainer.setPadding(0, systemBars.top, 0, systemBars.bottom)
            binding.bottomNavBar.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupChildViews() {
        // Inflate tabs into fragmentContainer
        forecastBinding = LayoutForecastBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        savedBinding = LayoutSavedLocationsBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        settingsBinding = LayoutSettingsBinding.inflate(layoutInflater, binding.fragmentContainer, false)

        binding.fragmentContainer.addView(forecastBinding.root)
        binding.fragmentContainer.addView(savedBinding.root)
        binding.fragmentContainer.addView(settingsBinding.root)

        // Inflate search into searchContainer
        searchBinding = LayoutSearchBinding.inflate(layoutInflater, binding.searchContainer, false)
        binding.searchContainer.addView(searchBinding.root)

        // Binders
        forecastBinder = ForecastBinder(ForecastView(forecastBinding))

        savedAdapter = SavedLocationsAdapter(
            onSelect = { location ->
                viewModel.selectLocation(location)
                selectTab(Tab.FORECAST)
            },
            onDelete = { location ->
                if (viewModel.state.value.savedLocations.size <= 1) {
                    Toast.makeText(this, getString(R.string.keep_one_city), Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.removeLocation(location)
                }
            }
        )
        savedBinding.savedLocationsList.adapter = savedAdapter
        savedBinding.addCityButton.setOnClickListener {
            openSearch(SearchSource.SAVED)
        }
        savedBinder = SavedLocationsBinder(savedAdapter)

        searchBinder = SearchBinder(SearchView(searchBinding))

        settingsBinder = SettingsBinder(settingsBinding) { newSettings ->
            if (newSettings.useCurrentLocation && !viewModel.state.value.settings.useCurrentLocation) {
                requestLocationPermission()
            }
            viewModel.updateSettings(newSettings)
        }

        // Attribution click
        settingsBinding.attributions.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveInitialLocation() {
        val repository = (application as WeatherApplication).repository
        lifecycleScope.launch {
            val detected = LocationResolver.resolveDefaultLocation(this@MainActivity)
            viewModel.setDetectedCurrentLocation(detected)

            val hasExplicit = withContext(Dispatchers.IO) { repository.hasSelectedLocation() }
            val settings = withContext(Dispatchers.IO) { repository.getSettings() }

            if (!hasExplicit || settings.useCurrentLocation) {
                viewModel.selectLocation(detected)
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.tabForecast.setOnClickListener { selectTab(Tab.FORECAST) }
        binding.tabSaved.setOnClickListener { selectTab(Tab.SAVED) }
        binding.tabSettings.setOnClickListener { selectTab(Tab.SETTINGS) }
        selectTab(Tab.FORECAST)
    }

    private fun selectTab(tab: Tab) {
        currentTab = tab
        forecastBinding.root.visibility = if (tab == Tab.FORECAST) View.VISIBLE else View.GONE
        savedBinding.root.visibility = if (tab == Tab.SAVED) View.VISIBLE else View.GONE
        settingsBinding.root.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE

        val active = ContextCompat.getColor(this, R.color.text_primary)
        val muted = ContextCompat.getColor(this, R.color.text_muted)

        binding.ivTabForecast.imageTintList = ColorStateList.valueOf(if (tab == Tab.FORECAST) active else muted)
        binding.tvTabForecast.setTextColor(if (tab == Tab.FORECAST) active else muted)
        binding.indicatorForecast.visibility = if (tab == Tab.FORECAST) View.VISIBLE else View.GONE

        binding.ivTabSaved.imageTintList = ColorStateList.valueOf(if (tab == Tab.SAVED) active else muted)
        binding.tvTabSaved.setTextColor(if (tab == Tab.SAVED) active else muted)
        binding.indicatorSaved.visibility = if (tab == Tab.SAVED) View.VISIBLE else View.GONE

        binding.ivTabSettings.imageTintList = ColorStateList.valueOf(if (tab == Tab.SETTINGS) active else muted)
        binding.tvTabSettings.setTextColor(if (tab == Tab.SETTINGS) active else muted)
        binding.indicatorSettings.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE

        if (tab == Tab.SAVED) {
            refreshSavedForecasts()
        }
    }

    private fun openSearch(source: SearchSource) {
        searchSource = source
        binding.searchContainer.visibility = View.VISIBLE
        binding.bottomNavBar.visibility = View.GONE
        searchBinding.searchInput.text?.clear()
        viewModel.search("")
        searchBinding.searchInput.requestFocus()
    }

    private fun closeSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchBinding.searchInput.windowToken, 0)
        binding.searchContainer.visibility = View.GONE
        binding.bottomNavBar.visibility = View.VISIBLE
        searchBinding.searchInput.clearFocus()
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.searchContainer.visibility == View.VISIBLE) {
                    closeSearch()
                } else if (currentTab != Tab.FORECAST) {
                    selectTab(Tab.FORECAST)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    // Apply Language & Color Scheme
                    applyColorScheme(state.settings.colorScheme)
                    applyLanguage(state.settings.language)

                    // Update Forecast
                    forecastBinder.bind(
                        state = state,
                        onSearch = { openSearch(SearchSource.FORECAST) },
                        onRefresh = { viewModel.refresh(force = true) },
                        onReturnToCurrentLocation = {
                            viewModel.returnToCurrentLocation()
                            Toast.makeText(this@MainActivity, getString(R.string.switched_to_current_location), Toast.LENGTH_SHORT).show()
                        }
                    )

                    // Update Saved
                    savedBinder.bind(state, savedForecasts)
                    savedBinding.emptyState.visibility = if (state.savedLocations.isEmpty()) View.VISIBLE else View.GONE

                    // Update Saved Current Location Card
                    val cur = state.detectedCurrentLocation
                    if (cur != null) {
                        savedBinding.cardCurrentLocation.visibility = View.VISIBLE
                        savedBinding.tvCurrentLocName.text = if (cur.subtitle.isNotBlank()) "${cur.name}, ${cur.subtitle}" else cur.name
                        val curForecast = state.currentLocationForecast
                        if (curForecast != null) {
                            savedBinding.tvCurrentLocTemp.text = state.settings.tempUnit.format(curForecast.current.temperatureC)
                            savedBinding.ivCurrentLocWeatherIcon.setImageResource(
                                WeatherCodeMapper.getIcon(curForecast.current.weatherCode, curForecast.current.isDay)
                            )
                        }
                        savedBinding.cardCurrentLocation.setOnClickListener {
                            viewModel.returnToCurrentLocation()
                            selectTab(Tab.FORECAST)
                            Toast.makeText(this@MainActivity, getString(R.string.switched_to_current_location), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        savedBinding.cardCurrentLocation.visibility = View.GONE
                    }

                    // Update Settings
                    settingsBinder.bind(state.settings)

                    // Update Search
                    searchBinder.bind(
                        state = state,
                        onBack = { closeSearch() },
                        onQuery = { query -> viewModel.search(query) },
                        onSelect = { location ->
                            if (searchSource == SearchSource.FORECAST) {
                                viewModel.selectLocation(location)
                                closeSearch()
                                selectTab(Tab.FORECAST)
                            } else {
                                viewModel.saveLocation(location)
                                closeSearch()
                                selectTab(Tab.SAVED)
                            }
                        }
                    )

                    // Update Weather Effect Background
                    updateMainBackground(state)
                }
            }
        }
    }

    private fun applyColorScheme(colorScheme: AppColorScheme) {
        if (activeColorScheme == colorScheme) return
        activeColorScheme = colorScheme

        val accentColor = colorScheme.primaryAccent
        val tintList = ColorStateList.valueOf(accentColor)

        // Indicator pills
        binding.indicatorForecast.backgroundTintList = tintList
        binding.indicatorSaved.backgroundTintList = tintList
        binding.indicatorSettings.backgroundTintList = tintList

        // Saved add button & current location quick card
        savedBinding.addCityButton.imageTintList = tintList
        savedBinding.ivCurrentLocIcon.imageTintList = tintList
        savedBinding.tvCurrentLocLabel.setTextColor(accentColor)

        // Search pill border
        val searchPill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * resources.displayMetrics.density
            setColor(0xCC0E282E.toInt())
            setStroke((1.5f * resources.displayMetrics.density).toInt(), accentColor)
        }
        searchBinding.root.findViewById<View>(R.id.searchInput)?.parent?.let {
            if (it is View) it.background = searchPill
        }
    }

    private fun applyLanguage(language: String) {
        val targetLocale = when (language) {
            "vi" -> LocaleListCompat.forLanguageTags("vi")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        if (AppCompatDelegate.getApplicationLocales() != targetLocale) {
            AppCompatDelegate.setApplicationLocales(targetLocale)
        }
    }

    private fun updateMainBackground(state: WeatherUiState) {
        val code = state.forecast?.current?.weatherCode ?: 0
        val isDay = state.forecast?.current?.isDay ?: true
        val enabled = state.settings.weatherBackgroundsEnabled
        val reduceMotion = state.settings.reduceMotion
        val colorScheme = state.settings.colorScheme

        WeatherBackgroundHelper.applyWeatherEffect(
            view = binding.weatherEffectBackground,
            weatherCode = code,
            isDay = isDay,
            reduceMotion = reduceMotion,
            colorScheme = colorScheme,
            enabled = enabled
        )
    }

    private fun refreshSavedForecasts() {
        val repository = (application as WeatherApplication).repository
        val saved = viewModel.state.value.savedLocations
        lifecycleScope.launch {
            saved.forEach { loc ->
                val cached = withContext(Dispatchers.IO) {
                    repository.getCachedForecast(loc.id)
                }
                if (cached != null) {
                    savedForecasts[loc.id] = cached
                } else {
                    withContext(Dispatchers.IO) {
                        repository.getForecast(loc, forceRefresh = false).getOrNull()
                    }?.let { savedForecasts[loc.id] = it }
                }
            }
            savedBinder.bind(viewModel.state.value, savedForecasts)
        }
    }

    private fun requestLocationPermission() {
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            obtainCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun obtainCurrentLocation() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val hasGps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val hasNetwork = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!hasGps && !hasNetwork) {
            Toast.makeText(this, getString(R.string.location_services_disabled), Toast.LENGTH_SHORT).show()
            return
        }

        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) return

        var lastLocation: Location? = null
        if (hasNetwork) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        if (lastLocation == null && hasGps) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }

        if (lastLocation != null) {
            handleResolvedLocation(lastLocation)
        } else {
            val provider = if (hasNetwork) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
            locationManager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    handleResolvedLocation(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }, mainLooper)
        }
    }

    private fun handleResolvedLocation(loc: Location) {
        lifecycleScope.launch(Dispatchers.IO) {
            var placeName = getString(R.string.current_location)
            var region = ""
            var country = ""
            try {
                val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    placeName = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: getString(R.string.current_location)
                    region = addr.adminArea.orEmpty()
                    country = addr.countryName.orEmpty()
                }
            } catch (_: Exception) {
            }

            val currentLocItem = LocationItem(
                id = "current_${loc.latitude}_${loc.longitude}",
                name = placeName,
                region = region,
                country = country,
                latitude = loc.latitude,
                longitude = loc.longitude,
                timezone = "auto",
                isCurrentLocation = true
            )

            withContext(Dispatchers.Main) {
                viewModel.setDetectedCurrentLocation(currentLocItem)
                viewModel.selectLocation(currentLocItem)
                selectTab(Tab.FORECAST)
                Toast.makeText(this@MainActivity, getString(R.string.location_updated, placeName), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
