package com.example.data.repository

import com.example.data.db.LocationDao
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {

    val allHistory: Flow<List<LocationHistoryEntity>> = locationDao.getAllHistory()
    val allFavorites: Flow<List<LocationFavoriteEntity>> = locationDao.getAllFavorites()

    suspend fun addHistory(latitude: Double, longitude: Double, label: String = ""): Long {
        return locationDao.insertHistory(
            LocationHistoryEntity(
                latitude = latitude,
                longitude = longitude,
                label = label.ifBlank { "Lat: %.6f, Lng: %.6f".format(latitude, longitude) }
            )
        )
    }

    suspend fun deleteHistory(id: Long) {
        locationDao.deleteHistory(id)
    }

    suspend fun clearHistory() {
        locationDao.clearAllHistory()
    }

    suspend fun addFavorite(name: String, latitude: Double, longitude: Double, description: String = ""): Long {
        val nextSortOrder = (locationDao.getMaxSortOrder() ?: -1) + 1
        return locationDao.insertFavorite(
            LocationFavoriteEntity(
                name = name.ifBlank { "Lokasi Favorit" },
                latitude = latitude,
                longitude = longitude,
                description = description,
                sortOrder = nextSortOrder
            )
        )
    }

    suspend fun updateFavorite(favorite: LocationFavoriteEntity) {
        locationDao.updateFavorite(favorite)
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
        return entitiesToInsert.size
    }
}
