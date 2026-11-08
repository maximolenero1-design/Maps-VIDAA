package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.PlaceEntity
import com.example.data.model.MapLayerType
import com.example.data.model.MapPoint
import com.example.data.model.RouteData
import com.example.data.model.SearchResultItem
import com.example.util.TileSources
import kotlinx.coroutines.flow.SharedFlow
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapViewContainer(
    mapLayer: MapLayerType,
    selectedPlace: SearchResultItem?,
    userLocation: MapPoint?,
    activeRoute: RouteData?,
    savedPlaces: List<PlaceEntity>,
    isMeasuring: Boolean,
    measurePoints: List<MapPoint>,
    cameraEvents: SharedFlow<MapPoint>,
    onMapTapped: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize osmdroid configuration
    remember {
        Configuration.getInstance().userAgentValue = "GeoMap-Android-App/1.0"
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
    }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            isTilesScaledToDpi = true
            maxZoomLevel = 20.0
            minZoomLevel = 3.0
            controller.setZoom(14.0)

            // Initial center (e.g. Mexico City / Global Default)
            val initialLat = userLocation?.latitude ?: 19.4326
            val initialLon = userLocation?.longitude ?: -99.1332
            controller.setCenter(GeoPoint(initialLat, initialLon))

            // Add scale bar
            val scaleBar = ScaleBarOverlay(this)
            scaleBar.setCentred(true)
            scaleBar.setScaleBarOffset(200, 30)
            overlays.add(scaleBar)
        }
    }

    // Lifecycle handling for mapview
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Handle tile source changes
    LaunchedEffect(mapLayer) {
        val tileSource = TileSources.getTileSource(mapLayer)
        mapView.setTileSource(tileSource)
    }

    // Handle camera navigation events
    LaunchedEffect(cameraEvents) {
        cameraEvents.collect { point ->
            val target = GeoPoint(point.latitude, point.longitude)
            mapView.controller.animateTo(target, 16.0, 1000L)
        }
    }

    // Update overlays when state changes
    LaunchedEffect(
        selectedPlace,
        userLocation,
        activeRoute,
        savedPlaces,
        isMeasuring,
        measurePoints
    ) {
        // Keep scale bar and clear dynamic markers/routes
        mapView.overlays.removeAll { it is Marker || it is Polyline || it is MapEventsOverlay || it is MyLocationNewOverlay }

        // Tap event receiver
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    onMapTapped(p.latitude, p.longitude)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    onMapTapped(p.latitude, p.longitude)
                    return true
                }
                return false
            }
        }
        mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))

        // My location overlay
        val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            setDrawAccuracyEnabled(true)
        }
        mapView.overlays.add(myLocationOverlay)

        // Draw saved places pins
        savedPlaces.forEach { place ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(place.latitude, place.longitude)
                title = place.name
                snippet = place.address
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    onMapTapped(place.latitude, place.longitude)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // Draw selected place marker
        if (selectedPlace != null) {
            val placeMarker = Marker(mapView).apply {
                position = GeoPoint(selectedPlace.latitude, selectedPlace.longitude)
                title = selectedPlace.name
                snippet = selectedPlace.displayName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                showInfoWindow()
            }
            mapView.overlays.add(placeMarker)
        }

        // Draw active route polyline
        if (activeRoute != null && activeRoute.points.isNotEmpty()) {
            val routePolyline = Polyline(mapView).apply {
                outlinePaint.color = AndroidColor.parseColor("#0284C7")
                outlinePaint.strokeWidth = 14f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
                outlinePaint.isAntiAlias = true

                val geoPoints = activeRoute.points.map { GeoPoint(it.latitude, it.longitude) }
                setPoints(geoPoints)
            }
            mapView.overlays.add(routePolyline)

            // Add destination marker
            val lastPoint = activeRoute.points.last()
            val destMarker = Marker(mapView).apply {
                position = GeoPoint(lastPoint.latitude, lastPoint.longitude)
                title = "Destino"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(destMarker)
        }

        // Measurement overlay
        if (isMeasuring && measurePoints.isNotEmpty()) {
            val measurePolyline = Polyline(mapView).apply {
                outlinePaint.color = AndroidColor.parseColor("#F59E0B") // Amber
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.isAntiAlias = true
                setPoints(measurePoints.map { GeoPoint(it.latitude, it.longitude) })
            }
            mapView.overlays.add(measurePolyline)

            // Add points markers
            measurePoints.forEachIndexed { index, pt ->
                val ptMarker = Marker(mapView).apply {
                    position = GeoPoint(pt.latitude, pt.longitude)
                    title = "Punto ${index + 1}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(ptMarker)
            }
        }

        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    )
}
