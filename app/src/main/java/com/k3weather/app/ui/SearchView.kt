package com.k3weather.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.R
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.databinding.ItemSearchResultBinding
import com.k3weather.app.databinding.LayoutSearchBinding

class SearchView(private val binding: LayoutSearchBinding) {
    private val adapter = SearchAdapter { }
    private var isSetup = false

    fun bind(
        state: WeatherUiState,
        onBack: () -> Unit,
        onQuery: (String) -> Unit,
        onSelect: (LocationItem) -> Unit
    ) {
        val context = binding.root.context
        if (!isSetup) {
            isSetup = true
            binding.searchResults.adapter = adapter
            binding.backButton.setOnClickListener { onBack() }
            binding.clearButton.setOnClickListener { binding.searchInput.text?.clear() }
            binding.searchInput.doAfterTextChanged { text ->
                val query = text?.toString().orEmpty()
                binding.clearButton.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                onQuery(query)
            }
        }
        adapter.onSelect = onSelect
        binding.searchProgress.visibility = if (state.searching) View.VISIBLE else View.GONE

        val hasQuery = !binding.searchInput.text.isNullOrBlank()
        binding.clearButton.visibility = if (hasQuery) View.VISIBLE else View.GONE

        val savedIds = buildSet {
            state.savedLocations.forEach { add(it.id) }
            state.detectedCurrentLocation?.let { add(it.id) }
        }
        val savedNames = buildSet {
            state.savedLocations.forEach { add(it.name.lowercase()) }
        }

        if (!hasQuery) {
            binding.tvResultsHeader.text = context.getString(R.string.popular_locations_header)
            val isVi = context.resources.configuration.locales[0]?.language == "vi"
            val popular = buildPopularLocations(isVi, state.detectedCurrentLocation)
            adapter.submit(popular, savedIds, savedNames)
            binding.emptyState.visibility = View.GONE
            binding.searchResults.visibility = View.VISIBLE
        } else {
            binding.tvResultsHeader.text = context.getString(R.string.search_results_header)
            adapter.submit(state.searchResults, savedIds, savedNames)
            val showEmpty = !state.searching && state.searchResults.isEmpty()
            binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
            binding.searchResults.visibility = if (showEmpty) View.GONE else View.VISIBLE
            binding.emptyState.text = state.searchError ?: context.getString(R.string.no_results)
        }
    }

