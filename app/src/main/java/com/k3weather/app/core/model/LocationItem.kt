package com.k3weather.app.core.model

data class LocationItem(
    val id: String,
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "Asia/Ho_Chi_Minh",
    val isCurrentLocation: Boolean = false
) {
    val displayName: String
        get() = when {
            region.isNotBlank() && region != name -> "$name, $region"
            country.isNotBlank() -> "$name, $country"
            else -> name
        }

    val subtitle: String
        get() = when {
            region.isNotBlank() && country.isNotBlank() && region != name -> "$region, $country"
            country.isNotBlank() -> country
            else -> region
        }

    companion object {
        val DEFAULT_DA_LAT = LocationItem(
            id = "1584071",
            name = "Đà Lạt",
            region = "Lâm Đồng",
            country = "Vietnam",
            latitude = 11.94646,
            longitude = 108.44193,
            timezone = "Asia/Ho_Chi_Minh",
            isCurrentLocation = false
        )
    }
}
