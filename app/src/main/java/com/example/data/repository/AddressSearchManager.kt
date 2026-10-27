package com.example.data.repository

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.data.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos

class AddressSearchManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        // Maximum radius for prioritized local search (20 kilometers = 20,000 meters)
        const val MAX_NEARBY_RADIUS_METERS = 20_000f

        // Bounding box delta: 1 deg lat ~ 111km, so 20km ~ 0.18 degrees
        const val RADIUS_20KM_DEG = 0.18
    }

    suspend fun searchAddress(
        query: String,
        currentLat: Double? = null,
        currentLng: Double? = null,
        context: Context? = null,
        localFavorites: List<LocationFavoriteEntity> = emptyList(),
        localHistory: List<LocationHistoryEntity> = emptyList()
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val allResults = mutableListOf<SearchResultItem>()

        // 1. Search in local saved Favorites & History first
        searchLocalData(trimmed, currentLat, currentLng, localFavorites, localHistory, allResults)

        // 2. Search using Android's native Geocoder (powered by Google Play Services / Google Maps backend)
        if (context != null && Geocoder.isPresent()) {
            searchAndroidGeocoder(trimmed, currentLat, currentLng, context, allResults)
        }

        // 3. Search using Photon API with location bias
        searchPhoton(trimmed, currentLat, currentLng, allResults)

        // 4. Search using Nominatim with viewbox bounded near mock location
        searchNominatim(trimmed, currentLat, currentLng, allResults)

        // Deduplicate results and prioritize within 20km radius
        processAndRankResults(allResults, currentLat, currentLng)
    }

    private fun searchLocalData(
        query: String,
        currentLat: Double?,
        currentLng: Double?,
        favorites: List<LocationFavoriteEntity>,
        history: List<LocationHistoryEntity>,
        results: MutableList<SearchResultItem>
    ) {
        val qLower = query.lowercase(Locale.getDefault())

        // Check favorites
        for (fav in favorites) {
            val nameMatch = fav.name.lowercase(Locale.getDefault()).contains(qLower)
            val descMatch = fav.description.lowercase(Locale.getDefault()).contains(qLower)
            if (nameMatch || descMatch) {
                val dist = if (currentLat != null && currentLng != null) {
                    calculateDistanceMeters(currentLat, currentLng, fav.latitude, fav.longitude)
                } else null
                results.add(
                    SearchResultItem(
                        name = fav.name,
                        displayName = "Favorit: ${fav.description.ifBlank { "Lat: ${fav.latitude}, Lng: ${fav.longitude}" }}",
                        latitude = fav.latitude,
                        longitude = fav.longitude,
                        distanceMeters = dist,
                        isNearby = dist != null && dist <= MAX_NEARBY_RADIUS_METERS,
                        source = "Favorit"
                    )
                )
            }
        }

        // Check history
        for (hist in history) {
            val labelMatch = hist.label.lowercase(Locale.getDefault()).contains(qLower)
            if (labelMatch) {
                val dist = if (currentLat != null && currentLng != null) {
                    calculateDistanceMeters(currentLat, currentLng, hist.latitude, hist.longitude)
                } else null
                results.add(
                    SearchResultItem(
                        name = hist.label,
                        displayName = "Riwayat: Lat: ${hist.latitude}, Lng: ${hist.longitude}",
                        latitude = hist.latitude,
                        longitude = hist.longitude,
                        distanceMeters = dist,
                        isNearby = dist != null && dist <= MAX_NEARBY_RADIUS_METERS,
                        source = "Riwayat"
                    )
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun searchAndroidGeocoder(
        query: String,
        currentLat: Double?,
        currentLng: Double?,
        context: Context,
        results: MutableList<SearchResultItem>
    ) {
        try {
            val geocoder = Geocoder(context, Locale("id", "ID"))
            val addresses = mutableListOf<Address>()

            if (currentLat != null && currentLng != null) {
                val lngDelta = RADIUS_20KM_DEG / cos(Math.toRadians(currentLat)).coerceAtLeast(0.1)
                val lowerLeftLat = (currentLat - RADIUS_20KM_DEG).coerceIn(-90.0, 90.0)
                val upperRightLat = (currentLat + RADIUS_20KM_DEG).coerceIn(-90.0, 90.0)
                val lowerLeftLng = (currentLng - lngDelta).coerceIn(-180.0, 180.0)
                val upperRightLng = (currentLng + lngDelta).coerceIn(-180.0, 180.0)

                val boundedList = geocoder.getFromLocationName(
                    query,
                    8,
                    lowerLeftLat,
                    lowerLeftLng,
                    upperRightLat,
                    upperRightLng
                )
                if (!boundedList.isNullOrEmpty()) {
                    addresses.addAll(boundedList)
                }
            }

            // Also search without bounds if bounded gave few results
            if (addresses.size < 4) {
                val generalList = geocoder.getFromLocationName(query, 6)
                if (!generalList.isNullOrEmpty()) {
                    for (addr in generalList) {
                        if (addresses.none { abs(it.latitude - addr.latitude) < 0.0002 && abs(it.longitude - addr.longitude) < 0.0002 }) {
                            addresses.add(addr)
                        }
                    }
                }
            }

            for (addr in addresses) {
                val lat = addr.latitude
                val lon = addr.longitude
                if (lat != 0.0 && lon != 0.0) {
                    val placeName = addr.featureName?.takeIf { it.isNotBlank() }
                        ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                        ?: query
                    val lines = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }
                    val fullAddress = if (lines.isNotEmpty()) {
                        lines.joinToString(", ")
                    } else {
                        listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality, addr.adminArea, addr.countryName)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                    }

                    val dist = if (currentLat != null && currentLng != null) {
                        calculateDistanceMeters(currentLat, currentLng, lat, lon)
                    } else null

                    results.add(
                        SearchResultItem(
                            name = placeName,
                            displayName = fullAddress.ifBlank { placeName },
                            latitude = lat,
                            longitude = lon,
                            distanceMeters = dist,
                            isNearby = dist != null && dist <= MAX_NEARBY_RADIUS_METERS,
                            source = "Google Maps"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun searchPhoton(
        query: String,
        currentLat: Double?,
        currentLng: Double?,
        results: MutableList<SearchResultItem>
    ) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://photon.komoot.io/api/?q=$encoded&limit=15")
            if (currentLat != null && currentLng != null) {
                urlBuilder.append("&lat=$currentLat&lon=$currentLng")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "MyMapsApp/2.0 (my-maps@android.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val features = root.optJSONArray("features") ?: JSONArray()
                    for (i in 0 until features.length()) {
                        val feat = features.getJSONObject(i)
                        val props = feat.optJSONObject("properties") ?: continue
                        val geom = feat.optJSONObject("geometry") ?: continue
                        val coords = geom.optJSONArray("coordinates") ?: continue
                        val lon = coords.optDouble(0)
                        val lat = coords.optDouble(1)

                        val name = props.optString("name").ifBlank {
                            props.optString("street").ifBlank { query }
                        }
                        val street = props.optString("street")
                        val district = props.optString("district")
                        val city = props.optString("city")
                        val state = props.optString("state")
                        val country = props.optString("country")
                        val fullAddress = listOf(street, district, city, state, country)
                            .filter { it.isNotBlank() }
                            .joinToString(", ")

                        if (lat != 0.0 && lon != 0.0 && name.isNotBlank()) {
                            val dist = if (currentLat != null && currentLng != null) {
                                calculateDistanceMeters(currentLat, currentLng, lat, lon)
                            } else null

                            results.add(
                                SearchResultItem(
                                    name = name,
                                    displayName = if (fullAddress.isNotBlank()) "$name, $fullAddress" else name,
                                    latitude = lat,
                                    longitude = lon,
                                    distanceMeters = dist,
                                    isNearby = dist != null && dist <= MAX_NEARBY_RADIUS_METERS,
                                    source = "Peta Jalan"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun searchNominatim(
        query: String,
        currentLat: Double?,
        currentLng: Double?,
        results: MutableList<SearchResultItem>
    ) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val urlBuilder = StringBuilder("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&addressdetails=1&limit=10&accept-language=id")

            if (currentLat != null && currentLng != null) {
                val lngDelta = RADIUS_20KM_DEG / cos(Math.toRadians(currentLat)).coerceAtLeast(0.1)
                val lowerLeftLat = (currentLat - RADIUS_20KM_DEG).coerceIn(-90.0, 90.0)
                val upperRightLat = (currentLat + RADIUS_20KM_DEG).coerceIn(-90.0, 90.0)
                val lowerLeftLng = (currentLng - lngDelta).coerceIn(-180.0, 180.0)
                val upperRightLng = (currentLng + lngDelta).coerceIn(-180.0, 180.0)
                urlBuilder.append("&viewbox=$lowerLeftLng,$upperRightLat,$upperRightLng,$lowerLeftLat&bounded=0")
            }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "MyMapsApp/2.0 (my-maps@android.app)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val displayName = obj.optString("display_name")
                        val name = obj.optString("name").ifBlank {
                            displayName.split(",").firstOrNull()?.trim() ?: query
                        }
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        if (lat != 0.0 && lon != 0.0) {
                            val dist = if (currentLat != null && currentLng != null) {
                                calculateDistanceMeters(currentLat, currentLng, lat, lon)
                            } else null

                            results.add(
                                SearchResultItem(
                                    name = name,
                                    displayName = displayName,
                                    latitude = lat,
                                    longitude = lon,
                                    distanceMeters = dist,
                                    isNearby = dist != null && dist <= MAX_NEARBY_RADIUS_METERS,
                                    source = "Peta & Lokasi"
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processAndRankResults(
        rawList: List<SearchResultItem>,
        currentLat: Double?,
        currentLng: Double?
    ): List<SearchResultItem> {
        if (rawList.isEmpty()) return emptyList()

        // 1. Deduplicate by coordinates (within 15 meters) and name similarity
        val uniqueList = mutableListOf<SearchResultItem>()
        for (item in rawList) {
            val exists = uniqueList.any { existing ->
                (abs(existing.latitude - item.latitude) < 0.00015 && abs(existing.longitude - item.longitude) < 0.00015) ||
                (existing.name.equals(item.name, ignoreCase = true) && abs(existing.latitude - item.latitude) < 0.001)
            }
            if (!exists) {
                uniqueList.add(item)
            }
        }

        // 2. Partition into Nearby (within 20km) and Farther
        if (currentLat != null && currentLng != null) {
            val nearbyList = uniqueList.filter { it.distanceMeters != null && it.distanceMeters <= MAX_NEARBY_RADIUS_METERS }
                .sortedBy { it.distanceMeters }

            val fartherList = uniqueList.filter { it.distanceMeters == null || it.distanceMeters > MAX_NEARBY_RADIUS_METERS }
                .sortedBy { it.distanceMeters ?: Float.MAX_VALUE }

            // Primary: Return results within 20km radius sorted closest first
            return if (nearbyList.isNotEmpty()) {
                // If nearby results exist, prioritize them at the top
                (nearbyList + fartherList).take(15)
            } else {
                // If nothing within 20km, show the closest matches available
                fartherList.take(15)
            }
        }

        return uniqueList.take(15)
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}
