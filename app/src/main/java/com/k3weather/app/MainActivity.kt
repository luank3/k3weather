package com.k3weather.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import com.k3weather.app.core.model.TemperatureUnit
import com.k3weather.app.data.local.WeatherPreferences
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.data.remote.LocationResolver
import com.k3weather.app.databinding.ActivityMainBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.databinding.LayoutRadarBinding
import com.k3weather.app.databinding.LayoutSavedLocationsBinding
import com.k3weather.app.databinding.LayoutSearchBinding
import com.k3weather.app.databinding.LayoutSettingsBinding
import com.k3weather.app.ui.ForecastBinder
import com.k3weather.app.ui.ForecastView
import com.k3weather.app.ui.RadarView
import com.k3weather.app.ui.SavedLocationsAdapter
import com.k3weather.app.ui.SavedLocationsBinder
import com.k3weather.app.ui.SearchBinder
import com.k3weather.app.ui.SearchView
import com.k3weather.app.ui.SettingsBinder
import com.k3weather.app.ui.WeatherUiState
import com.k3weather.app.ui.WeatherViewModel
import com.k3weather.app.ui.common.HeroWeatherArtworkView
import com.k3weather.app.ui.common.WeatherBackgroundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WeatherViewModel

    private lateinit var forecastBinding: LayoutForecastBinding
    private lateinit var radarBinding: LayoutRadarBinding
    private lateinit var savedBinding: LayoutSavedLocationsBinding
    private lateinit var settingsBinding: LayoutSettingsBinding
    private lateinit var searchBinding: LayoutSearchBinding

    private lateinit var forecastBinder: ForecastBinder
    private lateinit var radarView: RadarView
    private lateinit var savedAdapter: SavedLocationsAdapter
    private lateinit var savedBinder: SavedLocationsBinder
    private lateinit var searchBinder: SearchBinder
    private lateinit var settingsBinder: SettingsBinder

    private val savedForecasts = mutableMapOf<String, ForecastData>()

    private enum class Tab { FORECAST, RADAR, SAVED, SETTINGS }
    private var currentTab = Tab.FORECAST
    private var previousTab: Tab? = null

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

    override fun attachBaseContext(newBase: Context) {
        val prefs = WeatherPreferences(newBase)
        val lang = prefs.getSettings().language
        val context = if (lang == "en" || lang == "vi") {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as WeatherApplication
        val repository = app.repository
        val initialSettings = app.preferences.getSettings()
        lastAppliedLanguage = initialSettings.language
        viewModel = WeatherViewModel(repository, initialSettings)

        val initialTabName = savedInstanceState?.getString(KEY_ACTIVE_TAB)
            ?: intent.getStringExtra(KEY_ACTIVE_TAB)
        val initialTab = initialTabName?.let {
            runCatching { Tab.valueOf(it) }.getOrNull()
        } ?: Tab.FORECAST
        currentTab = initialTab
        previousTab = initialTab

        setupInsets()
        setupChildViews(initialTab)
        setupBottomNavigation(initialTab)
        setupBackPress()
        observeState()
        preloadSavedForecastCaches()
        resolveInitialLocation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAB, currentTab.name)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            binding.searchContainer.setPadding(0, systemBars.top, 0, systemBars.bottom)

            val dockMarginBottom = systemBars.bottom + (14 * resources.displayMetrics.density).toInt()
            val lp = binding.bottomNavBar.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.bottomMargin = dockMarginBottom
                binding.bottomNavBar.layoutParams = lp
            }
            insets
        }
    }

    private fun setupChildViews(initialTab: Tab) {
        forecastBinding = LayoutForecastBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        radarBinding = LayoutRadarBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        savedBinding = LayoutSavedLocationsBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        settingsBinding = LayoutSettingsBinding.inflate(layoutInflater, binding.fragmentContainer, false)

        forecastBinding.root.visibility = if (initialTab == Tab.FORECAST) View.VISIBLE else View.GONE
        radarBinding.root.visibility = if (initialTab == Tab.RADAR) View.VISIBLE else View.GONE
        savedBinding.root.visibility = if (initialTab == Tab.SAVED) View.VISIBLE else View.GONE
        settingsBinding.root.visibility = if (initialTab == Tab.SETTINGS) View.VISIBLE else View.GONE

        binding.fragmentContainer.addView(forecastBinding.root)
        binding.fragmentContainer.addView(radarBinding.root)
        binding.fragmentContainer.addView(savedBinding.root)
        binding.fragmentContainer.addView(settingsBinding.root)

        searchBinding = LayoutSearchBinding.inflate(layoutInflater, binding.searchContainer, false)
        binding.searchContainer.addView(searchBinding.root)

        val fv = ForecastView(forecastBinding)
        fv.onThemeOverrideChanged = { theme ->
            if (theme != null) {
                when (theme) {
                    HeroWeatherArtworkView.Theme.SUNNY -> {
                        binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x4DFBBF24.toInt())
                        binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x3338BDF8.toInt())
                    }
                    HeroWeatherArtworkView.Theme.RAINY -> {
                        binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x3322D3EE.toInt())
                        binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x333B82F6.toInt())
                    }
                    HeroWeatherArtworkView.Theme.CLOUDY -> {
                        binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x33818CF8.toInt())
                        binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x2694A3B8.toInt())
                    }
                    HeroWeatherArtworkView.Theme.NIGHT -> {
                        binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x40A855F7.toInt())
                        binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x334F46E5.toInt())
                    }
                }
            } else {
                updateMainBackground(viewModel.state.value)
            }
        }
        forecastBinder = ForecastBinder(fv)

        radarView = RadarView(radarBinding, lifecycleScope)

        savedBinding.cardCurrentLocation.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 24f * view.resources.displayMetrics.density)
            }
        }
        savedBinding.cardCurrentLocation.clipToOutline = true

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
        savedBinding.savedList.adapter = savedAdapter
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

        settingsBinding.attributions.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open-meteo.com/"))
                startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private fun preloadSavedForecastCaches() {
        val repository = (application as WeatherApplication).repository
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { repository.getSavedLocations() }
            saved.forEach { loc ->
                withContext(Dispatchers.IO) { repository.getCachedForecast(loc.id) }?.let {
                    savedForecasts[loc.id] = it
                }
            }
            savedBinder.bind(viewModel.state.value, savedForecasts)
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

    private fun setupBottomNavigation(initialTab: Tab) {
        binding.tabForecast.setOnClickListener { selectTab(Tab.FORECAST) }
        binding.tabRadar.setOnClickListener { selectTab(Tab.RADAR) }
        binding.tabSaved.setOnClickListener { selectTab(Tab.SAVED) }
        binding.tabSettings.setOnClickListener { selectTab(Tab.SETTINGS) }
        selectTab(initialTab)
    }

    private fun selectTab(tab: Tab) {
        val oldTab = previousTab
        currentTab = tab
        previousTab = tab

        val reduceMotion = viewModel.state.value.settings.reduceMotion
        val density = resources.displayMetrics.density

        // 4-Tab Liquid Glass Capsule Pill Styling
        fun styleTabItem(container: LinearLayout, icon: ImageView, text: TextView, isSelected: Boolean) {
            if (isSelected) {
                container.setBackgroundResource(R.drawable.bg_nav_active_pill)
                container.setPadding((14 * density).toInt(), 0, (14 * density).toInt(), 0)
                icon.imageTintList = ColorStateList.valueOf(0xFF0F172A.toInt())
                text.visibility = View.VISIBLE
                text.setTextColor(0xFF0F172A.toInt())
            } else {
                container.background = null
                container.setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
                icon.imageTintList = ColorStateList.valueOf(0xB3FFFFFF.toInt())
                text.visibility = View.GONE
            }
        }

        styleTabItem(binding.tabForecast, binding.ivTabForecast, binding.tvTabForecast, tab == Tab.FORECAST)
        styleTabItem(binding.tabRadar, binding.ivTabRadar, binding.tvTabRadar, tab == Tab.RADAR)
        styleTabItem(binding.tabSaved, binding.ivTabSaved, binding.tvTabSaved, tab == Tab.SAVED)
        styleTabItem(binding.tabSettings, binding.ivTabSettings, binding.tvTabSettings, tab == Tab.SETTINGS)

        fun getViewForTab(t: Tab): View = when (t) {
            Tab.FORECAST -> forecastBinding.root
            Tab.RADAR -> radarBinding.root
            Tab.SAVED -> savedBinding.root
            Tab.SETTINGS -> settingsBinding.root
        }

        val targetView = getViewForTab(tab)
        val outgoingView = if (oldTab != null && oldTab != tab) getViewForTab(oldTab) else null

        if (oldTab == null || reduceMotion || outgoingView == null) {
            forecastBinding.root.visibility = if (tab == Tab.FORECAST) View.VISIBLE else View.GONE
            radarBinding.root.visibility = if (tab == Tab.RADAR) View.VISIBLE else View.GONE
            savedBinding.root.visibility = if (tab == Tab.SAVED) View.VISIBLE else View.GONE
            settingsBinding.root.visibility = if (tab == Tab.SETTINGS) View.VISIBLE else View.GONE
            targetView.alpha = 1f
            targetView.translationX = 0f
        } else {
            val direction = if (tab.ordinal > oldTab.ordinal) 1f else -1f
            val distance = direction * (40f * density)

            // Animate outgoing screen
            outgoingView.animate().cancel()
            outgoingView.animate()
                .alpha(0f)
                .translationX(-distance * 0.5f)
                .setDuration(160)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    outgoingView.visibility = View.GONE
                    outgoingView.translationX = 0f
                }
                .start()

            // Animate incoming screen
            targetView.animate().cancel()
            targetView.visibility = View.VISIBLE
            targetView.alpha = 0f
            targetView.translationX = distance
            targetView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        if (tab == Tab.SAVED) {
            refreshSavedForecasts()
        }
    }

    private fun openSearch(source: SearchSource) {
        searchSource = source
        val reduceMotion = viewModel.state.value.settings.reduceMotion
        val density = resources.displayMetrics.density

        searchBinding.searchInput.text?.clear()
        viewModel.search("")

        if (reduceMotion) {
            binding.searchContainer.visibility = View.VISIBLE
            binding.bottomNavBar.visibility = View.GONE
        } else {
            binding.searchContainer.animate().cancel()
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchContainer.alpha = 0f
            binding.searchContainer.translationY = 50f * density
            binding.searchContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(240)
                .setInterpolator(DecelerateInterpolator())
                .start()

            binding.bottomNavBar.animate().cancel()
            binding.bottomNavBar.animate()
                .alpha(0f)
                .translationY(30f * density)
                .setDuration(180)
                .withEndAction { binding.bottomNavBar.visibility = View.GONE }
                .start()
        }

        searchBinding.searchInput.requestFocus()
    }

    private fun closeSearch() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        imm?.hideSoftInputFromWindow(searchBinding.searchInput.windowToken, 0)
        searchBinding.searchInput.clearFocus()

        val reduceMotion = viewModel.state.value.settings.reduceMotion
        val density = resources.displayMetrics.density

        if (reduceMotion) {
            binding.searchContainer.visibility = View.GONE
            binding.bottomNavBar.visibility = View.VISIBLE
        } else {
            binding.searchContainer.animate().cancel()
            binding.searchContainer.animate()
                .alpha(0f)
                .translationY(40f * density)
                .setDuration(180)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    binding.searchContainer.visibility = View.GONE
                    binding.searchContainer.translationY = 0f
                }
                .start()

            binding.bottomNavBar.animate().cancel()
            binding.bottomNavBar.visibility = View.VISIBLE
            binding.bottomNavBar.alpha = 0f
            binding.bottomNavBar.translationY = 30f * density
            binding.bottomNavBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
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
                    applyColorScheme(state.settings.colorScheme)
                    applyLanguage(state.settings.language)

                    forecastBinder.bind(
                        state = state,
                        onSearch = { openSearch(SearchSource.FORECAST) },
                        onRefresh = { viewModel.refresh(force = true) },
                        onSelectCity = { location -> viewModel.selectLocation(location) },
                        onToggleUnit = {
                            val current = viewModel.state.value.settings.tempUnit
                            val next = if (current == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                            viewModel.updateSettings(viewModel.state.value.settings.copy(tempUnit = next))
                        }
                    )

                    radarView.bind(state)

                    // Keep active forecast in savedForecasts map
                    state.forecast?.let { savedForecasts[it.location.id] = it }

                    savedBinder.bind(state, savedForecasts)
                    savedBinding.emptyLocations.visibility = if (state.savedLocations.isEmpty()) View.VISIBLE else View.GONE

                    val cur = state.detectedCurrentLocation
                    if (cur != null) {
                        savedBinding.cardCurrentLocation.visibility = View.VISIBLE
                        savedBinding.tvCurrentLocCity.text = if (cur.subtitle.isNotBlank()) "${cur.name}, ${cur.subtitle}" else cur.name
                        val curForecast = state.currentLocationForecast
                        if (curForecast != null) {
                            savedBinding.tvCurrentLocTemp.text = state.settings.tempUnit.format(curForecast.current.temperatureC)
                            savedBinding.tvCurrentLocCondition.text = WeatherCodeMapper.getLocalizedLabel(this@MainActivity, curForecast.current.weatherCode)
                        }
                        savedBinding.cardCurrentLocation.setOnClickListener {
                            viewModel.returnToCurrentLocation()
                            selectTab(Tab.FORECAST)
                            Toast.makeText(this@MainActivity, getString(R.string.switched_to_current_location), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        savedBinding.cardCurrentLocation.visibility = View.GONE
                    }

                    settingsBinder.bind(state.settings)

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

        savedBinding.addCityButton.backgroundTintList = tintList
        savedBinding.ivCurrentLocIcon.imageTintList = tintList
        savedBinding.tvCurrentLocLabel.setTextColor(accentColor)

        val searchPill = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f
            setColor(0x660B2233)
            setStroke((1.2f * resources.displayMetrics.density).toInt(), accentColor)
        }
        searchBinding.root.findViewById<View>(R.id.searchInput)?.parent?.let {
            if (it is View) it.background = searchPill
        }
    }

    private var lastAppliedLanguage: String? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lang = viewModel.state.value.settings.language
        val locale = if (lang == "vi") Locale("vi") else Locale("en")
        val config = Configuration(newConfig)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun applyLanguage(language: String) {
        if (lastAppliedLanguage == null) {
            lastAppliedLanguage = language
            return
        }
        if (lastAppliedLanguage == language) return
        lastAppliedLanguage = language

        val locale = if (language == "vi") Locale("vi") else Locale("en")
        Locale.setDefault(locale)

        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        applicationContext.resources.updateConfiguration(config, resources.displayMetrics)

        val targetLocale = when (language) {
            "vi" -> LocaleListCompat.forLanguageTags("vi")
            "en" -> LocaleListCompat.forLanguageTags("en")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(targetLocale)

        reloadChildViews()
    }

    private fun reloadChildViews() {
        val oldForecast = forecastBinding.root
        val oldRadar = radarBinding.root
        val oldSaved = savedBinding.root
        val oldSettings = settingsBinding.root
        val oldSearch = searchBinding.root

        setupChildViews(currentTab)

        binding.fragmentContainer.removeView(oldForecast)
        binding.fragmentContainer.removeView(oldRadar)
        binding.fragmentContainer.removeView(oldSaved)
        binding.fragmentContainer.removeView(oldSettings)
        binding.searchContainer.removeView(oldSearch)

        // Update bottom navigation bar labels in new locale
        binding.tvTabForecast.text = getString(R.string.tab_forecast)
        binding.tvTabRadar.text = getString(R.string.tab_radar)
        binding.tvTabSaved.text = getString(R.string.tab_saved)
        binding.tvTabSettings.text = getString(R.string.tab_settings)

        // Re-bind current data in new locale
        val state = viewModel.state.value
        settingsBinder.bind(state.settings)
        forecastBinder.bind(
            state = state,
            onSearch = { openSearch(SearchSource.FORECAST) },
            onRefresh = { viewModel.refresh(force = true) },
            onSelectCity = { location -> viewModel.selectLocation(location) },
            onToggleUnit = {
                val current = viewModel.state.value.settings.tempUnit
                val next = if (current == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
                viewModel.updateSettings(viewModel.state.value.settings.copy(tempUnit = next))
            }
        )
        radarView.bind(state)
        savedBinder.bind(state, savedForecasts)
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
    }

    private fun updateMainBackground(state: WeatherUiState) {
        val code = state.forecast?.current?.weatherCode ?: 0
        val isDay = state.forecast?.current?.isDay ?: true
        val reduceMotion = state.settings.reduceMotion
        val colorScheme = state.settings.colorScheme

        WeatherBackgroundHelper.applyWeatherEffect(
            view = binding.weatherEffectBackground,
            weatherCode = code,
            isDay = isDay,
            reduceMotion = reduceMotion,
            colorScheme = colorScheme,
            enabled = true
        )

        // Dynamic Ambient Mesh Glow Blobs based on atmosphere
        when {
            !isDay -> {
                binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x40A855F7.toInt())
                binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x334F46E5.toInt())
            }
            code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> {
                binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x3322D3EE.toInt())
                binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x333B82F6.toInt())
            }
            code in listOf(2, 3, 45, 48) -> {
                binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x33818CF8.toInt())
                binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x2694A3B8.toInt())
            }
            else -> {
                binding.glowBlobTop.backgroundTintList = ColorStateList.valueOf(0x4DFBBF24.toInt())
                binding.glowBlobMid.backgroundTintList = ColorStateList.valueOf(0x3338BDF8.toInt())
            }
        }
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
                }
            }
            savedBinder.bind(viewModel.state.value, savedForecasts)

            // Refresh stale or missing saved forecasts in background
            saved.forEach { loc ->
                withContext(Dispatchers.IO) {
                    repository.getForecast(loc, forceRefresh = false).getOrNull()
                }?.let { savedForecasts[loc.id] = it }
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

    companion object {
        private const val KEY_ACTIVE_TAB = "key_active_tab"
    }
}
