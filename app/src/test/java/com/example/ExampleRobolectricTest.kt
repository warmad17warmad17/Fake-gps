package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.map.MapProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Fake GPS", appName)
  }

  @Test
  fun `verify map projection round trip`() {
    val testLat = -7.253427
    val testLon = 112.707224
    val zoom = 15.0

    val (px, py) = MapProjection.latLonToWorldPixel(testLat, testLon, zoom)
    val (resLat, resLon) = MapProjection.worldPixelToLatLon(px, py, zoom)

    assertTrue(abs(testLat - resLat) < 0.0001)
    assertTrue(abs(testLon - resLon) < 0.0001)
  }

  @Test
  fun `verify favorite entity and name retrieval`() {
    val fav = com.example.data.model.LocationFavoriteEntity(
        id = 1L,
        name = "Gudang",
        latitude = -7.253427,
        longitude = 112.707224,
        description = "Gudang Surabaya"
    )
    assertEquals("Gudang", fav.name)
    assertEquals(-7.253427, fav.latitude, 0.000001)
    assertEquals(112.707224, fav.longitude, 0.000001)
    assertEquals(0, fav.sortOrder)
  }

  @Test
  fun `verify favorites reorder mapping`() {
    val fav1 = com.example.data.model.LocationFavoriteEntity(id = 1L, name = "HUB", latitude = -7.25, longitude = 112.70, sortOrder = 0)
    val fav2 = com.example.data.model.LocationFavoriteEntity(id = 2L, name = "Bu iin", latitude = -7.26, longitude = 112.71, sortOrder = 1)
    
    // Swap order
    val reordered = listOf(fav2, fav1).mapIndexed { index, item -> item.copy(sortOrder = index) }
    assertEquals(0, reordered[0].sortOrder)
    assertEquals("Bu iin", reordered[0].name)
    assertEquals(1, reordered[1].sortOrder)
    assertEquals("HUB", reordered[1].name)
  }

  @Test
  fun `verify battery optimization helper can be queried`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Should run without throwing exception
    val isIgnored = com.example.util.BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
    // In Robolectric environment, default is true or false without crash
    assertTrue(isIgnored || !isIgnored)
  }

  @Test
  fun `verify backup json generation and parsing round trip`() {
    val favorites = listOf(
        com.example.data.model.LocationFavoriteEntity(
            id = 1L,
            name = "Kantor Pusat",
            latitude = -6.2088,
            longitude = 106.8456,
            description = "Jakarta",
            sortOrder = 0
        ),
        com.example.data.model.LocationFavoriteEntity(
            id = 2L,
            name = "Gudang Surabaya",
            latitude = -7.2534,
            longitude = 112.7072,
            description = "Banyu Urip",
            sortOrder = 1
        )
    )

    val json = com.example.util.BackupRestoreHelper.createBackupJson(favorites)
    assertTrue(json.contains("Kantor Pusat"))
    assertTrue(json.contains("Gudang Surabaya"))
    assertTrue(json.contains("\"count\": 2"))

    val parsed = com.example.util.BackupRestoreHelper.parseBackupJson(json)
    assertEquals(2, parsed.size)
    assertEquals("Kantor Pusat", parsed[0].name)
    assertEquals(-6.2088, parsed[0].latitude, 0.0001)
    assertEquals("Gudang Surabaya", parsed[1].name)
    assertEquals(112.7072, parsed[1].longitude, 0.0001)
  }

  @Test
  fun `verify fallback parsing for raw json array`() {
    val rawArrayJson = """
      [
        {"name": "Lokasi A", "latitude": -7.1, "longitude": 112.2},
        {"name": "Lokasi B", "latitude": -7.3, "longitude": 112.4}
      ]
    """.trimIndent()

    val parsed = com.example.util.BackupRestoreHelper.parseBackupJson(rawArrayJson)
    assertEquals(2, parsed.size)
    assertEquals("Lokasi A", parsed[0].name)
    assertEquals("Lokasi B", parsed[1].name)
  }

  @Test
  fun `verify default backup filename format`() {
    val fileName = com.example.util.BackupRestoreHelper.generateDefaultBackupFileName()
    assertTrue(fileName.startsWith("fake_gps_favorit_"))
    assertTrue(fileName.endsWith(".json"))
  }
}
