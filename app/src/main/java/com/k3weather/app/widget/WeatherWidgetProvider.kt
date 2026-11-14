package com.k3weather.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.k3weather.app.MainActivity
import com.k3weather.app.R
import com.k3weather.app.core.model.AppSettings
import com.k3weather.app.core.model.ForecastData
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.data.WeatherRepositoryImpl
import com.k3weather.app.data.local.WeatherPreferences
import com.k3weather.app.data.mapper.WeatherCodeMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class WidgetSize {
    SMALL, MEDIUM, LARGE
}

open class BaseWeatherWidgetProvider(
    private val defaultSize: WidgetSize
) : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val targets = appWidgetIds.map { appWidgetId ->
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            appWidgetId to resolveWidgetSize(options, defaultSize)
        }
        val pendingResult = runCatching { goAsync() }.getOrNull()
        WeatherWidgetUpdater.scope.launch {
            try {
                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    WeatherWidgetUpdater.updateWidgetsSuspend(context, appWidgetManager, targets, allowNetwork = true)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val effectiveSize = resolveWidgetSize(newOptions, defaultSize)
        val pendingResult = runCatching { goAsync() }.getOrNull()
        WeatherWidgetUpdater.scope.launch {
            try {
                withTimeoutOrNull(BROADCAST_TIMEOUT_MS) {
                    WeatherWidgetUpdater.updateWidgetsSuspend(
                        context,
                        appWidgetManager,
                        listOf(appWidgetId to effectiveSize),
                        allowNetwork = true
                    )
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val BROADCAST_TIMEOUT_MS = 8_500L

        fun resolveWidgetSize(options: Bundle?, fallback: WidgetSize): WidgetSize {
            if (options == null) return fallback
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            if (minWidth == 0 && minHeight == 0) return fallback
            return when {
                minHeight >= 200 && minWidth >= 200 -> WidgetSize.LARGE
                minWidth >= 200 -> WidgetSize.MEDIUM
                else -> WidgetSize.SMALL
            }
        }
    }
}

class WeatherWidgetSmallProvider : BaseWeatherWidgetProvider(WidgetSize.SMALL)
class WeatherWidgetMediumProvider : BaseWeatherWidgetProvider(WidgetSize.MEDIUM)
class WeatherWidgetLargeProvider : BaseWeatherWidgetProvider(WidgetSize.LARGE)

object WeatherWidgetUpdater {
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val widgetSeq = ConcurrentHashMap<Int, AtomicLong>()
    private val widgetMutex = ConcurrentHashMap<Int, Mutex>()
    private var refreshAllJob: Job? = null

    fun updateAllWidgets(context: Context, allowNetwork: Boolean = false) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val providers = listOf(
            ComponentName(appContext, WeatherWidgetSmallProvider::class.java) to WidgetSize.SMALL,
            ComponentName(appContext, WeatherWidgetMediumProvider::class.java) to WidgetSize.MEDIUM,
            ComponentName(appContext, WeatherWidgetLargeProvider::class.java) to WidgetSize.LARGE
        )
        val targets = mutableListOf<Pair<Int, WidgetSize>>()
        for ((component, fallbackSize) in providers) {
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                val options = manager.getAppWidgetOptions(id)
                val effectiveSize = BaseWeatherWidgetProvider.resolveWidgetSize(options, fallbackSize)
                targets.add(id to effectiveSize)
            }
        }
        if (targets.isEmpty()) return

        refreshAllJob?.cancel()
        refreshAllJob = scope.launch {
            updateWidgetsSuspend(appContext, manager, targets, allowNetwork = allowNetwork)
        }
    }

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        size: WidgetSize,
        allowNetwork: Boolean = true
    ) {
        scope.launch {
            updateWidgetsSuspend(context, appWidgetManager, listOf(appWidgetId to size), allowNetwork = allowNetwork)
        }
    }

    internal suspend fun updateWidgetsSuspend(
        context: Context,
        appWidgetManager: AppWidgetManager,
        targets: List<Pair<Int, WidgetSize>>,
        allowNetwork: Boolean
    ) {
        if (targets.isEmpty()) return
        val appContext = context.applicationContext
        val tokens = targets.associate { (id, _) ->
            id to widgetSeq.getOrPut(id) { AtomicLong(0L) }.incrementAndGet()
        }

        val prefs = WeatherPreferences(appContext)
        val repo = WeatherRepositoryImpl(prefs)
        val settings = repo.getSettings()
        val locContext = localizedContext(appContext, settings.language)

        val targetLocation = repo.getDetectedCurrentLocation() ?: repo.getSelectedLocation()
        val ownCached = repo.getCachedForecast(targetLocation.id)
        val selectedCached = if (ownCached == null) {
            val selected = repo.getSelectedLocation()
            if (selected.id != targetLocation.id) repo.getCachedForecast(selected.id) else null
        } else {
            null
        }
        val initialForecast = ownCached ?: selectedCached

        if (initialForecast != null) {
            for ((widgetId, size) in targets) {
                val token = tokens[widgetId] ?: continue
                val mutex = widgetMutex.getOrPut(widgetId) { Mutex() }
                mutex.withLock {
                    if (widgetSeq[widgetId]?.get() == token) {
                        val views = buildRemoteViews(locContext, size, initialForecast.location, initialForecast, settings)
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            }
        }

        if (!allowNetwork) return

        repo.getForecast(targetLocation, forceRefresh = false).onSuccess { fresh ->
            for ((widgetId, size) in targets) {
                val token = tokens[widgetId] ?: continue
                val mutex = widgetMutex.getOrPut(widgetId) { Mutex() }
                mutex.withLock {
                    if (widgetSeq[widgetId]?.get() == token) {
                        val freshViews = buildRemoteViews(locContext, size, fresh.location, fresh, settings)
                        appWidgetManager.updateAppWidget(widgetId, freshViews)
                    }
                }
            }
        }
    }

    fun buildRemoteViews(
        context: Context,
        size: WidgetSize,
        location: LocationItem,
        forecast: ForecastData,
        settings: AppSettings
    ): RemoteViews {
        val layoutRes = when (size) {
            WidgetSize.SMALL -> R.layout.widget_weather_small
            WidgetSize.MEDIUM -> R.layout.widget_weather_medium
            WidgetSize.LARGE -> R.layout.widget_weather_large
        }
        val views = RemoteViews(context.packageName, layoutRes)
        val current = forecast.current
        val tempUnit = settings.tempUnit
        val windUnit = settings.windSpeedUnit
        val zoneId = runCatching { ZoneId.of(forecast.location.timezone) }.getOrDefault(ZoneId.systemDefault())

        // Dynamic Liquid Glass sky background matched to condition and day/night
        val bgRes = when {
            current.weatherCode in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) ->
                R.drawable.bg_widget_sky_rain
            current.weatherCode in listOf(45, 48) ->
                R.drawable.bg_widget_sky_fog
            !current.isDay ->
                R.drawable.bg_widget_sky_night
            current.weatherCode in listOf(2, 3) ->
                R.drawable.bg_widget_sky_cloudy
            else ->
                R.drawable.bg_widget_sky_day
        }
        views.setInt(R.id.widgetRoot, "setBackgroundResource", bgRes)

        // Tap anywhere on widget to launch MainActivity
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

        // Core Hero Data (Small, Medium, Large)
        views.setTextViewText(R.id.widgetCity, location.name)
        views.setViewVisibility(
            R.id.widgetLocationPin,
            if (location.isCurrentLocation) View.VISIBLE else View.GONE
        )
        views.setTextViewText(R.id.widgetTemp, tempUnit.format(current.temperatureC))
        views.setImageViewResource(
            R.id.widgetIcon,
            WeatherCodeMapper.getIcon(current.weatherCode, current.isDay)
        )
        views.setTextViewText(
            R.id.widgetCondition,
            WeatherCodeMapper.getLocalizedLabel(context, current.weatherCode)
        )
        views.setTextViewText(
            R.id.widgetHighLow,
            context.getString(
                R.string.temp_range_slash_format,
                tempUnit.format(current.minTempC),
                tempUnit.format(current.maxTempC)
            )
        )

        // Hourly 5-column strip (Medium & Large)
        if (size == WidgetSize.MEDIUM || size == WidgetSize.LARGE) {
            val timeIds = intArrayOf(
                R.id.widgetHourTime1, R.id.widgetHourTime2, R.id.widgetHourTime3,
                R.id.widgetHourTime4, R.id.widgetHourTime5
            )
            val iconIds = intArrayOf(
                R.id.widgetHourIcon1, R.id.widgetHourIcon2, R.id.widgetHourIcon3,
                R.id.widgetHourIcon4, R.id.widgetHourIcon5
            )
            val tempIds = intArrayOf(
                R.id.widgetHourTemp1, R.id.widgetHourTemp2, R.id.widgetHourTemp3,
                R.id.widgetHourTemp4, R.id.widgetHourTemp5
            )
            val hours = forecast.hourly.take(5)
            val nowLabel = context.getString(R.string.time_now)
            val nowEpochSec = Instant.now().epochSecond
            val todayDate = LocalDate.now(zoneId)
            for (i in 0 until 5) {
                val h = hours.getOrNull(i) ?: continue
                val isCurrentHour = i == 0 && !forecast.isStale && kotlin.math.abs(nowEpochSec - h.timeEpochSeconds) < 5400L
                val timeStr = if (isCurrentHour) {
                    nowLabel
                } else {
                    runCatching {
                        val zdt = Instant.ofEpochSecond(h.timeEpochSeconds).atZone(zoneId)
                        val pattern = if (zdt.toLocalDate() == todayDate) "HH:mm" else "d/M HH:mm"
                        zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
                    }.getOrDefault(h.timeLabel)
                }
                views.setTextViewText(timeIds[i], timeStr)
                views.setImageViewResource(iconIds[i], WeatherCodeMapper.getIcon(h.weatherCode, h.isDay))
                views.setTextViewText(tempIds[i], tempUnit.format(h.temperatureC))
            }
        }

        // Large Widget Extra Details & 3-Day Forecast
        if (size == WidgetSize.LARGE) {
            val isVi = context.resources.configuration.locales[0]?.language == "vi"
            val compass = degreesToCompass(isVi, current.windDirectionDeg)
            val windSpeed = windUnit.format(current.windSpeedKmh)
            val detailsText = if (isVi) {
                "Gió $compass $windSpeed  •  Độ ẩm ${current.humidityPercent}%  •  UV ${current.uvIndexInt}"
            } else {
                "$compass $windSpeed  •  Humidity ${current.humidityPercent}%  •  UV ${current.uvIndexInt}"
            }
            views.setTextViewText(R.id.widgetDetailsSummary, detailsText)

            val dayLabelIds = intArrayOf(R.id.widgetDayLabel1, R.id.widgetDayLabel2, R.id.widgetDayLabel3)
            val dayIconIds = intArrayOf(R.id.widgetDayIcon1, R.id.widgetDayIcon2, R.id.widgetDayIcon3)
            val dayPopIds = intArrayOf(R.id.widgetDayPop1, R.id.widgetDayPop2, R.id.widgetDayPop3)
            val dayMinIds = intArrayOf(R.id.widgetDayMin1, R.id.widgetDayMin2, R.id.widgetDayMin3)
            val dayMaxIds = intArrayOf(R.id.widgetDayMax1, R.id.widgetDayMax2, R.id.widgetDayMax3)

            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val todayDate = LocalDate.now(zoneId)
            val days = forecast.daily.take(3)
            for (i in 0 until 3) {
                val d = days.getOrNull(i) ?: continue
                val parsedDate = runCatching { LocalDate.parse(d.dateIso) }.getOrNull()
                val dayName = if (parsedDate != null && parsedDate == todayDate) {
                    context.getString(R.string.day_today)
                } else {
                    parsedDate?.format(DateTimeFormatter.ofPattern("EEE", locale)) ?: d.dayLabel
                }
                views.setTextViewText(dayLabelIds[i], dayName)
                views.setImageViewResource(dayIconIds[i], WeatherCodeMapper.getIcon(d.weatherCode, isDay = true))
                views.setTextViewText(
                    dayPopIds[i],
                    if (d.precipitationProbabilityMax >= 20) "${d.precipitationProbabilityMax}%" else ""
                )
                views.setTextViewText(dayMinIds[i], tempUnit.format(d.minTempC))
                views.setTextViewText(dayMaxIds[i], tempUnit.format(d.maxTempC))
            }
        }

        return views
    }

    private fun degreesToCompass(isVi: Boolean, deg: Int): String {
        val dirs = if (isVi) {
            arrayOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")
        } else {
            arrayOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
        }
        val idx = (((deg % 360) + 360) % 360 + 22) / 45 % 8
        return dirs[idx]
    }

    private fun localizedContext(context: Context, lang: String): Context {
        val locale = when (lang) {
            "vi" -> Locale("vi")
            "en" -> Locale("en")
            else -> Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
