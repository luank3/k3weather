package com.k3weather.app

import android.app.Application
import com.k3weather.app.core.repository.WeatherRepository
import com.k3weather.app.data.WeatherRepositoryImpl
import com.k3weather.app.data.local.WeatherPreferences

class WeatherApplication : Application() {
    lateinit var preferences: WeatherPreferences
        private set
    lateinit var repository: WeatherRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        preferences = WeatherPreferences(this)
        repository = WeatherRepositoryImpl(preferences)
    }

    companion object {
        lateinit var instance: WeatherApplication
            private set
    }
}
