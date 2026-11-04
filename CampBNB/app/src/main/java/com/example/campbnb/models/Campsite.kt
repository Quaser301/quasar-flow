package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

class Campsite(
    val ownerId: String = "",
    val campName: String = "",
    val campLoc: String = "",
    val campDesc: String = "",
    val campAmenities: List<String> = emptyList(),
    val geoLocation: String = "",
    val campImg: String = "",
    val maxOccupancy: Int = 0,
    val tags: List<String> = emptyList()
) : Identifiable()