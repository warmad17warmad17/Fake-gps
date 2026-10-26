package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LocationFavoriteEntity
import com.example.data.model.LocationHistoryEntity
import com.example.data.repository.LocationRepository
import com.example.service.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.widget.FavoritesWidgetProvider

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocationRepository = (application as FakeGpsApp).repository

    // Initial coordinates matching Surabaya - Gerbang Tol Banyu Urip from screenshot
    private val _selectedLat = MutableStateFlow(-7.253427)
    val selectedLat = _selectedLat.asStateFlow()

    private val _selectedLng = MutableStateFlow(112.707224)
    val selectedLng = _selectedLng.asStateFlow()

    // Filter states
    private val _showHistory = MutableStateFlow(true)
    val showHistory = _showHistory.asStateFlow()

    private val _showFavorites = MutableStateFlow(true)
    val showFavorites = _showFavorites.asStateFlow()

    // Database flows
    val historyList: StateFlow<List<LocationHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val favoritesList: StateFlow<List<LocationFavoriteEntity>> = repository.allFavorites
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Service state
    val isMocking: StateFlow<Boolean> = MockLocationService.isMockingRunning
    val mockError: StateFlow<String?> = MockLocationService.mockError

    init {
        viewModelScope.launch {
            val existingFavs = repository.allFavorites.first()
            if (existingFavs.isEmpty()) {
                repository.addFavorite(
                    name = "Gudang",
                    latitude = -7.253427,
                    longitude = 112.707224,
                    description = "Gudang Surabaya"
                )
            }
            val existingHist = repository.allHistory.first()
            if (existingHist.isEmpty()) {
                repository.addHistory(-7.255100, 112.698000, "Jl. Tj. Sari")
                repository.addHistory(-7.247500, 112.716000, "Jl. Dupak Rukun")
            }
        }
    }

    fun selectLocation(lat: Double, lng: Double, fromUserAction: Boolean = false) {
        _selectedLat.value = lat
        _selectedLng.value = lng

        // If actively mocking, update the active mock position seamlessly
        if (isMocking.value) {
            MockLocationService.updateLocation(getApplication(), lat, lng)
        }
    }

    fun startMock(context: Context) {
        val mockManager = com.example.service.MockLocationManager(context)
        if (!mockManager.isMockLocationPermitted()) {
            MockLocationService.setMockError("Aplikasi belum diatur sebagai 'Aplikasi Lokasi Tiruan' di Opsi Pengembang (Developer Options).\n\nSilakan buka Opsi Pengembang, cari 'Pilih aplikasi lokasi tiruan', lalu pilih 'Fake GPS'.")
            return
        }

        val lat = _selectedLat.value
        val lng = _selectedLng.value

        val currentFav = favoritesList.value.find {
            kotlin.math.abs(it.latitude - lat) < 0.0001 && kotlin.math.abs(it.longitude - lng) < 0.0001
        }

        MockLocationService.start(
            context = context,
            latitude = lat,
            longitude = lng,
            locationName = currentFav?.name,
            favoriteId = currentFav?.id ?: -1L
        )

        // Save to History when started
        viewModelScope.launch {
            repository.addHistory(lat, lng, currentFav?.name ?: "")
        }
    }

    fun stopMock(context: Context) {
        MockLocationService.stop(context)
    }

    fun clearMockError() {
        MockLocationService.clearError()
    }

    fun toggleFilterHistory(show: Boolean) {
        _showHistory.value = show
    }

    fun toggleFilterFavorites(show: Boolean) {
        _showFavorites.value = show
    }

    fun addFavorite(name: String, lat: Double, lng: Double, description: String = "") {
        viewModelScope.launch {
            repository.addFavorite(name, lat, lng, description)
            FavoritesWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun updateFavorite(favorite: LocationFavoriteEntity) {
        viewModelScope.launch {
            repository.updateFavorite(favorite)
            FavoritesWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deleteFavorite(favorite: LocationFavoriteEntity) {
        viewModelScope.launch {
            repository.deleteFavorite(favorite.id)
            FavoritesWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun reorderFavorites(newOrderList: List<LocationFavoriteEntity>) {
        viewModelScope.launch {
            repository.updateFavoritesOrder(newOrderList)
            FavoritesWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    fun deleteHistory(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
