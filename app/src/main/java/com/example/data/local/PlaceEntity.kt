package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val latitude: Double,
    val longitude: Double,
    val category: String = "favorite", // favorite, home, work, food, travel, pin
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
