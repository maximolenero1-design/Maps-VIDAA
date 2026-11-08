package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PlaceEntity
import com.example.data.model.MapLayerType
import com.example.data.model.MapPoint
import com.example.data.model.RouteData
import com.example.data.model.SearchResultItem
import com.example.data.model.TravelMode
import com.example.data.remote.MapServices
import com.example.util.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val placeDao = database.placeDao()
    private val mapServices = MapServices()
    private val locationHelper = LocationHelper(application)

    // UI States
    private val _mapLayer = MutableStateFlow(MapLayerType.OSM_STANDARD)
    val mapLayer: StateFlow<MapLayerType> = _mapLayer.asStateFlow()

    private val _userLocation = MutableStateFlow<MapPoint?>(null)
    val userLocation: StateFlow<MapPoint?> = _userLocation.asStateFlow()

    private val _selectedPlace = MutableStateFlow<SearchResultItem?>(null)
    val selectedPlace: StateFlow<SearchResultItem?> = _selectedPlace.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteData?>(null)
    val activeRoute: StateFlow<RouteData?> = _activeRoute.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _travelMode = MutableStateFlow(TravelMode.DRIVING)
    val travelMode: StateFlow<TravelMode> = _travelMode.asStateFlow()

    private val _savedPlaces = MutableStateFlow<List<PlaceEntity>>(emptyList())
    val savedPlaces: StateFlow<List<PlaceEntity>> = _savedPlaces.asStateFlow()

    private val _isSavedPlace = MutableStateFlow(false)
    val isSavedPlace: StateFlow<Boolean> = _isSavedPlace.asStateFlow()

    private val _isLayerSheetOpen = MutableStateFlow(false)
    val isLayerSheetOpen: StateFlow<Boolean> = _isLayerSheetOpen.asStateFlow()

    private val _isSavedPlacesSheetOpen = MutableStateFlow(false)
    val isSavedPlacesSheetOpen: StateFlow<Boolean> = _isSavedPlacesSheetOpen.asStateFlow()

    private val _isDirectionsOpen = MutableStateFlow(false)
    val isDirectionsOpen: StateFlow<Boolean> = _isDirectionsOpen.asStateFlow()

    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring: StateFlow<Boolean> = _isMeasuring.asStateFlow()

    private val _measurePoints = MutableStateFlow<List<MapPoint>>(emptyList())
    val measurePoints: StateFlow<List<MapPoint>> = _measurePoints.asStateFlow()

    private val _measureDistanceMeters = MutableStateFlow(0.0)
    val measureDistanceMeters: StateFlow<Double> = _measureDistanceMeters.asStateFlow()

    // Navigation events: center map to coordinates
    private val _cameraEvents = MutableSharedFlow<MapPoint>(extraBufferCapacity = 1)
    val cameraEvents: SharedFlow<MapPoint> = _cameraEvents.asSharedFlow()

    private var searchJob: Job? = null

    init {
        // Collect saved places from Room
        viewModelScope.launch {
            placeDao.getAllPlaces().collect { list ->
                _savedPlaces.value = list
                checkIfSelectedIsSaved()
            }
        }
        // Attempt initial location
        refreshUserLocation()
    }

    fun setMapLayer(layer: MapLayerType) {
        _mapLayer.value = layer
        _isLayerSheetOpen.value = false
    }

    fun setLayerSheetOpen(open: Boolean) {
        _isLayerSheetOpen.value = open
    }

    fun setSavedPlacesSheetOpen(open: Boolean) {
        _isSavedPlacesSheetOpen.value = open
    }

    fun setDirectionsOpen(open: Boolean) {
        _isDirectionsOpen.value = open
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // Debounce
            _isSearching.value = true
            val center = _userLocation.value
            val results = mapServices.searchPlaces(query, center?.latitude, center?.longitude)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun searchQuickCategory(category: String) {
        _searchQuery.value = category
        searchJob?.cancel()
        viewModelScope.launch {
            _isSearching.value = true
            val center = _userLocation.value ?: MapPoint(19.4326, -99.1332) // Default CDMX or user
            val results = mapServices.searchPlaces(category, center.latitude, center.longitude)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun selectPlace(place: SearchResultItem) {
        _selectedPlace.value = place
        _searchResults.value = emptyList()
        _searchQuery.value = ""
        checkIfSelectedIsSaved()
        _cameraEvents.tryEmit(MapPoint(place.latitude, place.longitude, place.name, place.displayName))
    }

    fun onMapTapped(latitude: Double, longitude: Double) {
        if (_isMeasuring.value) {
            addMeasurePoint(MapPoint(latitude, longitude))
            return
        }

        viewModelScope.launch {
            _selectedPlace.value = SearchResultItem(
                id = "temp_tap",
                name = "Ubicación seleccionada",
                displayName = "Cargando dirección...",
                latitude = latitude,
                longitude = longitude
            )
            checkIfSelectedIsSaved()

            val details = mapServices.reverseGeocode(latitude, longitude)
            if (details != null && _selectedPlace.value?.latitude == latitude) {
                _selectedPlace.value = details
                checkIfSelectedIsSaved()
            }
        }
    }

    fun clearSelectedPlace() {
        _selectedPlace.value = null
        _isSavedPlace.value = false
    }

    fun refreshUserLocation() {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                val point = MapPoint(location.latitude, location.longitude, "Mi ubicación", "GPS")
                _userLocation.value = point
            }
        }
    }

    fun moveToUserLocation() {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                val point = MapPoint(loc.latitude, loc.longitude, "Mi ubicación", "GPS")
                _userLocation.value = point
                _cameraEvents.tryEmit(point)
            } else if (_userLocation.value != null) {
                _cameraEvents.tryEmit(_userLocation.value!!)
            } else {
                // If no GPS yet, default to CDMX or notify
                val fallback = MapPoint(19.4326, -99.1332, "Centro", "Predeterminado")
                _cameraEvents.tryEmit(fallback)
            }
        }
    }

    fun calculateRouteToDestination(destination: SearchResultItem, mode: TravelMode = _travelMode.value) {
        _travelMode.value = mode
        viewModelScope.launch {
            _isCalculatingRoute.value = true
            // Use current location as start if available, otherwise a sensible origin
            val start = _userLocation.value ?: MapPoint(destination.latitude - 0.02, destination.longitude - 0.02)
            val route = mapServices.calculateRoute(
                startLat = start.latitude,
                startLon = start.longitude,
                endLat = destination.latitude,
                endLon = destination.longitude,
                travelMode = mode
            )
            _activeRoute.value = route
            _isCalculatingRoute.value = false
            if (route != null) {
                _isDirectionsOpen.value = true
            }
        }
    }

    fun clearRoute() {
        _activeRoute.value = null
        _isDirectionsOpen.value = false
    }

    fun toggleSavePlace(place: SearchResultItem, category: String = "favorite", notes: String = "") {
        viewModelScope.launch {
            val existing = placeDao.findPlaceNear(place.latitude, place.longitude)
            if (existing != null) {
                placeDao.deletePlace(existing)
                _isSavedPlace.value = false
            } else {
                val newEntity = PlaceEntity(
                    name = place.name.ifBlank { "Lugar guardado" },
                    address = place.displayName,
                    latitude = place.latitude,
                    longitude = place.longitude,
                    category = category,
                    notes = notes
                )
                placeDao.insertPlace(newEntity)
                _isSavedPlace.value = true
            }
        }
    }

    fun deleteSavedPlace(entity: PlaceEntity) {
        viewModelScope.launch {
            placeDao.deletePlace(entity)
        }
    }

    fun selectSavedPlace(entity: PlaceEntity) {
        val item = SearchResultItem(
            id = entity.id.toString(),
            name = entity.name,
            displayName = entity.address,
            latitude = entity.latitude,
            longitude = entity.longitude,
            type = entity.category
        )
        _isSavedPlacesSheetOpen.value = false
        selectPlace(item)
    }

    private fun checkIfSelectedIsSaved() {
        val current = _selectedPlace.value ?: run {
            _isSavedPlace.value = false
            return
        }
        val isSaved = _savedPlaces.value.any {
            Math.abs(it.latitude - current.latitude) < 0.0001 &&
            Math.abs(it.longitude - current.longitude) < 0.0001
        }
        _isSavedPlace.value = isSaved
    }

    // Measurement tool
    fun toggleMeasureMode() {
        val next = !_isMeasuring.value
        _isMeasuring.value = next
        if (!next) {
            clearMeasure()
        }
    }

    fun addMeasurePoint(point: MapPoint) {
        val current = _measurePoints.value.toMutableList()
        current.add(point)
        _measurePoints.value = current

        // Recalculate distance
        var total = 0.0
        for (i in 0 until current.size - 1) {
            val p1 = current[i]
            val p2 = current[i + 1]
            total += computeDirectDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        _measureDistanceMeters.value = total
    }

    fun clearMeasure() {
        _measurePoints.value = emptyList()
        _measureDistanceMeters.value = 0.0
    }

    private fun computeDirectDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
