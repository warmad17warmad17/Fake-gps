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
  }
}
