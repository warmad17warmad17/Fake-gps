package com.example.data.repository

import com.example.data.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AddressSearchManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {

    suspend fun searchAddress(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val results = mutableListOf<SearchResultItem>()

        // 1. Try Nominatim (OpenStreetMap) with Indonesian language bias
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&addressdetails=1&limit=6&accept-language=id"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "FakeGpsApp/1.4 (Android Map Geocoder)")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonArray = JSONArray(body)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val displayName = obj.optString("display_name")
                        val name = obj.optString("name").ifBlank {
                            displayName.split(",").firstOrNull()?.trim() ?: trimmed
                        }
                        val lat = obj.optDouble("lat", 0.0)
                        val lon = obj.optDouble("lon", 0.0)
                        if (lat != 0.0 && lon != 0.0) {
                            results.add(SearchResultItem(name = name, displayName = displayName, latitude = lat, longitude = lon))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. If no results, try Photon as fast fallback
        if (results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val photonUrl = "https://photon.komoot.io/api/?q=$encoded&limit=6&lang=id"
                val photonReq = Request.Builder()
                    .url(photonUrl)
                    .header("User-Agent", "FakeGpsApp/1.4")
                    .build()

                client.newCall(photonReq).execute().use { response ->
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
                            val name = props.optString("name").ifBlank { props.optString("street") }
                            val city = props.optString("city")
                            val state = props.optString("state")
                            val country = props.optString("country")
                            val full = listOf(name, city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                            if (lat != 0.0 && lon != 0.0 && name.isNotBlank()) {
                                results.add(SearchResultItem(name = name, displayName = full, latitude = lat, longitude = lon))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        results
    }
}
