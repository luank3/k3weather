package com.k3weather.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.k3weather.app.core.model.*
import com.k3weather.app.core.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WeatherUiState(
    val selectedLocation: LocationItem? = null,
    val detectedCurrentLocation: LocationItem? = null,
    val currentLocationForecast: ForecastData? = null,
    val forecast: ForecastData? = null,
    val savedLocations: List<LocationItem> = emptyList(),
    val searchResults: List<LocationItem> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val loading: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
    val searchError: String? = null
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    initialSettings: AppSettings = AppSettings()
) : ViewModel() {
    private val _state = MutableStateFlow(WeatherUiState(settings = initialSettings))
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var forecastJob: Job? = null
    private var forecastRequestSeq: Long = 0L
    private var detectedLocationJob: Job? = null
    private var detectedLocationSeq: Long = 0L

    init {
        forecastJob = viewModelScope.launch {
            val requestId = ++forecastRequestSeq
            val settings = repository.getSettings()
            val saved = repository.getSavedLocations()
            val detected = repository.getDetectedCurrentLocation()
            val selected = repository.getSelectedLocation()
            val cachedSelected = repository.getCachedForecast(selected.id)
            val cachedDetected = detected?.let { repository.getCachedForecast(it.id) }
            if (requestId != forecastRequestSeq) return@launch
            _state.value = _state.value.copy(
                settings = settings,
                savedLocations = saved,
                detectedCurrentLocation = detected,
                currentLocationForecast = cachedDetected,
                selectedLocation = selected,
                forecast = cachedSelected
            )
            loadForecast(selected, force = false, requestId = requestId)
        }
    }

    fun setDetectedCurrentLocation(location: LocationItem) {
        val seq = ++detectedLocationSeq
        detectedLocationJob?.cancel()
        val sameLocation = _state.value.currentLocationForecast?.location?.id == location.id
        _state.value = _state.value.copy(
            detectedCurrentLocation = location,
            currentLocationForecast = if (sameLocation) _state.value.currentLocationForecast else null
        )
        detectedLocationJob = viewModelScope.launch {
            repository.setDetectedCurrentLocation(location)
            if (seq != detectedLocationSeq) return@launch
            val cached = repository.getCachedForecast(location.id)
            if (seq != detectedLocationSeq) return@launch
            if (cached != null && _state.value.detectedCurrentLocation?.id == location.id) {
                _state.value = _state.value.copy(currentLocationForecast = cached)
            }
            repository.getForecast(location, forceRefresh = false).onSuccess { forecast ->
                if (seq == detectedLocationSeq && _state.value.detectedCurrentLocation?.id == location.id) {
                    _state.value = _state.value.copy(currentLocationForecast = forecast)
                }
            }
        }
    }

    fun returnToCurrentLocation() {
        val current = _state.value.detectedCurrentLocation ?: return
        selectLocation(current)
    }

    fun refresh(force: Boolean = true) {
        val location = _state.value.selectedLocation ?: return
        startForecastLoad(location, force)
    }

    private fun startForecastLoad(location: LocationItem, force: Boolean, persistSelection: Boolean = false) {
        val requestId = ++forecastRequestSeq
        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            if (persistSelection) {
                repository.setSelectedLocation(location)
            }
            loadForecast(location, force, requestId)
        }
    }

    private suspend fun loadForecast(location: LocationItem, force: Boolean = false, requestId: Long) {
        val immediateCache = repository.getCachedForecast(location.id)
        if (requestId != forecastRequestSeq) return

        val currentForecast = _state.value.forecast
        val matchingExisting = if (currentForecast?.location?.id == location.id) currentForecast else null
        _state.value = _state.value.copy(
            selectedLocation = location,
            forecast = immediateCache ?: matchingExisting,
            loading = true,
            error = null
        )
        repository.getForecast(location, force).fold(
            onSuccess = { forecast ->
                if (requestId != forecastRequestSeq) return
                val detectedId = _state.value.detectedCurrentLocation?.id
                val isCurrent = forecast.location.id == detectedId ||
                    (location.isCurrentLocation && detectedId == null)
                _state.value = _state.value.copy(
                    selectedLocation = forecast.location,
                    forecast = forecast,
                    loading = false,
                    error = null,
                    currentLocationForecast = if (isCurrent) forecast else _state.value.currentLocationForecast
                )
            },
            onFailure = { error ->
                if (requestId != forecastRequestSeq) return
                val cached = repository.getCachedForecast(location.id)?.copy(isStale = true)
                if (requestId != forecastRequestSeq) return
                _state.value = _state.value.copy(
                    selectedLocation = location,
                    forecast = cached,
                    loading = false,
                    error = error.message ?: "Unable to load forecast"
                )
            }
        )
    }

    fun selectLocation(location: LocationItem) {
        startForecastLoad(location, force = false, persistSelection = true)
    }

    /** Displays a temporary fallback forecast without marking it as an explicit user choice. */
    fun showFallbackLocation(location: LocationItem) {
        startForecastLoad(location, force = false, persistSelection = false)
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList(), searching = false, searchError = null)
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            _state.value = _state.value.copy(searching = true, searchError = null)
            repository.searchLocations(query).fold(
                onSuccess = { _state.value = _state.value.copy(searchResults = it, searching = false) },
                onFailure = { _state.value = _state.value.copy(searchResults = emptyList(), searching = false, searchError = it.message ?: "Search failed") }
            )
        }
    }

    fun saveLocation(location: LocationItem) {
        viewModelScope.launch {
            repository.saveLocation(location)
            _state.value = _state.value.copy(savedLocations = repository.getSavedLocations())
        }
    }

    fun removeLocation(location: LocationItem) {
        viewModelScope.launch {
            repository.removeLocation(location.id)
            _state.value = _state.value.copy(savedLocations = repository.getSavedLocations())
        }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(settings)
            _state.value = _state.value.copy(settings = settings)
        }
    }

    fun clearCache(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearCache()
            _state.value.selectedLocation?.let { startForecastLoad(it, force = true) }
            onComplete()
        }
    }
}

class WeatherViewModelFactory(
    private val repository: WeatherRepository,
    private val initialSettings: AppSettings
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            return WeatherViewModel(repository, initialSettings) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
