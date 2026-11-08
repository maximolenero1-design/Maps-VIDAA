package com.example.data.model

data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val title: String = "",
    val subtitle: String = "",
    val category: String = "general"
)

enum class MapLayerType(val displayName: String, val description: String) {
    OSM_STANDARD("Estándar", "Mapa mundial abierto de calles y detalles"),
    CARTO_LIGHT("Positron Claro", "Diseño limpio y minimalista"),
    CARTO_DARK("Dark Matter", "Modo noche oscuro de alto contraste"),
    SATELLITE("Satélite Esri", "Imágenes satelitales y aéreas de alta resolución"),
    TOPO("Topográfico", "Curvas de nivel, relieve y senderos")
}

enum class TravelMode(val displayName: String, val apiValue: String) {
    DRIVING("En auto", "driving"),
    WALKING("A pie", "walking"),
    CYCLING("En bici", "cycling")
}

data class SearchResultItem(
    val id: String,
    val name: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "",
    val category: String = "",
    val distanceMeters: Double? = null
)

data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val modifier: String? = null,
    val type: String? = null
)

data class RouteData(
    val points: List<MapPoint>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val steps: List<RouteStep>,
    val travelMode: TravelMode
)
