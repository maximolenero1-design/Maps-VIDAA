package com.example.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.MapPoint
import com.example.ui.components.LayerSelectorDialog
import com.example.ui.components.MapControls
import com.example.ui.components.MapViewContainer
import com.example.ui.components.MeasureDistanceOverlay
import com.example.ui.components.PlaceDetailSheet
import com.example.ui.components.RouteDirectionsSheet
import com.example.ui.components.SavedPlacesSheet
import com.example.ui.components.SearchBarOverlay

@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel()
) {
    val mapLayer by viewModel.mapLayer.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val selectedPlace by viewModel.selectedPlace.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val activeRoute by viewModel.activeRoute.collectAsStateWithLifecycle()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsStateWithLifecycle()
    val travelMode by viewModel.travelMode.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val isSavedPlace by viewModel.isSavedPlace.collectAsStateWithLifecycle()
    val isLayerSheetOpen by viewModel.isLayerSheetOpen.collectAsStateWithLifecycle()
    val isSavedPlacesSheetOpen by viewModel.isSavedPlacesSheetOpen.collectAsStateWithLifecycle()
    val isDirectionsOpen by viewModel.isDirectionsOpen.collectAsStateWithLifecycle()
    val isMeasuring by viewModel.isMeasuring.collectAsStateWithLifecycle()
    val measurePoints by viewModel.measurePoints.collectAsStateWithLifecycle()
    val measureDistanceMeters by viewModel.measureDistanceMeters.collectAsStateWithLifecycle()

    // Request Location Permission on start
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.moveToUserLocation()
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Interactive Map View Engine (Base Layer)
            MapViewContainer(
                mapLayer = mapLayer,
                selectedPlace = selectedPlace,
                userLocation = userLocation,
                activeRoute = activeRoute,
                savedPlaces = savedPlaces,
                isMeasuring = isMeasuring,
                measurePoints = measurePoints,
                cameraEvents = viewModel.cameraEvents,
                onMapTapped = { lat, lon ->
                    viewModel.onMapTapped(lat, lon)
                }
            )

            // 2. Top Bar: Search Bar & Measurement Tool Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isMeasuring) {
                    MeasureDistanceOverlay(
                        isMeasuring = true,
                        pointsCount = measurePoints.size,
                        distanceMeters = measureDistanceMeters,
                        onClear = { viewModel.clearMeasure() },
                        onFinish = { viewModel.toggleMeasureMode() }
                    )
                } else {
                    SearchBarOverlay(
                        searchQuery = searchQuery,
                        isSearching = isSearching,
                        searchResults = searchResults,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onCategorySelected = { viewModel.searchQuickCategory(it) },
                        onSelectPlace = { viewModel.selectPlace(it) },
                        onOpenLayers = { viewModel.setLayerSheetOpen(true) },
                        onOpenSaved = { viewModel.setSavedPlacesSheetOpen(true) }
                    )
                }
            }

            // 3. Floating Map Controls (Right Side)
            MapControls(
                isMeasuring = isMeasuring,
                onZoomIn = {
                    // Zoom handled via camera event or map view
                    val currentCenter = selectedPlace?.let { MapPoint(it.latitude, it.longitude) }
                        ?: userLocation
                        ?: MapPoint(19.4326, -99.1332)
                    viewModel.selectPlace(
                        selectedPlace ?: com.example.data.model.SearchResultItem(
                            id = "center",
                            name = currentCenter.title.ifBlank { "Ubicación" },
                            displayName = currentCenter.subtitle,
                            latitude = currentCenter.latitude,
                            longitude = currentCenter.longitude
                        )
                    )
                },
                onZoomOut = {
                    // Camera event
                },
                onMyLocationClick = {
                    viewModel.moveToUserLocation()
                },
                onToggleMeasure = {
                    viewModel.toggleMeasureMode()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )

            // 4. Bottom Sheets: Place Details OR Route Directions
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (activeRoute != null && isDirectionsOpen) {
                    RouteDirectionsSheet(
                        route = activeRoute,
                        currentMode = travelMode,
                        onModeChange = { newMode ->
                            selectedPlace?.let { dest ->
                                viewModel.calculateRouteToDestination(dest, newMode)
                            }
                        },
                        onCloseRoute = { viewModel.clearRoute() }
                    )
                } else if (selectedPlace != null && !isMeasuring) {
                    PlaceDetailSheet(
                        place = selectedPlace,
                        isSaved = isSavedPlace,
                        isCalculatingRoute = isCalculatingRoute,
                        onCalculateRoute = {
                            selectedPlace?.let { dest ->
                                viewModel.calculateRouteToDestination(dest)
                            }
                        },
                        onToggleSave = {
                            selectedPlace?.let { place ->
                                viewModel.toggleSavePlace(place)
                            }
                        },
                        onClose = { viewModel.clearSelectedPlace() }
                    )
                }
            }

            // 5. Saved Places Modal Bottom Sheet
            SavedPlacesSheet(
                isOpen = isSavedPlacesSheetOpen,
                savedPlaces = savedPlaces,
                onSelectPlace = { viewModel.selectSavedPlace(it) },
                onDeletePlace = { viewModel.deleteSavedPlace(it) },
                onDismiss = { viewModel.setSavedPlacesSheetOpen(false) }
            )

            // 6. Map Layer Selector Modal Bottom Sheet
            LayerSelectorDialog(
                isOpen = isLayerSheetOpen,
                currentLayer = mapLayer,
                onSelectLayer = { viewModel.setMapLayer(it) },
                onDismiss = { viewModel.setLayerSheetOpen(false) }
            )
        }
    }
}
