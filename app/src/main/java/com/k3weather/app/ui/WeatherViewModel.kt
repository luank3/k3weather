package com.k3weather.app.ui

import androidx.lifecycle.ViewModel
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

    init {
        viewModelScope.launch {
            val settings = repository.getSettings()
            val saved = repository.getSavedLocations()
            val detected = repository.getDetectedCurrentLocation()
            val selected = repository.getSelectedLocation()
            val cachedSelected = repository.getCachedForecast(selected.id)?.copy(isStale = false)
            val cachedDetected = detected?.let { repository.getCachedForecast(it.id)?.copy(isStale = false) }
            _state.value = _state.value.copy(
                settings = settings,
                savedLocations = saved,
                detectedCurrentLocation = detected,
                currentLocationForecast = cachedDetected,
                selectedLocation = selected,
                forecast = cachedSelected
            )
            loadForecast(selected, force = false)
        }
    }

    fun setDetectedCurrentLocation(location: LocationItem) {
        _state.value = _state.value.copy(detectedCurrentLocation = location)
        viewModelScope.launch {
            repository.setDetectedCurrentLocation(location)
            val cached = repository.getCachedForecast(location.id)?.copy(isStale = false)
            if (cached != null && _state.value.currentLocationForecast == null) {
                _state.value = _state.value.copy(currentLocationForecast = cached)
            }
            repository.getForecast(location, forceRefresh = false).onSuccess { forecast ->
                _state.value = _state.value.copy(currentLocationForecast = forecast)
            }
        }
    }

    fun returnToCurrentLocation() {
        val current = _state.value.detectedCurrentLocation ?: return
        selectLocation(current)
    }

    fun refresh(force: Boolean = true) {
        val location = _state.value.selectedLocation ?: return
        viewModelScope.launch { loadForecast(location, force) }
    }

    private suspend fun loadForecast(location: LocationItem, force: Boolean = false) {
        val immediateCache = repository.getCachedForecast(location.id)?.copy(isStale = false)
        _state.value = _state.value.copy(
            selectedLocation = location,
            forecast = immediateCache ?: _state.value.forecast,
            loading = true,
            error = null
        )
        repository.getForecast(location, force).fold(
            onSuccess = { forecast ->
                val isCurrent = location.isCurrentLocation || location.id == _state.value.detectedCurrentLocation?.id
                _state.value = _state.value.copy(
                    selectedLocation = forecast.location,
                    forecast = forecast,
                    loading = false,
                    currentLocationForecast = if (isCurrent) forecast else _state.value.currentLocationForecast
                )
            },
            onFailure = { error ->
                val cached = repository.getCachedForecast(location.id)
                _state.value = _state.value.copy(
                    forecast = cached,
                    loading = false,
                    error = error.message ?: "Unable to load forecast"
                )
            }
        )
    }

    fun selectLocation(location: LocationItem) {
        viewModelScope.launch {
            repository.setSelectedLocation(location)
            loadForecast(location, force = false)
        }
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
}
