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
}
