package com.example.campbnb

/**
 * Represents a campsite that is registered by an owner
 * Stores the information of the campsite provided by the owner
 * Displays and updares the campsite listing
 */

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