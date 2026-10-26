package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    // History Queries
    @Query("SELECT * FROM location_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<LocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: LocationHistoryEntity): Long

    @Query("DELETE FROM location_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    @Query("DELETE FROM location_history")
    suspend fun clearAllHistory()

    // Favorites Queries
    @Query("SELECT * FROM location_favorites ORDER BY sortOrder ASC, id ASC")
    fun getAllFavorites(): Flow<List<LocationFavoriteEntity>>

    @Query("SELECT * FROM location_favorites ORDER BY sortOrder ASC, id ASC")
    fun getAllFavoritesList(): List<LocationFavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: LocationFavoriteEntity): Long

    @Update
    suspend fun updateFavorite(favorite: LocationFavoriteEntity)

    @Update
    suspend fun updateFavorites(favorites: List<LocationFavoriteEntity>)

    @Query("UPDATE location_favorites SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateFavoriteOrder(id: Long, sortOrder: Int)

    @Query("SELECT MAX(sortOrder) FROM location_favorites")
    suspend fun getMaxSortOrder(): Int?

    @Query("DELETE FROM location_favorites WHERE id = :id")
    suspend fun deleteFavorite(id: Long)

    @Query("SELECT * FROM location_favorites WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Long): LocationFavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(favorites: List<LocationFavoriteEntity>): List<Long>

    @Query("DELETE FROM location_favorites")
    suspend fun clearAllFavorites()
}
