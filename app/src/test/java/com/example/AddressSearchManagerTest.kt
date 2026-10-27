package com.example

import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.data.model.SearchResultItem
import com.example.data.repository.AddressSearchManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AddressSearchManagerTest {

    private val searchManager = AddressSearchManager()

    @Test
    fun testDistanceCalculation() {
        // Distance between Surabaya Gerbang Tol Banyu Urip and nearby location (~250m)
        val lat1 = -7.253427
        val lon1 = 112.707224
        val lat2 = -7.253900
        val lon2 = 112.709000

        val dist = searchManager.calculateDistanceMeters(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be around 200m", dist in 150f..350f)
    }

    @Test
    fun testSearchResultItemFormatDistance() {
        val nearItem = SearchResultItem("Warung Bu Iin", "Asem Mulya", -7.24, 112.71, distanceMeters = 350f, isNearby = true)
        assertEquals("350 m", nearItem.formatDistance())

        val farItem = SearchResultItem("Kantor Pusat", "Jakarta", -6.20, 106.81, distanceMeters = 5400f, isNearby = true)
        assertEquals("5.4 km", farItem.formatDistance())
    }

    @Test
    fun testLocalFavoritesAndHistoryPriority() = runBlocking {
        val mockLat = -7.247659
        val mockLng = 112.716047

        val favorites = listOf(
            LocationFavoriteEntity(id = 1L, name = "Warung Sembako Bu Iin", latitude = -7.247660, longitude = 112.716050, description = "Toko Kelontong")
        )
        val history = listOf(
            LocationHistoryEntity(id = 1L, label = "Warung Jihan", latitude = -7.248000, longitude = 112.715000)
        )

        val results = searchManager.searchAddress(
            query = "Warung",
            currentLat = mockLat,
            currentLng = mockLng,
            localFavorites = favorites,
            localHistory = history
        )

        assertTrue(results.isNotEmpty())
        val first = results.first()
        assertTrue("First result should be close local favorite", first.name.contains("Warung"))
        assertNotNull(first.distanceMeters)
        assertTrue("Distance should be under 20km", first.distanceMeters!! <= 20_000f)
        assertTrue(first.isNearby)
    }
}
