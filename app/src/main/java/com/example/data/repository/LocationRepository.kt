package com.example.data.repository

import com.example.data.db.LocationDao
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class LocationRepository(private val locationDao: LocationDao) {

    companion object {
        // Precision threshold: ~0.0001 degrees latitude/longitude is ~11 meters
        const val COORD_TOLERANCE = 0.0001

        fun isSameLocation(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Boolean {
            return abs(lat1 - lat2) < COORD_TOLERANCE && abs(lng1 - lng2) < COORD_TOLERANCE
        }
    }

    val allHistory: Flow<List<LocationHistoryEntity>> = locationDao.getAllHistory()
    val allFavorites: Flow<List<LocationFavoriteEntity>> = locationDao.getAllFavorites()

    /**
     * Adds location to history ONLY if:
     * 1. It is not already saved in Favorites.
     * 2. It is not already in History (keeps the first recorded entry, prevents duplicate records).
     */
    suspend fun addHistory(latitude: Double, longitude: Double, label: String = ""): Long {
        // 1. Check if coordinate already exists in Favorites
        val favorites = locationDao.getAllFavoritesList()
        val isAlreadyFavorite = favorites.any { isSameLocation(it.latitude, it.longitude, latitude, longitude) }
        if (isAlreadyFavorite) {
            return -1L
        }

        // 2. Check if coordinate already exists in History (preserve the first recorded entry)
        val historyList = locationDao.getAllHistoryList()
        val isAlreadyInHistory = historyList.any { isSameLocation(it.latitude, it.longitude, latitude, longitude) }
        if (isAlreadyInHistory) {
            return -1L
        }

        return locationDao.insertHistory(
            LocationHistoryEntity(
                latitude = latitude,
                longitude = longitude,
                label = label.ifBlank { "Lat: %.6f, Lng: %.6f".format(latitude, longitude) }
            )
        )
    }

    /**
     * Automatically deletes any history entries matching the given coordinates.
     * Called when a location is added to Favorites.
     */
    suspend fun deleteHistoryMatchingCoordinates(latitude: Double, longitude: Double) {
        val historyList = locationDao.getAllHistoryList()
        val matchingIds = historyList.filter {
            isSameLocation(it.latitude, it.longitude, latitude, longitude)
        }.map { it.id }
        if (matchingIds.isNotEmpty()) {
            locationDao.deleteHistoryByIds(matchingIds)
        }
    }

    /**
     * Cleans up history:
     * - Removes any history record that matches an existing favorite.
     * - Deduplicates repeating coordinate records, keeping only the earliest occurrence.
     */
    suspend fun cleanAndDeduplicateHistory() {
        val favorites = locationDao.getAllFavoritesList()
        val historyList = locationDao.getAllHistoryList() // ordered by timestamp ASC (earliest first)

        val idsToDelete = mutableListOf<Long>()
        val seenCoordinates = mutableListOf<Pair<Double, Double>>()

        for (hist in historyList) {
            // Delete if already in favorites
            val inFavorites = favorites.any { isSameLocation(it.latitude, it.longitude, hist.latitude, hist.longitude) }
            if (inFavorites) {
                idsToDelete.add(hist.id)
                continue
            }

            // Keep only the first occurrence; mark duplicate subsequent occurrences for deletion
            val isDuplicate = seenCoordinates.any { isSameLocation(it.first, it.second, hist.latitude, hist.longitude) }
            if (isDuplicate) {
                idsToDelete.add(hist.id)
            } else {
                seenCoordinates.add(hist.latitude to hist.longitude)
            }
        }

        if (idsToDelete.isNotEmpty()) {
            locationDao.deleteHistoryByIds(idsToDelete)
        }
    }

    suspend fun deleteHistory(id: Long) {
        locationDao.deleteHistory(id)
    }

    suspend fun clearHistory() {
        locationDao.clearAllHistory()
    }

    suspend fun addFavorite(name: String, latitude: Double, longitude: Double, description: String = ""): Long {
        val nextSortOrder = (locationDao.getMaxSortOrder() ?: -1) + 1
        val favId = locationDao.insertFavorite(
            LocationFavoriteEntity(
                name = name.ifBlank { "Lokasi Favorit" },
                latitude = latitude,
                longitude = longitude,
                description = description,
                sortOrder = nextSortOrder
            )
        )
        // Automatically delete matching history entries because the coordinate is now a favorite
        deleteHistoryMatchingCoordinates(latitude, longitude)
        return favId
    }

    suspend fun updateFavorite(favorite: LocationFavoriteEntity) {
        locationDao.updateFavorite(favorite)
        deleteHistoryMatchingCoordinates(favorite.latitude, favorite.longitude)
    }

    suspend fun updateFavoritesOrder(reorderedList: List<LocationFavoriteEntity>) {
        val updated = reorderedList.mapIndexed { index, fav ->
            fav.copy(sortOrder = index)
        }
        locationDao.updateFavorites(updated)
    }

    suspend fun deleteFavorite(id: Long) {
        locationDao.deleteFavorite(id)
    }

    suspend fun getFavoriteById(id: Long): LocationFavoriteEntity? {
        return locationDao.getFavoriteById(id)
    }

    suspend fun restoreFavorites(favorites: List<LocationFavoriteEntity>, replaceExisting: Boolean): Int {
        if (replaceExisting) {
            locationDao.clearAllFavorites()
        }
        val startSortOrder = if (replaceExisting) 0 else ((locationDao.getMaxSortOrder() ?: -1) + 1)
        val entitiesToInsert = favorites.mapIndexed { index, item ->
            LocationFavoriteEntity(
                id = 0,
                name = item.name.ifBlank { "Lokasi Favorit" },
                latitude = item.latitude,
                longitude = item.longitude,
                description = item.description,
                timestamp = if (item.timestamp > 0) item.timestamp else System.currentTimeMillis(),
                sortOrder = startSortOrder + index
            )
        }
        locationDao.insertFavorites(entitiesToInsert)

        // Clean up any history records matching newly restored favorites
        cleanAndDeduplicateHistory()
        return entitiesToInsert.size
    }
}
