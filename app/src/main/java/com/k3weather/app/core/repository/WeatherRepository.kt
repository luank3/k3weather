package com.k3weather.app.core.repository

import com.k3weather.app.core.model.AppSettings
import com.k3weather.app.core.model.ForecastData
import com.k3weather.app.core.model.LocationItem

interface WeatherRepository {
    suspend fun getForecast(location: LocationItem, forceRefresh: Boolean = false): Result<ForecastData>
    suspend fun getCachedForecast(locationId: String): ForecastData?
    suspend fun searchLocations(query: String): Result<List<LocationItem>>
    suspend fun getSavedLocations(): List<LocationItem>
    suspend fun saveLocation(location: LocationItem): Boolean
    suspend fun removeLocation(locationId: String): Boolean
    suspend fun getSelectedLocation(): LocationItem
    suspend fun setSelectedLocation(location: LocationItem)
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(settings: AppSettings)
}
