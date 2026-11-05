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
    val tags: List<String> = emptyList(),
    val cancelPolicy: String = "",
    val campPrice: Double = 0.0,
    val availableStartDate: String = "",       // yyyy-MM-dd
    val availableEndDate: String = "",         // yyyy-MM-dd
    val blockedDates: List<String> = emptyList()
) : Identifiable() {}