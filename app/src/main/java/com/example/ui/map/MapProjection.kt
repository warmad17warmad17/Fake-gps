package com.example.ui.map

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

object MapProjection {
    const val TILE_SIZE = 256.0

    fun latLonToWorldPixel(lat: Double, lon: Double, zoom: Double): Pair<Double, Double> {
        val scale = Math.pow(2.0, zoom) * TILE_SIZE
        val x = (lon + 180.0) / 360.0 * scale
        val sinLat = min(max(Math.sin(lat * PI / 180.0), -0.9999), 0.9999)
        val y = (0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)) * scale
        return Pair(x, y)
    }

    fun worldPixelToLatLon(x: Double, y: Double, zoom: Double): Pair<Double, Double> {
        val scale = Math.pow(2.0, zoom) * TILE_SIZE
        val lon = (x / scale) * 360.0 - 180.0
        val n = PI - (2.0 * PI * y) / scale
        val lat = (180.0 / PI) * atan(0.5 * (exp(n) - exp(-n)))
        return Pair(lat, lon)
    }

    fun latLonToTile(lat: Double, lon: Double, zoomInt: Int): Pair<Int, Int> {
        val n = 1 shl zoomInt
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt()
        return Pair(x, y)
    }
}
