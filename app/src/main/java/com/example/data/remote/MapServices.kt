package com.example.data.remote

import com.example.data.model.MapPoint
import com.example.data.model.RouteData
import com.example.data.model.RouteStep
import com.example.data.model.SearchResultItem
import com.example.data.model.TravelMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class MapServices {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val userAgent = "GeoMap-Android-App/1.0 (Android OS)"

    suspend fun searchPlaces(query: String, centerLat: Double? = null, centerLon: Double? = null): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val viewboxParam = if (centerLat != null && centerLon != null) {
            val delta = 0.5 // approx 50km box to prioritize nearby results
            val left = centerLon - delta
            val right = centerLon + delta
            val top = centerLat + delta
            val bottom = centerLat - delta
            "&viewbox=$left,$top,$right,$bottom"
        } else ""

        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=15$viewboxParam"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "es,en")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            if (!response.isSuccessful) return@withContext emptyList()

            val jsonArray = JSONArray(body)
            val results = mutableListOf<SearchResultItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("place_id", i.toString())
                val lat = obj.optString("lat").toDoubleOrNull() ?: continue
                val lon = obj.optString("lon").toDoubleOrNull() ?: continue
                val displayName = obj.optString("display_name", "")
                val name = obj.optString("name").ifBlank {
                    displayName.split(",").firstOrNull()?.trim() ?: displayName
                }
                val type = obj.optString("type", "")
                val category = obj.optString("class", "")

                var distanceMeters: Double? = null
                if (centerLat != null && centerLon != null) {
                    distanceMeters = computeDistanceMeters(centerLat, centerLon, lat, lon)
                }

                results.add(
                    SearchResultItem(
                        id = id,
                        name = name,
                        displayName = displayName,
                        latitude = lat,
                        longitude = lon,
                        type = type,
                        category = category,
                        distanceMeters = distanceMeters
                    )
                )
            }
            results
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): SearchResultItem? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&addressdetails=1"
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "es,en")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null

            val obj = JSONObject(body)
            val displayName = obj.optString("display_name", "")
            val name = obj.optString("name").ifBlank {
                val addressObj = obj.optJSONObject("address")
                addressObj?.optString("road")?.takeIf { it.isNotBlank() }
                    ?: addressObj?.optString("suburb")?.takeIf { it.isNotBlank() }
                    ?: displayName.split(",").firstOrNull()?.trim()
                    ?: "Ubicación seleccionada"
            }

            SearchResultItem(
                id = obj.optString("place_id", "pin"),
                name = name,
                displayName = displayName,
                latitude = lat,
                longitude = lon,
                type = obj.optString("type", "lugar"),
                category = obj.optString("class", "general")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun calculateRoute(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        travelMode: TravelMode = TravelMode.DRIVING
    ): RouteData? = withContext(Dispatchers.IO) {
        // OSRM profile: driving, walking, cycling
        val profile = travelMode.apiValue
        val url = "https://router.project-osrm.org/route/v1/$profile/$startLon,$startLat;$endLon,$endLat?overview=full&geometries=geojson&steps=true"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            if (!response.isSuccessful) return@withContext null

            val root = JSONObject(body)
            if (root.optString("code") != "Ok") return@withContext null

            val routes = root.getJSONArray("routes")
            if (routes.length() == 0) return@withContext null

            val route = routes.getJSONObject(0)
            val distance = route.optDouble("distance", 0.0)
            val duration = route.optDouble("duration", 0.0)

            // Geometry coordinates: [[lon, lat], [lon, lat], ...]
            val geometry = route.getJSONObject("geometry")
            val coordsArray = geometry.getJSONArray("coordinates")
            val points = mutableListOf<MapPoint>()
            for (i in 0 until coordsArray.length()) {
                val coord = coordsArray.getJSONArray(i)
                val lon = coord.getDouble(0)
                val lat = coord.getDouble(1)
                points.add(MapPoint(latitude = lat, longitude = lon))
            }

            // Steps
            val stepsList = mutableListOf<RouteStep>()
            val legs = route.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps")
                if (steps != null) {
                    for (i in 0 until steps.length()) {
                        val stepObj = steps.getJSONObject(i)
                        val stepDistance = stepObj.optDouble("distance", 0.0)
                        val stepDuration = stepObj.optDouble("duration", 0.0)
                        val streetName = stepObj.optString("name", "")
                        val maneuver = stepObj.optJSONObject("maneuver")
                        val mType = maneuver?.optString("type")
                        val mModifier = maneuver?.optString("modifier")

                        val instruction = formatInstruction(mType, mModifier, streetName, stepDistance)
                        stepsList.add(
                            RouteStep(
                                instruction = instruction,
                                distanceMeters = stepDistance,
                                durationSeconds = stepDuration,
                                modifier = mModifier,
                                type = mType
                            )
                        )
                    }
                }
            }

            RouteData(
                points = points,
                totalDistanceMeters = distance,
                totalDurationSeconds = duration,
                steps = stepsList,
                travelMode = travelMode
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun formatInstruction(type: String?, modifier: String?, streetName: String, distance: Double): String {
        val target = if (streetName.isNotBlank()) " en $streetName" else ""
        return when (type) {
            "depart" -> "Inicia el recorrido hacia el norte$target"
            "arrive" -> "Has llegado a tu destino"
            "turn" -> when (modifier) {
                "left" -> "Gira a la izquierda$target"
                "right" -> "Gira a la derecha$target"
                "sharp left" -> "Giro pronunciado a la izquierda$target"
                "sharp right" -> "Giro pronunciado a la derecha$target"
                "slight left" -> "Mantén la izquierda hacia$target"
                "slight right" -> "Mantén la derecha hacia$target"
                "uturn" -> "Da vuelta en U$target"
                else -> "Gira$target"
            }
            "new name", "continue" -> "Continúa recto$target"
            "roundabout" -> "Entra en la rotonda y toma la salida$target"
            "rotary" -> "En la rotonda toma la salida hacia$target"
            "merge" -> "Incorpórate a la vía$target"
            "fork" -> "En la bifurcación mantente a la ${if (modifier == "left") "izquierda" else "derecha"}$target"
            "end of road" -> "Al final de la calle gira a la ${if (modifier == "left") "izquierda" else "derecha"}$target"
            else -> if (streetName.isNotBlank()) "Avanza por $streetName" else "Continúa adelante"
        }
    }

    private fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
