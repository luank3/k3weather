package com.k3weather.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.k3weather.app.core.model.LocationItem
import com.k3weather.app.databinding.ItemSearchResultBinding
import com.k3weather.app.databinding.LayoutSearchBinding

class SearchView(private val binding: LayoutSearchBinding) {
    private val adapter = SearchAdapter { }
    private var isSetup = false

    fun bind(state: WeatherUiState, onBack: () -> Unit, onQuery: (String) -> Unit, onSelect: (LocationItem) -> Unit) {
        if (!isSetup) {
            isSetup = true
            binding.searchResults.adapter = adapter
            binding.backButton.setOnClickListener { onBack() }
            binding.clearButton.setOnClickListener { binding.searchInput.text?.clear() }
            binding.searchInput.doAfterTextChanged { onQuery(it?.toString().orEmpty()) }
        }
        adapter.onSelect = onSelect
        binding.searchProgress.visibility = if (state.searching) View.VISIBLE else View.GONE
        adapter.submit(state.searchResults)
        val hasQuery = !binding.searchInput.text.isNullOrBlank()
        val showEmpty = !state.searching && state.searchResults.isEmpty()
        binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
        binding.emptyState.text = state.searchError ?: if (hasQuery) "No cities found" else "Search for a city to add"
    }
}

class SearchBinder(private val view: SearchView) {
    fun bind(state: WeatherUiState, onBack: () -> Unit, onQuery: (String) -> Unit, onSelect: (LocationItem) -> Unit) = view.bind(state, onBack, onQuery, onSelect)
}

private class SearchAdapter(var onSelect: (LocationItem) -> Unit) : RecyclerView.Adapter<SearchAdapter.Holder>() {
    private var items: List<LocationItem> = emptyList()
    class Holder(val binding: ItemSearchResultBinding) : RecyclerView.ViewHolder(binding.root)
    fun submit(values: List<LocationItem>) { items = values; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.cityName.text = item.name
        holder.binding.citySubtitle.text = item.subtitle
        holder.binding.root.setOnClickListener { onSelect(item) }
    }
}
