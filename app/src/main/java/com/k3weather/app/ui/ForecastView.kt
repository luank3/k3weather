package com.k3weather.app.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.data.mapper.WeatherCodeMapper
import com.k3weather.app.databinding.ItemDailyForecastBinding
import com.k3weather.app.databinding.ItemHourlyForecastBinding
import com.k3weather.app.databinding.LayoutForecastBinding
import com.k3weather.app.ui.common.HeroWeatherArtworkView
import com.k3weather.app.ui.common.MotionHelper
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ForecastView(private val binding: LayoutForecastBinding) {

    private var showDailyChart = true
    private var isInteractiveSetupDone = false
    private var isScrubbingDaily = false
    private var reduceMotion = false
    private var lastBoundCityId: String? = null
    private var dailyIndicatorAnimator: ValueAnimator? = null
    private var dailyBarUpdaters: List<() -> Unit> = emptyList()

    fun bind(
        state: WeatherUiState,
        onSearch: () -> Unit,
        onRefresh: () -> Unit,
        onSelectCity: (LocationItem) -> Unit,
        onToggleUnit: (() -> Unit)? = null
    ) {
        val context = binding.root.context
        val colorScheme = state.settings.colorScheme
        val accentColor = colorScheme.primaryAccent
        reduceMotion = state.settings.reduceMotion

        if (!isInteractiveSetupDone) {
            isInteractiveSetupDone = true
            MotionHelper.attachPressScale(binding.searchButton, pressedScale = 0.91f, hapticOnTap = true)
            setupDailySwitch()
        }

        binding.searchButton.setOnClickListener { onSearch() }
        binding.swipeRefresh.setColorSchemeColors(accentColor)
        binding.swipeRefresh.setProgressBackgroundColorSchemeColor(0xE60A1E2E.toInt())
        binding.swipeRefresh.setOnRefreshListener { onRefresh() }
        binding.swipeRefresh.isRefreshing = state.loading

        // Setup City Quick-Pills Header
        setupCityPills(state, onSelectCity)

        val forecast = state.forecast
        val loc = forecast?.location ?: state.selectedLocation ?: LocationItem(
            id = "default",
            name = "Da Nang",
            country = "Vietnam",
            latitude = 16.0678,
            longitude = 108.2208
        )
        val cityChanged = lastBoundCityId != null && lastBoundCityId != loc.id
        if (forecast != null) {
            lastBoundCityId = loc.id
        }

        // Location Hero Heading
        if (cityChanged) {
            MotionHelper.pulseTextChange(binding.cityName, loc.name, reduceMotion)
        } else {
            binding.cityName.text = loc.name
        }
        val isCurrentLoc = loc.isCurrentLocation || loc.id == state.detectedCurrentLocation?.id
        val subtitleText = when {
            isCurrentLoc && loc.region.isNotBlank() && !loc.region.equals(loc.name, ignoreCase = true) ->
                "${context.getString(R.string.my_location)} • ${loc.region}"
            isCurrentLoc -> context.getString(R.string.my_location)
            loc.region.isNotBlank() && !loc.region.equals(loc.name, ignoreCase = true) -> loc.region
            loc.country.isNotBlank() -> loc.country
            else -> ""
        }
        if (subtitleText.isNotBlank()) {
            binding.citySubtitle.visibility = View.VISIBLE
            binding.citySubtitle.text = subtitleText.uppercase(Locale.getDefault())
        } else {
            binding.citySubtitle.visibility = View.GONE
        }

        if (forecast == null) {
            binding.temperature.text = "--"
            binding.highLow.text = ""
            binding.condition.text = when {
                state.loading -> context.getString(R.string.forecast_loading)
                !state.error.isNullOrBlank() -> context.getString(R.string.forecast_unavailable)
                else -> ""
            }
            if (!state.error.isNullOrBlank() && !state.loading) {
                binding.tvForecastStatus.visibility = View.VISIBLE
                binding.tvForecastStatus.text = state.error
                binding.tvForecastStatus.setOnClickListener { onRefresh() }
            } else {
                binding.tvForecastStatus.visibility = View.GONE
                binding.tvForecastStatus.setOnClickListener(null)
            }
            binding.hourlyList.adapter = null
            binding.dailyRows.removeAllViews()
            dailyBarUpdaters = emptyList()
            setForecastSectionsVisibility(View.GONE)
            return
        }

        setForecastSectionsVisibility(View.VISIBLE)

        val current = forecast.current
        val tempUnit = state.settings.tempUnit
        val windUnit = state.settings.windSpeedUnit
        val locationZone = runCatching { ZoneId.of(forecast.location.timezone) }.getOrDefault(ZoneId.systemDefault())

        updateForecastStatusBanner(context, forecast, state.error, locationZone, onRefresh)

        // Natural Atmosphere Theme derived directly from weather code & day/night
        val effectiveTheme = when {
            !current.isDay -> HeroWeatherArtworkView.Theme.NIGHT
            current.weatherCode in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> HeroWeatherArtworkView.Theme.RAINY
            current.weatherCode in listOf(2, 3, 45, 48) -> HeroWeatherArtworkView.Theme.CLOUDY
            else -> HeroWeatherArtworkView.Theme.SUNNY
        }
        binding.heroWeatherArtwork.setTheme(effectiveTheme)
        binding.heroWeatherArtwork.setReduceMotion(state.settings.reduceMotion)

        // Hero Temperature & Concise Summary (animated on city switch)
        val newTemp = tempUnit.format(current.temperatureC)
        val conditionLabel = WeatherCodeMapper.getLocalizedLabel(context, current.weatherCode)
        val newRange = context.getString(
            R.string.temp_range_slash_format,
            tempUnit.format(current.minTempC),
            tempUnit.format(current.maxTempC)
        )
        if (cityChanged) {
            MotionHelper.pulseTextChange(binding.temperature, newTemp, reduceMotion)
            MotionHelper.pulseTextChange(binding.condition, conditionLabel, reduceMotion)
            MotionHelper.pulseTextChange(binding.highLow, newRange, reduceMotion)
        } else {
            binding.temperature.text = newTemp
            binding.condition.text = conditionLabel
            binding.highLow.text = newRange
        }

        // Hourly Forecast Ribbon (24-hour window)
        val hourlyWindow = forecast.hourly.take(24)
        val maxHourlyPop = hourlyWindow.maxOfOrNull { it.precipitationProbability } ?: 0
        binding.hourlyList.adapter = HourlyAdapter(
            items = forecast.hourly,
            unit = tempUnit,
            nowLabel = context.getString(R.string.time_now),
            accentColor = accentColor,
            zoneId = locationZone,
            isStale = forecast.isStale
        )

        // Data-backed Narrative Weather Description
        binding.tvWeatherDescription.text = generateNarrativeDescription(
            context = context,
            current = current,
            maxPop24h = maxHourlyPop,
            windUnit = windUnit,
            precipUnit = state.settings.precipitationUnit
        )

        // Multi-Day Forecast: Dual-Curve Chart & 7-Day Range List with Scrubbable Segmented Toggle
        binding.dailyTrendChart.setForecastDays(forecast.daily, tempUnit, locationZone)
        setupWeeklyForecast(forecast, tempUnit, colorScheme, locationZone)
        applyDailyViewMode(animate = false)

        // Compact 3-Column Metrics Grid
        val uvInt = current.uvIndexInt
        binding.tvUvValue.text = uvInt.toString()
        val uvCatRes = when {
            uvInt <= 2 -> R.string.uv_low
            uvInt <= 5 -> R.string.uv_moderate
            uvInt <= 7 -> R.string.uv_high
            uvInt <= 10 -> R.string.uv_very_high
            else -> R.string.uv_extreme
        }
        binding.tvUvCategory.text = context.getString(uvCatRes)

        // Feels Like
        binding.tvAqiValue.text = tempUnit.format(current.apparentTemperatureC)

        // Humidity
        binding.tvHumidityValue.text = "${current.humidityPercent}%"

        // Wind
        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        val compass = degreesToCompass(context, current.windDirectionDeg)
        binding.tvWindDirection.text = if (isVi) "Gió $compass" else "$compass wind"
        binding.tvWindValue.text = windUnit.format(current.windSpeedKmh)

        // Pressure
        binding.tvPressureNote.text = state.settings.pressureUnit.format(current.surfacePressureHpa)

        // Visibility
        binding.tvVisibilityValue.text = formatVisibility(current.visibilityKm, windUnit)

        // Full-Width Sunrise & Sunset Progress Card
        val sunriseTime = formatIsoTime(current.sunriseIso, "05:48")
        val sunsetTime = formatIsoTime(current.sunsetIso, "17:22")
        binding.tvSunriseValue.text = sunriseTime
        binding.tvSunsetValue.text = sunsetTime

        val dayProgress = calculateDayProgress(current.sunriseIso, current.sunsetIso, locationZone)
        binding.solarArcView.setProgress(dayProgress, current.isDay)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDailySwitch() {
        binding.btnDailyChart.setOnClickListener {
            if (!showDailyChart) {
                binding.dailyToggleTrack.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                showDailyChart = true
                applyDailyViewMode(animate = true)
            }
        }
        binding.btnDailyList.setOnClickListener {
            if (showDailyChart) {
                binding.dailyToggleTrack.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                showDailyChart = false
                applyDailyViewMode(animate = true)
            }
        }

        val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
        var startRawX = 0f

        val scrubListener = View.OnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    isScrubbingDaily = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.rawX - startRawX)
                    if (!isScrubbingDaily && dx > touchSlop * 0.25f) {
                        isScrubbingDaily = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        binding.dailyToggleTrack.requestDisallowInterceptTouchEvent(true)
                    }
                    if (isScrubbingDaily) {
                        val contentLoc = IntArray(2)
                        binding.dailyToggleContent.getLocationOnScreen(contentLoc)
                        val localX = (event.rawX - contentLoc[0]).coerceIn(
                            0f,
                            binding.dailyToggleContent.width.toFloat()
                        )
                        updateDailyIndicatorForScrub(localX)

                        val midpoint = (binding.btnDailyChart.right + binding.btnDailyList.left) * 0.5f
                        val targetChart = localX < midpoint
                        if (targetChart != showDailyChart) {
                            binding.dailyToggleTrack.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            showDailyChart = targetChart
                            applyDailyViewMode(animate = true, fromScrub = true)
                        }
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isScrubbingDaily) {
                        isScrubbingDaily = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        binding.dailyToggleTrack.requestDisallowInterceptTouchEvent(false)
                        positionDailyIndicator(immediate = reduceMotion)
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }

        binding.btnDailyChart.setOnTouchListener(scrubListener)
        binding.btnDailyList.setOnTouchListener(scrubListener)
        binding.dailyToggleTrack.setOnTouchListener(scrubListener)
        binding.dailyToggleContent.post {
            positionDailyIndicator(immediate = true)
        }
    }

    private fun updateDailyIndicatorForScrub(localX: Float) {
        dailyIndicatorAnimator?.cancel()
        val chartCenter = binding.btnDailyChart.left + binding.btnDailyChart.width * 0.5f
        val listCenter = binding.btnDailyList.left + binding.btnDailyList.width * 0.5f
        val span = (listCenter - chartCenter).coerceAtLeast(1f)
        val frac = ((localX - chartCenter) / span).coerceIn(0f, 1f)

        val interpW = (binding.btnDailyChart.width + (binding.btnDailyList.width - binding.btnDailyChart.width) * frac).toInt()
        val interpX = binding.btnDailyChart.left + (binding.btnDailyList.left - binding.btnDailyChart.left) * frac
        val lp = binding.dailyToggleIndicator.layoutParams
        lp.width = interpW
        binding.dailyToggleIndicator.layoutParams = lp
        binding.dailyToggleIndicator.translationX = interpX
    }

    private fun positionDailyIndicator(immediate: Boolean) {
        val target = if (showDailyChart) binding.btnDailyChart else binding.btnDailyList
        val targetW = target.width
        val targetX = target.left.toFloat()
        if (targetW <= 0) return

        dailyIndicatorAnimator?.cancel()
        val lp = binding.dailyToggleIndicator.layoutParams
        if (immediate) {
            lp.width = targetW
            binding.dailyToggleIndicator.layoutParams = lp
            binding.dailyToggleIndicator.translationX = targetX
            return
        }

        val startX = binding.dailyToggleIndicator.translationX
        val startW = if (lp.width > 0) lp.width else targetW
        dailyIndicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 210L
            interpolator = MotionHelper.FLUID_EASE
            addUpdateListener { va ->
                val f = va.animatedFraction
                binding.dailyToggleIndicator.translationX = startX + (targetX - startX) * f
                val wLp = binding.dailyToggleIndicator.layoutParams
                wLp.width = (startW + (targetW - startW) * f).toInt()
                binding.dailyToggleIndicator.layoutParams = wLp
            }
            start()
        }
    }

    private fun applyDailyViewMode(animate: Boolean, fromScrub: Boolean = false) {
        binding.btnDailyChart.setTextColor(if (showDailyChart) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())
        binding.btnDailyList.setTextColor(if (!showDailyChart) 0xFFFFFFFF.toInt() else 0x99FFFFFF.toInt())

        if (!fromScrub) {
            if (binding.btnDailyChart.width > 0) {
                positionDailyIndicator(immediate = !animate || reduceMotion)
            } else {
                binding.dailyToggleContent.post { positionDailyIndicator(immediate = true) }
            }
        }

        val incoming = if (showDailyChart) binding.dailyChartScroll else binding.dailyRows
        val outgoing = if (showDailyChart) binding.dailyRows else binding.dailyChartScroll

        if (!animate || reduceMotion) {
            outgoing.animate().cancel()
            incoming.animate().cancel()
            outgoing.visibility = View.GONE
            outgoing.alpha = 1f
            outgoing.translationX = 0f
            incoming.visibility = View.VISIBLE
            incoming.alpha = 1f
            incoming.translationX = 0f
            if (!showDailyChart) {
                binding.dailyRows.post { dailyBarUpdaters.forEach { it() } }
            }
            return
        }

        val density = binding.root.resources.displayMetrics.density
        val slideOffset = if (showDailyChart) -14f * density else 14f * density

        outgoing.animate().cancel()
        outgoing.visibility = View.GONE
        outgoing.alpha = 1f
        outgoing.translationX = 0f

        incoming.animate().cancel()
        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        incoming.translationX = slideOffset
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(220L)
            .setInterpolator(MotionHelper.FLUID_EASE)
            .start()
        if (!showDailyChart) {
            binding.dailyRows.post { dailyBarUpdaters.forEach { it() } }
        }
    }

    private fun setupCityPills(state: WeatherUiState, onSelectCity: (LocationItem) -> Unit) {
        val context = binding.root.context
        val density = context.resources.displayMetrics.density
        binding.cityPillsContainer.removeAllViews()

        val allCities = mutableListOf<LocationItem>()
        state.detectedCurrentLocation?.let { allCities.add(it) }
        state.savedLocations.forEach { loc ->
            if (allCities.none { it.id == loc.id }) allCities.add(loc)
        }

        if (allCities.isEmpty() && state.selectedLocation != null) {
            allCities.add(state.selectedLocation)
        }

        val activeId = state.selectedLocation?.id ?: allCities.firstOrNull()?.id
        val pillHeightPx = (42 * density).toInt()

        var activePill: View? = null
        allCities.forEach { city ->
            val isSelected = city.id == activeId
            val pill = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(
                    (14 * density).toInt(),
                    0,
                    (14 * density).toInt(),
                    0
                )
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    pillHeightPx
                ).apply {
                    marginEnd = (6 * density).toInt()
                }
                layoutParams = params

                if (isSelected) {
                    setBackgroundResource(R.drawable.bg_city_pill_active)
                } else {
                    setBackgroundResource(R.drawable.bg_pill_accent_translucent)
                }

                if (city.isCurrentLocation || city.id == state.detectedCurrentLocation?.id) {
                    val icon = ImageView(context).apply {
                        val size = (14 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            marginEnd = (5 * density).toInt()
                        }
                        setImageResource(R.drawable.ic_arrow_current_location)
                        imageTintList = ColorStateList.valueOf(if (isSelected) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt())
                    }
                    addView(icon)
                }

                val text = TextView(context).apply {
                    this.text = city.name
                    textSize = 12f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
                    setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt())
                }
                addView(text)

                setOnClickListener { onSelectCity(city) }
                MotionHelper.attachPressScale(this, pressedScale = 0.94f, hapticOnTap = true)
            }
            if (isSelected) activePill = pill
            binding.cityPillsContainer.addView(pill)
        }

        val targetPill = activePill
        val scroller = binding.cityPillsContainer.parent as? HorizontalScrollView
        if (targetPill != null && scroller != null) {
            scroller.post {
                val targetX = (targetPill.left - (scroller.width - targetPill.width) / 2).coerceAtLeast(0)
                if (reduceMotion) scroller.scrollTo(targetX, 0) else scroller.smoothScrollTo(targetX, 0)
            }
        }
    }

    private fun setupWeeklyForecast(
        forecast: ForecastData,
        tempUnit: TemperatureUnit,
        colorScheme: AppColorScheme,
        locationZone: ZoneId
    ) {
        val context = binding.root.context
        val daysToRender = forecast.daily.take(7)
        val minGlobal = daysToRender.minOfOrNull { it.minTempC } ?: 0.0
        val maxGlobal = daysToRender.maxOfOrNull { it.maxTempC } ?: (minGlobal + 1.0)
        val tempRange = (maxGlobal - minGlobal).coerceAtLeast(1.0)

        binding.dailyRows.removeAllViews()
        val currentLocale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val todayDate = LocalDate.now(locationZone)
        val updaters = mutableListOf<() -> Unit>()

        daysToRender.forEachIndexed { index, day ->
            val row = ItemDailyForecastBinding.inflate(LayoutInflater.from(context), binding.dailyRows, false)

            val parsedDate = runCatching { LocalDate.parse(day.dateIso) }.getOrNull()
            val isToday = parsedDate != null && parsedDate == todayDate
            val dayName = if (isToday) {
                context.getString(R.string.day_today)
            } else {
                parsedDate?.format(DateTimeFormatter.ofPattern("EEE", currentLocale)) ?: day.dayLabel
            }

            row.dayLabel.text = dayName
            row.dayLabel.setTextColor(0xFFFFFFFF.toInt())
            row.dayIcon.setImageResource(WeatherCodeMapper.getIcon(day.weatherCode, isDay = true))

            if (day.precipitationProbabilityMax >= 20) {
                row.dayPop.visibility = View.VISIBLE
                row.dayPop.text = "${day.precipitationProbabilityMax}%"
                row.dayPop.setTextColor(0xFF38BDF8.toInt())
            } else {
                row.dayPop.visibility = View.GONE
            }

            row.minTemperature.text = tempUnit.format(day.minTempC)
            row.maxTemperature.text = tempUnit.format(day.maxTempC)

            val barGradient = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFF38BDF8.toInt(), 0xFFFDE047.toInt(), 0xFFFB923C.toInt())
            ).apply {
                cornerRadius = 999f
            }
            row.temperatureRange.background = barGradient

            val updateBar = {
                val containerWidth = row.temperatureRangeContainer.width
                if (containerWidth > 0 && (row.temperatureRangeContainer.tag as? Int) != containerWidth) {
                    row.temperatureRangeContainer.tag = containerWidth
                    val totalWidth = containerWidth.toFloat()
                    val minRatio = ((day.minTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val maxRatio = ((day.maxTempC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                    val minBarPx = (16 * context.resources.displayMetrics.density).toInt()
                    val leftMargin = (totalWidth * minRatio).toInt().coerceIn(0, (totalWidth - minBarPx).toInt().coerceAtLeast(0))
                    val barWidth = (totalWidth * (maxRatio - minRatio)).toInt()
                        .coerceAtLeast(minBarPx)
                        .coerceAtMost((totalWidth - leftMargin).toInt().coerceAtLeast(0))

                    val lp = row.temperatureRange.layoutParams as FrameLayout.LayoutParams
                    lp.leftMargin = leftMargin
                    lp.marginStart = leftMargin
                    lp.width = barWidth
                    row.temperatureRange.layoutParams = lp

                    if (isToday && !forecast.isStale) {
                        row.currentTempDot.visibility = View.VISIBLE
                        val curRatio = ((forecast.current.temperatureC - minGlobal) / tempRange).coerceIn(0.0, 1.0).toFloat()
                        val dotSize = (8 * context.resources.displayMetrics.density).toInt()
                        val dotLeft = ((totalWidth - dotSize) * curRatio).toInt().coerceAtLeast(0)
                        val dotLp = row.currentTempDot.layoutParams as FrameLayout.LayoutParams
                        dotLp.leftMargin = dotLeft
                        dotLp.marginStart = dotLeft
                        row.currentTempDot.layoutParams = dotLp
                    } else {
                        row.currentTempDot.visibility = View.GONE
                    }
                }
            }
            updaters.add(updateBar)
            row.temperatureRangeContainer.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.width > 0 && (v.tag as? Int) != v.width) {
                    v.post(updateBar)
                }
            }
            row.temperatureRangeContainer.post(updateBar)

            binding.dailyRows.addView(row.root)

            if (index < daysToRender.lastIndex) {
                val divider = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (1f * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(0x14FFFFFF)
                }
                binding.dailyRows.addView(divider)
            }
        }
        dailyBarUpdaters = updaters
    }

    private fun setForecastSectionsVisibility(visibility: Int) {
        binding.cardHourly.visibility = visibility
        binding.cardWeekly.visibility = visibility
        binding.metricsRow1.visibility = visibility
        binding.metricsRow2.visibility = visibility
        binding.cardSolar.visibility = visibility
    }

    private fun updateForecastStatusBanner(
        context: Context,
        forecast: ForecastData,
        error: String?,
        zoneId: ZoneId,
        onRefresh: () -> Unit
    ) {
        val showOffline = !error.isNullOrBlank()
        if (!forecast.isStale && !showOffline) {
            binding.tvForecastStatus.visibility = View.GONE
            binding.tvForecastStatus.setOnClickListener(null)
            return
        }
        val formattedTime = runCatching {
            val fetchedZdt = Instant.ofEpochMilli(forecast.fetchedAtEpochMs).atZone(zoneId)
            val nowZdt = ZonedDateTime.now(zoneId)
            val pattern = if (fetchedZdt.toLocalDate() == nowZdt.toLocalDate()) "HH:mm" else "d/M HH:mm"
            fetchedZdt.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }.getOrDefault("--:--")

        val resId = if (showOffline) R.string.forecast_offline_stale_format else R.string.forecast_stale_format
        binding.tvForecastStatus.visibility = View.VISIBLE
        binding.tvForecastStatus.text = context.getString(resId, formattedTime)
        binding.tvForecastStatus.setOnClickListener { onRefresh() }
    }

    private fun generateNarrativeDescription(
        context: Context,
        current: CurrentWeather,
        maxPop24h: Int,
        windUnit: WindSpeedUnit,
        precipUnit: PrecipitationUnit = PrecipitationUnit.MILLIMETERS
    ): String {
        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        val windStr = windUnit.format(current.windSpeedKmh)
        val compass = degreesToCompass(context, current.windDirectionDeg)
        val precipStr = precipUnit.format(current.precipitationMm)
        val visibilityStr = formatVisibility(current.visibilityKm, windUnit)
        return if (isVi) {
            val windPart = "Gió $compass $windStr, độ ẩm ${current.humidityPercent}%"
            when {
                current.precipitationMm > 0.5 -> "Đang có mưa ($precipStr) • $windPart."
                maxPop24h >= 40 -> "Khả năng mưa lên tới $maxPop24h% trong 24 giờ tới • $windPart."
                current.uvIndex >= 6.0 && current.isDay -> "Chỉ số UV hiện tại ${current.uvIndexInt} • $windPart."
                else -> "$windPart • Tầm nhìn $visibilityStr."
            }
        } else {
            val windPart = "$compass wind at $windStr, ${current.humidityPercent}% humidity"
            when {
                current.precipitationMm > 0.5 -> "Active precipitation ($precipStr) • $windPart."
                maxPop24h >= 40 -> "Rain chance up to $maxPop24h% over the next 24h • $windPart."
                current.uvIndex >= 6.0 && current.isDay -> "UV index at ${current.uvIndexInt} • $windPart."
                else -> "$windPart • Visibility $visibilityStr."
            }
        }
    }

    private fun calculateDayProgress(sunriseIso: String, sunsetIso: String, zoneId: ZoneId): Float {
        return runCatching {
            val nowSec = ZonedDateTime.now(zoneId).toEpochSecond()
            val riseSec = LocalDateTime.parse(sunriseIso).atZone(zoneId).toEpochSecond()
            val setSec = LocalDateTime.parse(sunsetIso).atZone(zoneId).toEpochSecond()
            ((nowSec - riseSec).toFloat() / (setSec - riseSec).toFloat()).coerceIn(0f, 1f)
        }.getOrDefault(0.6f)
    }

    private fun degreesToCompass(context: Context, deg: Int): String {
        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        val dirs = if (isVi) {
            arrayOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")
        } else {
            arrayOf("North", "Northeast", "East", "Southeast", "South", "Southwest", "West", "Northwest")
        }
        val idx = (((deg % 360) + 360) % 360 + 22) / 45 % 8
        return dirs[idx]
    }

    private fun formatIsoTime(iso: String, fallback: String): String {
        if (iso.isBlank()) return fallback
        return runCatching {
            LocalDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrElse {
            iso.substringAfter('T', fallback).take(5).ifBlank { fallback }
        }
    }

    companion object {
        internal fun formatVisibility(visibilityKm: Double, windUnit: WindSpeedUnit): String {
            return if (windUnit == WindSpeedUnit.MPH) {
                val miles = visibilityKm * 0.621371
                if (miles < 1.0) {
                    String.format(Locale.US, "%.1f mi", miles.coerceAtLeast(0.1))
                } else {
                    "${miles.roundToInt()} mi"
                }
            } else {
                if (visibilityKm < 1.0) {
                    "${(visibilityKm * 1000.0).roundToInt().coerceAtLeast(50)} m"
                } else {
                    "${visibilityKm.roundToInt()} km"
                }
            }
        }
    }
}

