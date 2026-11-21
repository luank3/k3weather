package com.k3weather.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
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
            binding.searchInput.setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(v.windowToken, 0)
                    onQuery(v.text?.toString().orEmpty())
                    true
                } else {
                    false
                }
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

        val isVi = context.resources.configuration.locales[0]?.language == "vi"
        val popular = buildPopularLocations(isVi, state.detectedCurrentLocation)

        if (!hasQuery) {
            binding.tvResultsHeader.text = context.getString(R.string.popular_locations_header)
            adapter.submit(popular, savedIds, savedNames)
            binding.emptyState.visibility = View.GONE
            binding.searchResults.visibility = View.VISIBLE
        } else {
            val queryStr = binding.searchInput.text?.toString()?.trim().orEmpty()
            binding.tvResultsHeader.text = context.getString(R.string.search_results_header)
            val instantMatches = if (state.searchResults.isEmpty()) {
                popular.filter {
                    it.name.contains(queryStr, ignoreCase = true) ||
                        it.region.contains(queryStr, ignoreCase = true) ||
                        it.country.contains(queryStr, ignoreCase = true)
                }
            } else {
                state.searchResults
            }
            adapter.submit(instantMatches, savedIds, savedNames)
            val showEmpty = !state.searching && instantMatches.isEmpty()
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
            LocationItem(id = "1581130", name = "Hà Nội", country = vnCountry, latitude = 21.0245, longitude = 105.84117, timezone = "Asia/Ho_Chi_Minh", region = if (isVi) "Thủ đô" else "Capital"),
            LocationItem(id = "1566083", name = if (isVi) "TP. Hồ Chí Minh" else "Ho Chi Minh City", country = vnCountry, latitude = 10.8231, longitude = 106.6297, timezone = "Asia/Ho_Chi_Minh", region = if (isVi) "Đông Nam Bộ" else "Southern Vietnam"),
            LocationItem(id = "1583992", name = "Đà Nẵng", country = vnCountry, latitude = 16.06778, longitude = 108.22083, timezone = "Asia/Ho_Chi_Minh", region = if (isVi) "Duyên hải Nam Trung Bộ" else "Central Coast"),
            LocationItem(id = "1580240", name = "Huế", country = vnCountry, latitude = 16.4637, longitude = 107.5909, timezone = "Asia/Ho_Chi_Minh", region = "Thừa Thiên Huế"),
            LocationItem(id = "1584071", name = "Đà Lạt", country = vnCountry, latitude = 11.94646, longitude = 108.44193, timezone = "Asia/Ho_Chi_Minh", region = "Lâm Đồng"),
            LocationItem(id = "1572151", name = "Nha Trang", country = vnCountry, latitude = 12.2388, longitude = 109.1967, timezone = "Asia/Ho_Chi_Minh", region = "Khánh Hòa"),
            LocationItem(id = "1581298", name = "Hải Phòng", country = vnCountry, latitude = 20.8449, longitude = 106.6881, timezone = "Asia/Ho_Chi_Minh", region = if (isVi) "Đồng bằng sông Hồng" else "Red River Delta"),
            LocationItem(id = "1586203", name = "Cần Thơ", country = vnCountry, latitude = 10.0452, longitude = 105.7469, timezone = "Asia/Ho_Chi_Minh", region = if (isVi) "Đồng bằng sông Cửu Long" else "Mekong Delta"),
            LocationItem(id = "1579008", name = "Phú Quốc", country = vnCountry, latitude = 10.2270, longitude = 103.9632, timezone = "Asia/Ho_Chi_Minh", region = "Kiên Giang"),
            LocationItem(id = "1850147", name = "Tokyo", country = if (isVi) "Nhật Bản" else "Japan", latitude = 35.6762, longitude = 139.6503, timezone = "Asia/Tokyo", region = "Kanto"),
            LocationItem(id = "1835848", name = "Seoul", country = if (isVi) "Hàn Quốc" else "South Korea", latitude = 37.5665, longitude = 126.9780, timezone = "Asia/Seoul", region = "Seoul"),
            LocationItem(id = "1880252", name = "Singapore", country = "Singapore", latitude = 1.3521, longitude = 103.8198, timezone = "Asia/Singapore"),
            LocationItem(id = "2643743", name = "London", country = if (isVi) "Vương quốc Anh" else "United Kingdom", latitude = 51.5074, longitude = -0.1278, timezone = "Europe/London", region = "England"),
            LocationItem(id = "2988507", name = "Paris", country = if (isVi) "Pháp" else "France", latitude = 48.8566, longitude = 2.3522, timezone = "Europe/Paris", region = "Île-de-France")
        )
        for (p in presets) {
            if (list.none { it.id == p.id || it.name.equals(p.name, ignoreCase = true) }) {
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
        val oldItems = this.items
        val oldSavedIds = this.savedIds
        val oldSavedNames = this.savedNames
        this.items = values
        this.savedIds = savedIds
        this.savedNames = savedNames

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = values.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].id == values[newItemPosition].id

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldItems[oldItemPosition]
                val newItem = values[newItemPosition]
                val oldSaved = oldItem.isCurrentLocation || oldItem.id in oldSavedIds || oldItem.name.lowercase() in oldSavedNames
                val newSaved = newItem.isCurrentLocation || newItem.id in savedIds || newItem.name.lowercase() in savedNames
                val oldLast = oldItemPosition == oldItems.lastIndex
                val newLast = newItemPosition == values.lastIndex
                return oldItem == newItem && oldSaved == newSaved && oldLast == newLast
            }
        })
        diff.dispatchUpdatesTo(this)
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
