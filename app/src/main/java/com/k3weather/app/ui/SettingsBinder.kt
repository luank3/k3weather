package com.k3weather.app.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.content.ContextCompat
import com.k3weather.app.R
import com.k3weather.app.core.model.*
import com.k3weather.app.databinding.DialogLanguageGlassBinding
import com.k3weather.app.databinding.LayoutSettingsBinding

class SettingsBinder(
    private val binding: LayoutSettingsBinding,
    private val onSettingsChanged: (AppSettings) -> Unit
) {
    fun bind(settings: AppSettings) {
        val context = binding.root.context

        // Units
        binding.tvTemperatureUnitValue.text = if (settings.tempUnit == TemperatureUnit.CELSIUS) "°C  ›" else "°F  ›"
        binding.tvWindSpeedUnitValue.text = if (settings.windSpeedUnit == WindSpeedUnit.KMH) "km/h  ›" else "mph  ›"

        binding.rowTemperature.setOnClickListener {
            val next = if (settings.tempUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
            send(settings.copy(tempUnit = next))
        }

        binding.rowWindSpeed.setOnClickListener {
            val next = if (settings.windSpeedUnit == WindSpeedUnit.KMH) WindSpeedUnit.MPH else WindSpeedUnit.KMH
            send(settings.copy(windSpeedUnit = next))
        }

        // Stateful theme color for switches
        val accentColor = settings.colorScheme.primaryAccent
        val activeTrackColor = (accentColor and 0x00FFFFFF) or 0x73000000
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val thumbTintList = ColorStateList(
            states,
            intArrayOf(accentColor, 0xFFB0BEC5.toInt())
        )
        val trackTintList = ColorStateList(
            states,
            intArrayOf(activeTrackColor, 0x3DFFFFFF)
        )

        binding.reduceMotionSwitch.thumbTintList = thumbTintList
        binding.reduceMotionSwitch.trackTintList = trackTintList
        binding.currentLocationSwitch.thumbTintList = thumbTintList
        binding.currentLocationSwitch.trackTintList = trackTintList

        binding.reduceMotionSwitch.setOnCheckedChangeListener(null)
        binding.reduceMotionSwitch.isChecked = settings.reduceMotion
        binding.reduceMotionSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(reduceMotion = checked))
        }
        binding.rowReduceMotion.setOnClickListener {
            binding.reduceMotionSwitch.toggle()
        }

        // Language
        val langLabel = when (settings.language) {
            "en" -> context.getString(R.string.lang_en)
            "vi" -> context.getString(R.string.lang_vi)
            else -> context.getString(R.string.lang_system)
        }
        binding.tvLanguageValue.text = "$langLabel  ›"
        binding.rowLanguage.setOnClickListener {
            showLanguageDialog(settings)
        }

        // Location
        binding.currentLocationSwitch.setOnCheckedChangeListener(null)
        binding.currentLocationSwitch.isChecked = settings.useCurrentLocation
        binding.currentLocationSwitch.setOnCheckedChangeListener { _, checked ->
            send(settings.copy(useCurrentLocation = checked))
        }
        binding.rowCurrentLocation.setOnClickListener {
            binding.currentLocationSwitch.toggle()
        }
    }

    private fun showLanguageDialog(settings: AppSettings) {
        val context = binding.root.context
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogLanguageGlassBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setDimAmount(0.65f)
        }

        val currentLang = settings.language // "system", "en", "vi"

        fun updateSelection(selected: String) {
            val cyan = ContextCompat.getColor(context, R.color.accent_cyan)
            val white = Color.WHITE

            val isSystem = selected == "system"
            dialogBinding.optSystem.setBackgroundResource(
                if (isSystem) R.drawable.bg_dialog_item_selected else R.drawable.bg_dialog_item_unselected
            )
            dialogBinding.ivCheckSystem.visibility = if (isSystem) View.VISIBLE else View.GONE
            dialogBinding.tvOptSystemTitle.setTextColor(if (isSystem) cyan else white)

            val isEn = selected == "en"
            dialogBinding.optEn.setBackgroundResource(
                if (isEn) R.drawable.bg_dialog_item_selected else R.drawable.bg_dialog_item_unselected
            )
            dialogBinding.ivCheckEn.visibility = if (isEn) View.VISIBLE else View.GONE
            dialogBinding.tvOptEnTitle.setTextColor(if (isEn) cyan else white)

            val isVi = selected == "vi"
            dialogBinding.optVi.setBackgroundResource(
                if (isVi) R.drawable.bg_dialog_item_selected else R.drawable.bg_dialog_item_unselected
            )
            dialogBinding.ivCheckVi.visibility = if (isVi) View.VISIBLE else View.GONE
            dialogBinding.tvOptViTitle.setTextColor(if (isVi) cyan else white)
        }

        updateSelection(currentLang)

        dialogBinding.optSystem.setOnClickListener {
            dialog.dismiss()
            send(settings.copy(language = "system"))
        }

        dialogBinding.optEn.setOnClickListener {
            dialog.dismiss()
            send(settings.copy(language = "en"))
        }

        dialogBinding.optVi.setOnClickListener {
            dialog.dismiss()
            send(settings.copy(language = "vi"))
        }

        dialogBinding.btnDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun send(settings: AppSettings) = onSettingsChanged(settings)
}
