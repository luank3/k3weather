package com.k3weather.app.ui

import android.content.res.ColorStateList
import android.view.View
import androidx.lifecycle.LifecycleCoroutineScope
import com.k3weather.app.R
import com.k3weather.app.core.model.ForecastData
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.databinding.LayoutRadarBinding
import com.k3weather.app.ui.common.RadarMapView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RadarView(
    private val binding: LayoutRadarBinding,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    private var currentLayer: RadarMapView.Layer = RadarMapView.Layer.PRECIP
    private var currentStep: Int = 0
    private var isPlaying: Boolean = true
    private var playJob: Job? = null

    private val segmentViews by lazy {
        listOf(
            binding.seg0,
            binding.seg1,
            binding.seg2,
            binding.seg3,
            binding.seg4,
            binding.seg5
        )
    }

    init {
        setupListeners()
        startPlayback()
    }

    private fun setupListeners() {
        binding.btnLayerRain.setOnClickListener { selectLayer(RadarMapView.Layer.PRECIP) }
        binding.btnLayerWind.setOnClickListener { selectLayer(RadarMapView.Layer.WIND) }
        binding.btnLayerTemp.setOnClickListener { selectLayer(RadarMapView.Layer.TEMP) }

        binding.btnRadarPlayPause.setOnClickListener {
            isPlaying = !isPlaying
            binding.btnRadarPlayPause.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            if (isPlaying) startPlayback() else stopPlayback()
        }

        segmentViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                setStep(index)
            }
        }
    }

    private fun selectLayer(layer: RadarMapView.Layer) {
        currentLayer = layer
        binding.radarMapView.setLayer(layer)

        val activeBg = R.drawable.bg_pill_accent_translucent
        val inactiveBg = android.R.color.transparent
        val activeTextColor = 0xFFFFFFFF.toInt()
        val inactiveTextColor = 0xB3FFFFFF.toInt()

        binding.btnLayerRain.setBackgroundResource(if (layer == RadarMapView.Layer.PRECIP) activeBg else inactiveBg)
        binding.btnLayerRain.setTextColor(if (layer == RadarMapView.Layer.PRECIP) activeTextColor else inactiveTextColor)

        binding.btnLayerWind.setBackgroundResource(if (layer == RadarMapView.Layer.WIND) activeBg else inactiveBg)
        binding.btnLayerWind.setTextColor(if (layer == RadarMapView.Layer.WIND) activeTextColor else inactiveTextColor)

        binding.btnLayerTemp.setBackgroundResource(if (layer == RadarMapView.Layer.TEMP) activeBg else inactiveBg)
        binding.btnLayerTemp.setTextColor(if (layer == RadarMapView.Layer.TEMP) activeTextColor else inactiveTextColor)
    }

    private fun setStep(step: Int) {
        currentStep = step.coerceIn(0, 5)
        binding.radarMapView.setStep(currentStep)
        updateSegmentsUi()
    }

    private fun updateSegmentsUi() {
        val cyan = 0xFF38BDF8.toInt()
        val transparent = 0x00000000
        segmentViews.forEachIndexed { i, v ->
            v.setBackgroundColor(if (i <= currentStep) cyan else transparent)
        }
    }

    private fun startPlayback() {
        playJob?.cancel()
        playJob = lifecycleScope.launch {
            while (isActive && isPlaying) {
                delay(1100L)
                val next = (currentStep + 1) % 6
                setStep(next)
            }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
    }

    fun bind(state: WeatherUiState) {
        val context = binding.root.context
        val loc = state.forecast?.location ?: state.selectedLocation
        val cityName = loc?.name ?: "Da Nang"
        val tempStr = state.forecast?.let {
            state.settings.tempUnit.format(it.current.temperatureC)
        } ?: "--°"

        binding.tvRadarSubtitle.text = context.getString(R.string.radar_subtitle, cityName)
        binding.tvAdvisoryTitle.text = context.getString(R.string.radar_advisory_title, cityName)
        binding.radarMapView.setCity(cityName, tempStr)
    }
}
