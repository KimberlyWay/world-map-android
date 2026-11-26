package com.example.worldmap

/** Место на карте: результат поиска или то, на что нажали. zoom — насколько приближать. */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val zoom: Double
)
