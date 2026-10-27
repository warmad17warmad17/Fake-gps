package com.example.util

import android.content.Intent
import android.net.Uri
import com.example.data.repository.AddressSearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ParsedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val sourceDescription: String = ""
)

object SharedLocationParser {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val COORD_PATTERN = Pattern.compile("([-+]?\\d{1,2}\\.\\d{3,})[,\\s]+([-+]?\\d{1,3}\\.\\d{3,})")
    private val AT_COORD_PATTERN = Pattern.compile("/@([-+]?\\d{1,2}\\.\\d+),([-+]?\\d{1,3}\\.\\d+)")
    private val URL_PATTERN = Pattern.compile("(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)")

    /**
     * Parses an incoming Intent from WhatsApp, Google Maps, or any other app.
     * Can parse ACTION_VIEW (geo:, http:, https:) and ACTION_SEND (text/plain).
     */
    suspend fun parseIntent(intent: Intent, addressSearchManager: AddressSearchManager? = null): ParsedLocation? {
        val action = intent.action ?: return null

        if (action == Intent.ACTION_VIEW) {
            val data = intent.data ?: return null
            val directResult = parseUri(data)
            if (directResult != null) return directResult

            // If it's a URL that might redirect (e.g. goo.gl or maps.app.goo.gl)
            val scheme = data.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                val resolvedResult = resolveUrlCoordinates(data.toString())
                if (resolvedResult != null) return resolvedResult
            }

            // Check if geo: query is a pure text address that needs geocoding
            if (scheme == "geo" && addressSearchManager != null) {
                val ssp = data.schemeSpecificPart ?: ""
                var q: String? = null
                if (ssp.contains("?")) {
                    val qMatcher = Pattern.compile("(?:^|&)q=([^&]*)").matcher(ssp.substringAfter("?"))
                    if (qMatcher.find()) {
                        q = qMatcher.group(1)
                    }
                }
                if (q == null && data.isHierarchical) {
                    q = try { data.getQueryParameter("q") } catch (_: Exception) { null }
                }

                if (!q.isNullOrBlank()) {
                    val decodedQ = try { URLDecoder.decode(q, "UTF-8") } catch (_: Exception) { q }
                    val searchResults = addressSearchManager.searchAddress(decodedQ)
                    val first = searchResults.firstOrNull()
                    if (first != null) {
                        return ParsedLocation(
                            latitude = first.latitude,
                            longitude = first.longitude,
                            label = first.name.ifBlank { decodedQ },
                            sourceDescription = first.displayName
                        )
                    }
                }
            }
        } else if (action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return parseText(text, addressSearchManager)
        }

