package com.k3weather.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.k3weather.app.ui.common.HeroWeatherArtworkView
import com.k3weather.app.ui.common.WeatherBackgroundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var previousTab: Tab? = null

    private enum class SearchSource { FORECAST, SAVED }
    private var searchSource = SearchSource.FORECAST

    private var activeColorScheme: AppColorScheme? = null

    private var startupLocationPending = false
    private var locationResolveJob: Job? = null
    private var locationDisabledToastShown = false
    private var wasLocationEnabled = false

    private val locationProviderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == LocationManager.PROVIDERS_CHANGED_ACTION || action == LocationManager.MODE_CHANGED_ACTION) {
                refreshLocationIfNeeded(fromResumeOrProviderChange = true)
            }
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val startup = startupLocationPending
        startupLocationPending = false
        if (startup) {
            if (granted) resolveRealStartupLocation() else applyIpFallbackLocation()
        } else if (granted) {
            obtainCurrentLocation()
        } else {
            Toast.makeText(this, getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show()
            applyIpFallbackLocation()
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
        setupScrollAwareBottomNav()
        setupBackPress()
        observeState()
        preloadSavedForecastCaches()
        wasLocationEnabled = LocationResolver.hasLocationPermission(this) && LocationResolver.isLocationEnabled(this)
        val locationFilter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, locationProviderReceiver, locationFilter, ContextCompat.RECEIVER_EXPORTED)
        resolveInitialLocation()
    }

    override fun onResume() {
        super.onResume()
        refreshLocationIfNeeded(fromResumeOrProviderChange = true)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(locationProviderReceiver) }
        super.onDestroy()
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

            val lp = binding.bottomNavBar.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.bottomMargin = systemBars.bottom + (12 * resources.displayMetrics.density).toInt()
                binding.bottomNavBar.layoutParams = lp
            }
            insets
        }
    }

    private fun setupChildViews(initialTab: Tab) {
        forecastBinding = LayoutForecastBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        savedBinding = LayoutSavedLocationsBinding.inflate(layoutInflater, binding.fragmentContainer, false)
        settingsBinding = LayoutSettingsBinding.inflate(layoutInflater, binding.fragmentContainer, false)

        forecastBinding.root.visibility = if (initialTab == Tab.FORECAST) View.VISIBLE else View.GONE
        savedBinding.root.visibility = if (initialTab == Tab.SAVED) View.VISIBLE else View.GONE
        settingsBinding.root.visibility = if (initialTab == Tab.SETTINGS) View.VISIBLE else View.GONE

        binding.fragmentContainer.addView(forecastBinding.root)
        binding.fragmentContainer.addView(savedBinding.root)
        binding.fragmentContainer.addView(settingsBinding.root)

        searchBinding = LayoutSearchBinding.inflate(layoutInflater, binding.searchContainer, false)
        binding.searchContainer.addView(searchBinding.root)

        val fv = ForecastView(forecastBinding)
        forecastBinder = ForecastBinder(fv)

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
        if (LocationResolver.hasLocationPermission(this)) {
            resolveRealStartupLocation(retryOnEmpty = true)
        } else {
            // Ask first; IP geolocation is used only if the user denies.
            startupLocationPending = true
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun refreshLocationIfNeeded(fromResumeOrProviderChange: Boolean) {
        if (!LocationResolver.hasLocationPermission(this)) return
        val enabled = LocationResolver.isLocationEnabled(this)
        val justEnabled = enabled && !wasLocationEnabled
        wasLocationEnabled = enabled
        if (!enabled) return
        if (fromResumeOrProviderChange && !justEnabled && locationResolveJob?.isActive == true) return
        val cur = viewModel.state.value.detectedCurrentLocation
        val needsRealLocation = cur == null || cur.id.startsWith("ip_") || cur.id == LocationResolver.FALLBACK_DALAT.id
        if (!fromResumeOrProviderChange || justEnabled || needsRealLocation) {
            resolveRealStartupLocation(retryOnEmpty = justEnabled)
        }
    }

    private fun resolveRealStartupLocation(retryOnEmpty: Boolean = false) {
        locationResolveJob?.cancel()
        locationResolveJob = lifecycleScope.launch {
            var real = LocationResolver.resolveRealLocation(this@MainActivity)
            if (real == null && retryOnEmpty && LocationResolver.isLocationEnabled(this@MainActivity)) {
                // Location providers can take a few seconds to warm up after enabling system Location.
                for (waitMs in longArrayOf(2_000L, 5_000L)) {
                    delay(waitMs)
                    if (!LocationResolver.isLocationEnabled(this@MainActivity)) break
                    real = LocationResolver.resolveRealLocation(this@MainActivity)
                    if (real != null) break
                }
            }
            if (real != null) {
                locationDisabledToastShown = false
                applyDetectedLocation(real)
            } else {
                // Permission granted but no fix (location services off / no signal): no IP guess.
                if (!locationDisabledToastShown) {
                    locationDisabledToastShown = true
                    Toast.makeText(this@MainActivity, getString(R.string.location_services_disabled), Toast.LENGTH_SHORT).show()
                }
                val repository = (application as WeatherApplication).repository
                val hasExplicit = withContext(Dispatchers.IO) { repository.hasSelectedLocation() }
                if (!hasExplicit && viewModel.state.value.forecast == null) {
                    viewModel.showFallbackLocation(LocationResolver.FALLBACK_DALAT)
                }
            }
        }
    }

    private fun applyIpFallbackLocation() {
        lifecycleScope.launch {
            applyDetectedLocation(LocationResolver.resolveIpFallback())
        }
    }

    private suspend fun applyDetectedLocation(detected: LocationItem) {
        val repository = (application as WeatherApplication).repository
        val previousDetected = viewModel.state.value.detectedCurrentLocation
        viewModel.setDetectedCurrentLocation(detected)
        val hasExplicit = withContext(Dispatchers.IO) { repository.hasSelectedLocation() }
        val settings = withContext(Dispatchers.IO) { repository.getSettings() }
        val currentSelected = viewModel.state.value.selectedLocation
        val isAutoSelected = currentSelected == null ||
            currentSelected.isCurrentLocation ||
            currentSelected.id.startsWith("ip_") ||
            currentSelected.id.startsWith("gps_") ||
            currentSelected.id.startsWith("current_") ||
            (currentSelected.id == LocationResolver.FALLBACK_DALAT.id && previousDetected == null)
        if (!hasExplicit || settings.useCurrentLocation || isAutoSelected) {
            viewModel.selectLocation(detected)
        }
    }

    private var isBottomNavHidden = false
    private var isScrubbingNav = false
    private var navIndicatorAnimator: android.animation.ValueAnimator? = null

    private fun setupScrollAwareBottomNav() {
        val density = resources.displayMetrics.density
        val hideDistance = 160f * density

        val showNav = {
            if (isBottomNavHidden) {
                isBottomNavHidden = false
                binding.bottomNavBar.animate().cancel()
                if (viewModel.state.value.settings.reduceMotion) {
                    binding.bottomNavBar.translationY = 0f
                    binding.bottomNavBar.alpha = 1f
                } else {
                    binding.bottomNavBar.animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
            }
        }

        val hideNav = {
            if (!isBottomNavHidden && !isScrubbingNav) {
                isBottomNavHidden = true
                binding.bottomNavBar.animate().cancel()
                if (viewModel.state.value.settings.reduceMotion) {
                    binding.bottomNavBar.translationY = hideDistance
                    binding.bottomNavBar.alpha = 0f
                } else {
                    binding.bottomNavBar.animate()
                        .translationY(hideDistance)
                        .alpha(0f)
                        .setDuration(220)
                        .setInterpolator(AccelerateInterpolator())
                        .start()
                }
            }
        }

        forecastBinding.forecastScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val dy = scrollY - oldScrollY
            val child = forecastBinding.forecastScrollView.getChildAt(0)
            val atBottom = child != null && (scrollY + forecastBinding.forecastScrollView.height >= child.height - (48 * density).toInt())
            if (dy < -14 || scrollY < (50 * density).toInt() || atBottom) {
                showNav()
            } else if (dy > 14 && scrollY > (100 * density).toInt()) {
                hideNav()
            }
        }
    }

    private fun tabViewFor(tab: Tab): LinearLayout = when (tab) {
        Tab.FORECAST -> binding.tabForecast
        Tab.SAVED -> binding.tabSaved
        Tab.SETTINGS -> binding.tabSettings
    }

    private fun positionNavIndicator(tab: Tab, immediate: Boolean) {
        val target = tabViewFor(tab)
        val targetWidth = target.width
        val targetX = target.left.toFloat()
        if (targetWidth <= 0) return

        val lp = binding.navActiveIndicator.layoutParams
        navIndicatorAnimator?.cancel()
        if (immediate) {
            lp.width = targetWidth
            binding.navActiveIndicator.layoutParams = lp
            binding.navActiveIndicator.translationX = targetX
        } else {
            val startX = binding.navActiveIndicator.translationX
            val startW = if (lp.width > 0) lp.width else targetWidth
            navIndicatorAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200L
                interpolator = DecelerateInterpolator()
                addUpdateListener { va ->
                    val f = va.animatedFraction
                    binding.navActiveIndicator.translationX = startX + (targetX - startX) * f
                    val wLp = binding.navActiveIndicator.layoutParams
                    wLp.width = (startW + (targetWidth - startW) * f).toInt()
                    binding.navActiveIndicator.layoutParams = wLp
                }
                start()
            }
        }
    }

    private fun updateIndicatorForScrub(localX: Float) {
        navIndicatorAnimator?.cancel()
        val tabs = listOf(binding.tabForecast, binding.tabSaved, binding.tabSettings)
        val firstCenter = tabs.first().let { it.left + it.width * 0.5f }
        val lastCenter = tabs.last().let { it.left + it.width * 0.5f }
        val clampedCenterX = localX.coerceIn(firstCenter, lastCenter)

        var leftTab = tabs.first()
        var rightTab = tabs.last()
        for (i in 0 until tabs.lastIndex) {
            val c1 = tabs[i].left + tabs[i].width * 0.5f
            val c2 = tabs[i + 1].left + tabs[i + 1].width * 0.5f
            if (clampedCenterX in c1..c2) {
                leftTab = tabs[i]
                rightTab = tabs[i + 1]
                break
            }
        }

        val c1 = leftTab.left + leftTab.width * 0.5f
        val c2 = rightTab.left + rightTab.width * 0.5f
        val frac = if (c2 > c1) ((clampedCenterX - c1) / (c2 - c1)).coerceIn(0f, 1f) else 0f
        val interpWidth = (leftTab.width + (rightTab.width - leftTab.width) * frac).toInt()
        val interpX = leftTab.left + (rightTab.left - leftTab.left) * frac

        val lp = binding.navActiveIndicator.layoutParams
        lp.width = interpWidth
        binding.navActiveIndicator.layoutParams = lp
        binding.navActiveIndicator.translationX = interpX
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupBottomNavigation(initialTab: Tab) {
        binding.tabForecast.setOnClickListener { selectTab(Tab.FORECAST) }
        binding.tabSaved.setOnClickListener { selectTab(Tab.SAVED) }
        binding.tabSettings.setOnClickListener { selectTab(Tab.SETTINGS) }

        val touchSlop = android.view.ViewConfiguration.get(this).scaledTouchSlop
        var startRawX = 0f

        val scrubTouchListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    isScrubbingNav = false
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.rawX - startRawX)
                    if (!isScrubbingNav && dx > touchSlop * 0.35f) {
                        isScrubbingNav = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        binding.bottomNavBar.requestDisallowInterceptTouchEvent(true)
                        view.isPressed = false
                    }
                    if (isScrubbingNav) {
                        val contentLoc = IntArray(2)
                        binding.bottomNavContent.getLocationOnScreen(contentLoc)
                        val localX = (event.rawX - contentLoc[0]).coerceIn(0f, binding.bottomNavContent.width.toFloat())
                        updateIndicatorForScrub(localX)

                        val hoveredTab = when {
                            localX < binding.tabSaved.left -> Tab.FORECAST
                            localX < binding.tabSettings.left -> Tab.SAVED
                            else -> Tab.SETTINGS
                        }
                        if (hoveredTab != currentTab) {
                            binding.bottomNavBar.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            selectTab(hoveredTab, fromScrub = true)
                        }
                        true
                    } else {
                        false
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (isScrubbingNav) {
                        isScrubbingNav = false
                        view.isPressed = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        binding.bottomNavBar.requestDisallowInterceptTouchEvent(false)
                        positionNavIndicator(currentTab, immediate = viewModel.state.value.settings.reduceMotion)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        binding.tabForecast.setOnTouchListener(scrubTouchListener)
        binding.tabSaved.setOnTouchListener(scrubTouchListener)
        binding.tabSettings.setOnTouchListener(scrubTouchListener)
        binding.bottomNavBar.setOnTouchListener(scrubTouchListener)

        selectTab(initialTab)
        binding.bottomNavContent.post {
            positionNavIndicator(currentTab, immediate = true)
        }
    }

    private fun selectTab(tab: Tab, fromScrub: Boolean = false) {
        val oldTab = previousTab
        currentTab = tab
        previousTab = tab

        val reduceMotion = viewModel.state.value.settings.reduceMotion
        val density = resources.displayMetrics.density

        if (isBottomNavHidden) {
            isBottomNavHidden = false
            binding.bottomNavBar.animate().cancel()
            binding.bottomNavBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        // 3-Tab Liquid Glass Capsule Pill Styling
        fun styleTabItem(container: LinearLayout, icon: ImageView, text: TextView, isSelected: Boolean) {
            container.isSelected = isSelected
            text.visibility = View.VISIBLE
            if (isSelected) {
                icon.imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
                text.setTextColor(0xFFFFFFFF.toInt())
                text.alpha = 1f
            } else {
                icon.imageTintList = ColorStateList.valueOf(0x99FFFFFF.toInt())
                text.setTextColor(0x99FFFFFF.toInt())
                text.alpha = 0.85f
            }
        }

        styleTabItem(binding.tabForecast, binding.ivTabForecast, binding.tvTabForecast, tab == Tab.FORECAST)
        styleTabItem(binding.tabSaved, binding.ivTabSaved, binding.tvTabSaved, tab == Tab.SAVED)
        styleTabItem(binding.tabSettings, binding.ivTabSettings, binding.tvTabSettings, tab == Tab.SETTINGS)

        if (!fromScrub) {
            val targetView = tabViewFor(tab)
            if (targetView.width > 0) {
                positionNavIndicator(tab, immediate = oldTab == null || oldTab == tab || reduceMotion)
            } else {
                binding.bottomNavContent.post { positionNavIndicator(tab, immediate = true) }
            }
        }

        binding.bottomNavContent.post {
            val targetView = tabViewFor(tab)
            val scrollX = (targetView.left - (binding.bottomNavBar.width - targetView.width) / 2).coerceAtLeast(0)
            binding.bottomNavBar.smoothScrollTo(scrollX, 0)
        }

        fun getViewForTab(t: Tab): View = when (t) {
            Tab.FORECAST -> forecastBinding.root
            Tab.SAVED -> savedBinding.root
            Tab.SETTINGS -> settingsBinding.root
        }

        val targetView = getViewForTab(tab)
        val outgoingView = if (oldTab != null && oldTab != tab) getViewForTab(oldTab) else null

        if (oldTab == null || reduceMotion || outgoingView == null) {
            forecastBinding.root.visibility = if (tab == Tab.FORECAST) View.VISIBLE else View.GONE
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
            binding.fragmentContainer.visibility = View.INVISIBLE
            binding.searchContainer.visibility = View.VISIBLE
            binding.bottomNavBar.visibility = View.GONE
        } else {
            binding.fragmentContainer.animate().cancel()
            binding.fragmentContainer.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction { binding.fragmentContainer.visibility = View.INVISIBLE }
                .start()

            binding.searchContainer.animate().cancel()
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchContainer.alpha = 0f
            binding.searchContainer.translationY = 40f * density
            binding.searchContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
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
            binding.fragmentContainer.alpha = 1f
            binding.fragmentContainer.visibility = View.VISIBLE
            binding.bottomNavBar.visibility = View.VISIBLE
        } else {
            binding.fragmentContainer.animate().cancel()
            binding.fragmentContainer.visibility = View.VISIBLE
            binding.fragmentContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start()

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
                        onRefresh = {
                            viewModel.refresh(force = true)
                            refreshLocationIfNeeded(fromResumeOrProviderChange = true)
                        },
                        onSelectCity = { location -> viewModel.selectLocation(location) }
                    )

                    // Keep active and current-location forecasts in savedForecasts map
                    state.forecast?.let { savedForecasts[it.location.id] = it }
                    val cur = state.detectedCurrentLocation
                    if (cur != null && state.currentLocationForecast != null) {
                        savedForecasts[cur.id] = state.currentLocationForecast
                    }

                    savedBinder.bind(state, savedForecasts)
                    savedBinding.emptyLocations.visibility =
                        if (state.savedLocations.isEmpty() && cur == null) View.VISIBLE else View.GONE
                    savedBinding.cardCurrentLocation.visibility = View.GONE

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
                    com.k3weather.app.widget.WeatherWidgetUpdater.updateAllWidgets(this@MainActivity)
                }
            }
        }
    }

    private fun applyColorScheme(colorScheme: AppColorScheme) {
        if (activeColorScheme == colorScheme) return
        activeColorScheme = colorScheme

        val accentColor = colorScheme.primaryAccent
        val tintList = ColorStateList.valueOf(accentColor)

        savedBinding.addCityButton.backgroundTintList = null
        savedBinding.ivCurrentLocIcon.imageTintList = tintList
        savedBinding.tvCurrentLocLabel.setTextColor(accentColor)
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
        val oldSaved = savedBinding.root
        val oldSettings = settingsBinding.root
        val oldSearch = searchBinding.root

        setupChildViews(currentTab)
        setupScrollAwareBottomNav()

        binding.fragmentContainer.removeView(oldForecast)
        binding.fragmentContainer.removeView(oldSaved)
        binding.fragmentContainer.removeView(oldSettings)
        binding.searchContainer.removeView(oldSearch)

        // Update bottom navigation bar labels in new locale
        binding.tvTabForecast.text = getString(R.string.tab_forecast)
        binding.tvTabSaved.text = getString(R.string.tab_saved)
        binding.tvTabSettings.text = getString(R.string.tab_settings)
        binding.bottomNavContent.post {
            positionNavIndicator(currentTab, immediate = true)
        }

        // Re-bind current data in new locale
        val state = viewModel.state.value
        settingsBinder.bind(state.settings)
        forecastBinder.bind(
            state = state,
            onSearch = { openSearch(SearchSource.FORECAST) },
            onRefresh = {
                viewModel.refresh(force = true)
                refreshLocationIfNeeded(fromResumeOrProviderChange = true)
            },
            onSelectCity = { location -> viewModel.selectLocation(location) }
        )
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
        if (LocationResolver.hasLocationPermission(this)) {
            obtainCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun obtainCurrentLocation() {
        lifecycleScope.launch {
            val real = LocationResolver.resolveRealLocation(this@MainActivity)
            if (real != null) {
                viewModel.setDetectedCurrentLocation(real)
                viewModel.selectLocation(real)
                selectTab(Tab.FORECAST)
                Toast.makeText(this@MainActivity, getString(R.string.location_updated, real.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, getString(R.string.location_services_disabled), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val KEY_ACTIVE_TAB = "key_active_tab"
    }
}
