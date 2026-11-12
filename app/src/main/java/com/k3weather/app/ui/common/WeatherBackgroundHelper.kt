package com.k3weather.app.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.k3weather.app.R
import com.k3weather.app.core.model.LocationItem

object WeatherBackgroundHelper {
    fun cityDrawable(context: Context, location: LocationItem): Drawable? {
        val key = "city_${location.name.lowercase().replace(Regex("[^a-z]"), "") }"
        val resourceId = when {
            key.contains("dalat") || key.contains("đàlạt") -> R.drawable.city_dalat
            key.contains("danang") || key.contains("đànẵng") -> R.drawable.city_danang
            key.contains("hanoi") || key.contains("hànoi") -> R.drawable.city_hanoi
            else -> 0
        }
        return if (resourceId != 0) ContextCompat.getDrawable(context, resourceId) else null
    }

    fun apply(view: View, location: LocationItem, condition: String, isDay: Boolean, enabled: Boolean = true) {
        if (!enabled) {
            view.background = ContextCompat.getDrawable(view.context, R.color.weather_background)
            return
        }
        view.background = cityDrawable(view.context, location)
            ?: ContextCompat.getDrawable(view.context, when {
                condition.contains("rain", true) || condition.contains("storm", true) -> R.drawable.bg_gradient_overlay
                condition.contains("fog", true) || condition.contains("mist", true) -> R.drawable.bg_gradient_overlay
                condition.contains("cloud", true) -> R.drawable.bg_gradient_overlay
                else -> R.drawable.bg_gradient_overlay
            })
        val tint = when {
            condition.contains("rain", true) || condition.contains("storm", true) -> Color.argb(if (isDay) 72 else 128, 12, 39, 69)
            condition.contains("fog", true) || condition.contains("mist", true) -> Color.argb(if (isDay) 50 else 90, 128, 141, 150)
            condition.contains("cloud", true) -> Color.argb(if (isDay) 32 else 96, 44, 57, 74)
            !isDay -> Color.argb(112, 7, 17, 42)
            else -> Color.TRANSPARENT
        }
        view.foreground = android.graphics.drawable.ColorDrawable(tint)
    }
}
