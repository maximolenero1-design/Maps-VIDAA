package com.example.util

import com.example.data.model.MapLayerType
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

object TileSources {
    val CARTO_LIGHT: ITileSource = XYTileSource(
        "CartoDB_Positron",
        0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/light_all/",
            "https://b.basemaps.cartocdn.com/light_all/",
            "https://c.basemaps.cartocdn.com/light_all/"
        ),
        "© OpenStreetMap, © CARTO"
    )

    val CARTO_DARK: ITileSource = XYTileSource(
        "CartoDB_DarkMatter",
        0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/"
        ),
        "© OpenStreetMap, © CARTO"
    )

    val ESRI_SATELLITE: ITileSource = XYTileSource(
        "Esri_WorldImagery",
        0, 19, 256, ".jpg",
        arrayOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"
        ),
        "© Esri, Maxar, Earthstar Geographics"
    )

    val OPEN_TOPO: ITileSource = XYTileSource(
        "OpenTopoMap",
        0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        ),
        "© OpenTopoMap, © OpenStreetMap"
    )

    fun getTileSource(type: MapLayerType): ITileSource {
        return when (type) {
            MapLayerType.OSM_STANDARD -> TileSourceFactory.MAPNIK
            MapLayerType.CARTO_LIGHT -> CARTO_LIGHT
            MapLayerType.CARTO_DARK -> CARTO_DARK
            MapLayerType.SATELLITE -> ESRI_SATELLITE
            MapLayerType.TOPO -> OPEN_TOPO
        }
    }
}