class ForecastBinder(private val view: ForecastView) {
    fun bind(
        state: WeatherUiState,
        onSearch: () -> Unit,
        onRefresh: () -> Unit,
        onSelectCity: (LocationItem) -> Unit,
        onToggleUnit: (() -> Unit)? = null
    ) = view.bind(state, onSearch, onRefresh, onSelectCity, onToggleUnit)
}

private class HourlyAdapter(
    private val items: List<HourlyForecast>,
    private val unit: TemperatureUnit,
    private val nowLabel: String,
    private val accentColor: Int,
    private val zoneId: ZoneId,
    private val isStale: Boolean
) : RecyclerView.Adapter<HourlyAdapter.Holder>() {

    private val nowEpochSec = Instant.now().epochSecond
    private val todayDate = LocalDate.now(zoneId)

    class Holder(val binding: ItemHourlyForecastBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemHourlyForecastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = minOf(24, items.size)

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val isCurrentHour = position == 0 && !isStale && kotlin.math.abs(nowEpochSec - item.timeEpochSeconds) < 5400L

        if (isCurrentHour) {
            holder.binding.hourlyCard.setBackgroundResource(R.drawable.bg_hourly_card_active)
        } else {
            holder.binding.hourlyCard.setBackgroundResource(R.drawable.bg_hourly_card)
        }

        holder.binding.hourTime.text = if (isCurrentHour) {
            nowLabel
        } else {
            runCatching {
                val zdt = Instant.ofEpochSecond(item.timeEpochSeconds).atZone(zoneId)
                val pattern = if (zdt.toLocalDate() == todayDate) "HH:mm" else "d/M HH:mm"
                zdt.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
            }.getOrDefault(item.timeLabel)
        }
        holder.binding.hourTime.setTextColor(if (isCurrentHour) 0xFFFFFFFF.toInt() else 0xD9FFFFFF.toInt())
        holder.binding.hourIcon.setImageResource(WeatherCodeMapper.getIcon(item.weatherCode, item.isDay))
        if (item.precipitationProbability >= 15) {
            holder.binding.hourPop.visibility = View.VISIBLE
            holder.binding.hourPop.text = "${item.precipitationProbability}%"
            holder.binding.hourPop.setTextColor(0xFF7DD3FC.toInt())
        } else {
            holder.binding.hourPop.visibility = View.INVISIBLE
        }
        holder.binding.hourTemperature.text = unit.format(item.temperatureC)
    }
}
