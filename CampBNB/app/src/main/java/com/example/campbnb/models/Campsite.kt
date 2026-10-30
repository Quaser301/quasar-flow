package com.example.campbnb.models

class Campsite(
    val id: String = "",
    val ownerId: String = "",
    val CampName: String = "",
    val CampLoc: String = "",
    val CampDesc: String = "",
    val CampAmenities: List<String> = emptyList(),
    val geoLocation: String = "",
    val CampImg: String = "",
    val maxOccupancy: Int = 0,
    val tags: List<String> = emptyList()
) {}