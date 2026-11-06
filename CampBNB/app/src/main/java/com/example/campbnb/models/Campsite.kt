package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

/**
 * A campsite listing an owner publishes: name, location, amenities, price, policies and
 * availability windows. Dates are ISO yyyy-MM-dd strings because that is the format
 * every date-based query in the app assumes; see [ReservationModel] for the same rule.
 */
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