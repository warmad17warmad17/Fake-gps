package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.LocationDao
import com.example.data.repository.LocationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HistoryDeduplicationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LocationDao
    private lateinit var repository: LocationRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.locationDao()
        repository = LocationRepository(dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testOnlyNewCoordinatesAddedToHistory() = runBlocking {
        // First mock at coordinate A
        val id1 = repository.addHistory(-7.253427, 112.707224, "Titik A")
        assertTrue(id1 > 0)

        // Mock again at the same coordinate A (should be rejected, keeping only the first one)
        val id2 = repository.addHistory(-7.253427, 112.707224, "Titik A Lagi")
        assertEquals(-1L, id2)

        val history = repository.allHistory.first()
        assertEquals(1, history.size)
        assertEquals("Titik A", history[0].label)
    }

    @Test
    fun testFavoritesAreNotAddedToHistory() = runBlocking {
        // Add coordinate as Favorite first
        repository.addFavorite("Kantor", -6.200000, 106.816666, "Kantor Pusat")

        // Try to add the same coordinate to history
        val histId = repository.addHistory(-6.200000, 106.816666, "Kantor")
        assertEquals(-1L, histId)

        val history = repository.allHistory.first()
        assertTrue(history.isEmpty())
    }

    @Test
    fun testHistoryAutoDeletedWhenSavedToFavorites() = runBlocking {
        // Initially in history
        val histId = repository.addHistory(-6.175392, 106.827153, "Monas")
        assertTrue(histId > 0)
        assertEquals(1, repository.allHistory.first().size)

        // User saves this coordinate to Favorites
        val favId = repository.addFavorite("Monas Favorit", -6.175392, 106.827153, "Wisata")
        assertTrue(favId > 0)

        // Verify history for that coordinate is automatically deleted!
        val historyAfter = repository.allHistory.first()
        assertTrue("History harus otomatis terhapus saat dijadikan favorit", historyAfter.isEmpty())

        val favorites = repository.allFavorites.first()
        assertEquals(1, favorites.size)
        assertEquals("Monas Favorit", favorites[0].name)
    }

    @Test
    fun testCleanAndDeduplicateHistory() = runBlocking {
        // Insert manually via DAO to simulate dirty/old data
        dao.insertHistory(com.example.data.model.LocationHistoryEntity(latitude = -7.1, longitude = 112.1, label = "First"))
        dao.insertHistory(com.example.data.model.LocationHistoryEntity(latitude = -7.1, longitude = 112.1, label = "Duplicate 1"))
        dao.insertHistory(com.example.data.model.LocationHistoryEntity(latitude = -7.1, longitude = 112.1, label = "Duplicate 2"))
        dao.insertHistory(com.example.data.model.LocationHistoryEntity(latitude = -8.0, longitude = 110.0, label = "To be favorite"))

        // Add favorite matching the -8.0, 110.0 coordinate
        dao.insertFavorite(com.example.data.model.LocationFavoriteEntity(name = "Jogja", latitude = -8.0, longitude = 110.0))

        // Run cleanup
        repository.cleanAndDeduplicateHistory()

        val history = repository.allHistory.first()
        assertEquals(1, history.size)
        assertEquals("First", history[0].label)
    }
}