        return null
    }

    /**
     * Parses a Uri directly (synchronously).
     */
    fun parseUri(uri: Uri): ParsedLocation? {
        val scheme = uri.scheme?.lowercase() ?: return null

        when (scheme) {
            "geo" -> {
                // geo: URIs in Android are opaque by default: "geo:-6.2088,106.8456?z=16" or "geo:0,0?q=-6.2088,106.8456(Label)"
                // Scheme-specific part provides content after "geo:" safely without calling getQueryParameter directly
                val ssp = uri.schemeSpecificPart ?: ""
                val queryPart = if (ssp.contains("?")) ssp.substringAfter("?") else ""
                val coordPart = if (ssp.contains("?")) ssp.substringBefore("?") else ssp

                // 1. Check 'q' parameter in queryPart or hierarchical query
                var q: String? = null
                if (queryPart.isNotBlank()) {
                    val qMatcher = Pattern.compile("(?:^|&)q=([^&]*)").matcher(queryPart)
                    if (qMatcher.find()) {
                        q = qMatcher.group(1)
                    }
                }
                if (q == null && uri.isHierarchical) {
                    q = try { uri.getQueryParameter("q") } catch (_: Exception) { null }
                }

                if (!q.isNullOrBlank()) {
                    val decodedQ = try { URLDecoder.decode(q, "UTF-8") } catch (_: Exception) { q }

                    // Check for label in parenthesis: lat,lng(Label Name)
                    var label: String? = null
                    val labelMatcher = Pattern.compile("\\(([^)]+)\\)").matcher(decodedQ)
                    if (labelMatcher.find()) {
                        label = labelMatcher.group(1)?.trim()
                    }

                    val coordMatcher = COORD_PATTERN.matcher(decodedQ)
                    if (coordMatcher.find()) {
                        val lat = coordMatcher.group(1)?.toDoubleOrNull()
                        val lng = coordMatcher.group(2)?.toDoubleOrNull()
                        if (isValidCoord(lat, lng)) {
                            return ParsedLocation(lat!!, lng!!, label, "WhatsApp / Peta")
                        }
                    }
                }

                // 2. Parse schemeSpecificPart e.g. "geo:-6.2088,106.8456?z=16"
                val sspMatcher = COORD_PATTERN.matcher(coordPart)
                if (sspMatcher.find()) {
                    val lat = sspMatcher.group(1)?.toDoubleOrNull()
                    val lng = sspMatcher.group(2)?.toDoubleOrNull()
                    if (isValidCoord(lat, lng)) {
                        return ParsedLocation(lat!!, lng!!, null, "WhatsApp / Lokasi")
                    }
                }
            }
            "http", "https" -> {
                return parseHttpUrl(uri.toString())
            }
        }
        return null
    }

    /**
     * Parse coordinates from standard web Maps URLs.
     */
    fun parseHttpUrl(urlString: String): ParsedLocation? {
        val uri = try { Uri.parse(urlString) } catch (_: Exception) { null } ?: return null

        // 1. Check query parameter 'q', 'query', 'daddr', 'll' safely
        if (uri.isHierarchical) {
            val paramKeys = listOf("q", "query", "daddr", "saddr", "ll")
            for (key in paramKeys) {
                val param = try { uri.getQueryParameter(key) } catch (_: Exception) { null }
                if (!param.isNullOrBlank()) {
                    val decoded = try { URLDecoder.decode(param, "UTF-8") } catch (_: Exception) { param }
                    val matcher = COORD_PATTERN.matcher(decoded)
                    if (matcher.find()) {
                        val lat = matcher.group(1)?.toDoubleOrNull()
                        val lng = matcher.group(2)?.toDoubleOrNull()
                        if (isValidCoord(lat, lng)) {
                            return ParsedLocation(lat!!, lng!!, null, "Tautan Peta")
                        }
                    }
                }
            }
        }

        // 2. Check path pattern /@lat,lng e.g. https://www.google.com/maps/@-6.2088,106.8456,17z
        val atMatcher = AT_COORD_PATTERN.matcher(urlString)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lng = atMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoord(lat, lng)) {
                return ParsedLocation(lat!!, lng!!, null, "Tautan Google Maps")
            }
        }

        // 3. Check general coordinate pattern anywhere in URL
        val generalMatcher = COORD_PATTERN.matcher(urlString)
        if (generalMatcher.find()) {
            val lat = generalMatcher.group(1)?.toDoubleOrNull()
            val lng = generalMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoord(lat, lng)) {
                return ParsedLocation(lat!!, lng!!, null, "Tautan Peta")
            }
        }

        return null
    }

    /**
     * Parses plain text shared via ACTION_SEND.
     */
    suspend fun parseText(text: String, addressSearchManager: AddressSearchManager? = null): ParsedLocation? {
        val trimmed = text.trim()

        // 1. Check if the text contains a geo: URI
        val geoIndex = trimmed.indexOf("geo:")
        if (geoIndex >= 0) {
            val geoPart = trimmed.substring(geoIndex).split("\\s+".toRegex()).firstOrNull() ?: ""
            val parsedGeo = parseUri(Uri.parse(geoPart))
            if (parsedGeo != null) return parsedGeo
        }

        // 2. Check if the text contains an HTTP/HTTPS URL
        val urlMatcher = URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            val extractedUrl = urlMatcher.group(1)
            if (!extractedUrl.isNullOrBlank()) {
                val direct = parseHttpUrl(extractedUrl)
                if (direct != null) return direct

                // Try resolving redirect (for short URLs like maps.app.goo.gl)
                val resolved = resolveUrlCoordinates(extractedUrl)
                if (resolved != null) return resolved
            }
        }

        // 3. Check for explicit coordinate regex in the text (e.g. "-6.2088, 106.8456")
        val coordMatcher = COORD_PATTERN.matcher(trimmed)
        if (coordMatcher.find()) {
            val lat = coordMatcher.group(1)?.toDoubleOrNull()
            val lng = coordMatcher.group(2)?.toDoubleOrNull()
            if (isValidCoord(lat, lng)) {
                return ParsedLocation(lat!!, lng!!, null, "Teks Koordinat")
            }
        }

        // 4. Fallback: Search as address if address search manager provided
        if (addressSearchManager != null && trimmed.length in 3..100) {
            val results = addressSearchManager.searchAddress(trimmed)
            val first = results.firstOrNull()
            if (first != null) {
                return ParsedLocation(
                    latitude = first.latitude,
                    longitude = first.longitude,
                    label = first.name,
                    sourceDescription = first.displayName
                )
            }
        }

        return null
    }

    /**
     * Follows HTTP redirects for short URLs (like goo.gl/maps or maps.app.goo.gl)
     * and extracts coordinates from the final URL.
     */
    suspend fun resolveUrlCoordinates(urlString: String): ParsedLocation? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val parsed = parseHttpUrl(finalUrl)
                if (parsed != null) return@withContext parsed

                // Sometimes coordinates are in the HTML title or body
                val body = response.body?.string() ?: ""
                val atMatcher = AT_COORD_PATTERN.matcher(body)
                if (atMatcher.find()) {
                    val lat = atMatcher.group(1)?.toDoubleOrNull()
                    val lng = atMatcher.group(2)?.toDoubleOrNull()
                    if (isValidCoord(lat, lng)) {
                        return@withContext ParsedLocation(lat!!, lng!!, null, "Tautan Peta (Shortlink)")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun isValidCoord(lat: Double?, lng: Double?): Boolean {
        if (lat == null || lng == null) return false
        if (lat == 0.0 && lng == 0.0) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }
}