    private fun buildPopularLocations(isVi: Boolean, currentLocation: LocationItem?): List<LocationItem> {
        val list = mutableListOf<LocationItem>()
        currentLocation?.let { list.add(it) }
        val vnCountry = if (isVi) "Việt Nam" else "Vietnam"
        val presets = listOf(
            LocationItem(id = "preset_hanoi", name = "Hà Nội", country = vnCountry, latitude = 21.0285, longitude = 105.8542, timezone = "Asia/Bangkok", region = if (isVi) "Thủ đô" else "Capital"),
            LocationItem(id = "preset_hcmc", name = if (isVi) "TP. Hồ Chí Minh" else "Ho Chi Minh City", country = vnCountry, latitude = 10.8231, longitude = 106.6297, timezone = "Asia/Bangkok", region = if (isVi) "Đông Nam Bộ" else "Southern Vietnam"),
            LocationItem(id = "preset_danang", name = "Đà Nẵng", country = vnCountry, latitude = 16.0544, longitude = 108.2022, timezone = "Asia/Bangkok", region = if (isVi) "Duyên hải Nam Trung Bộ" else "Central Coast"),
            LocationItem(id = "preset_hue", name = "Huế", country = vnCountry, latitude = 16.4637, longitude = 107.5909, timezone = "Asia/Bangkok", region = "Thừa Thiên Huế"),
            LocationItem(id = "preset_dalat", name = "Đà Lạt", country = vnCountry, latitude = 11.9404, longitude = 108.4583, timezone = "Asia/Bangkok", region = "Lâm Đồng"),
            LocationItem(id = "preset_nhatrang", name = "Nha Trang", country = vnCountry, latitude = 12.2388, longitude = 109.1967, timezone = "Asia/Bangkok", region = "Khánh Hòa"),
            LocationItem(id = "preset_haiphong", name = "Hải Phòng", country = vnCountry, latitude = 20.8449, longitude = 106.6881, timezone = "Asia/Bangkok", region = if (isVi) "Đồng bằng sông Hồng" else "Red River Delta"),
            LocationItem(id = "preset_cantho", name = "Cần Thơ", country = vnCountry, latitude = 10.0452, longitude = 105.7469, timezone = "Asia/Bangkok", region = if (isVi) "Đồng bằng sông Cửu Long" else "Mekong Delta"),
            LocationItem(id = "preset_phuquoc", name = "Phú Quốc", country = vnCountry, latitude = 10.2270, longitude = 103.9632, timezone = "Asia/Bangkok", region = "Kiên Giang"),
            LocationItem(id = "preset_tokyo", name = "Tokyo", country = if (isVi) "Nhật Bản" else "Japan", latitude = 35.6762, longitude = 139.6503, timezone = "Asia/Tokyo", region = "Kanto"),
            LocationItem(id = "preset_seoul", name = "Seoul", country = if (isVi) "Hàn Quốc" else "South Korea", latitude = 37.5665, longitude = 126.9780, timezone = "Asia/Seoul", region = "Seoul"),
            LocationItem(id = "preset_singapore", name = "Singapore", country = "Singapore", latitude = 1.3521, longitude = 103.8198, timezone = "Asia/Singapore"),
            LocationItem(id = "preset_london", name = "London", country = if (isVi) "Vương quốc Anh" else "United Kingdom", latitude = 51.5074, longitude = -0.1278, timezone = "Europe/London", region = "England"),
            LocationItem(id = "preset_paris", name = "Paris", country = if (isVi) "Pháp" else "France", latitude = 48.8566, longitude = 2.3522, timezone = "Europe/Paris", region = "Île-de-France")
        )
        for (p in presets) {
            if (list.none { it.name.equals(p.name, ignoreCase = true) }) {
                list.add(p)
            }
        }
        return list
    }
}

class SearchBinder(private val view: SearchView) {
    fun bind(
        state: WeatherUiState,
        onBack: () -> Unit,
        onQuery: (String) -> Unit,
        onSelect: (LocationItem) -> Unit
    ) = view.bind(state, onBack, onQuery, onSelect)
}

private class SearchAdapter(var onSelect: (LocationItem) -> Unit) : RecyclerView.Adapter<SearchAdapter.Holder>() {
    private var items: List<LocationItem> = emptyList()
    private var savedIds: Set<String> = emptySet()
    private var savedNames: Set<String> = emptySet()

    class Holder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(values: List<LocationItem>, savedIds: Set<String>, savedNames: Set<String>) {
        items = values
        this.savedIds = savedIds
        this.savedNames = savedNames
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.binding.root.context
        holder.binding.cityName.text = item.name

        val subtitle = if (item.isCurrentLocation) {
            val base = item.subtitle.ifBlank { item.country }
            "${context.getString(R.string.current_location)} • $base"
        } else {
            item.subtitle.ifBlank { item.country }
        }
        holder.binding.citySubtitle.text = subtitle
        holder.binding.citySubtitle.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE

        holder.binding.ivLocationPin.setImageResource(
            if (item.isCurrentLocation) R.drawable.ic_arrow_current_location else R.drawable.ic_nav_saved
        )

        val isSaved = item.isCurrentLocation || item.id in savedIds || item.name.lowercase() in savedNames
        holder.binding.tvSavedBadge.visibility = if (isSaved) View.VISIBLE else View.GONE
        holder.binding.actionIconContainer.visibility = if (isSaved) View.GONE else View.VISIBLE
        holder.binding.itemDivider.visibility = if (position == items.lastIndex) View.GONE else View.VISIBLE

        holder.binding.root.setOnClickListener { onSelect(item) }
    }
}
