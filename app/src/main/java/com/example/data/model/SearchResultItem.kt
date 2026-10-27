package com.example.data.model

data class SearchResultItem(
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null,
    val isNearby: Boolean = false,
    val source: String = ""
) {
    fun formatDistance(): String? {
        if (distanceMeters == null) return null
        return if (distanceMeters < 1000f) {
            "${distanceMeters.toInt()} m"
        } else {
            String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000f)
        }
    }
}
