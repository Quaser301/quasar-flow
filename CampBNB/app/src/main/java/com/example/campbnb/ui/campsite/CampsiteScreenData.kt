package com.example.campbnb.ui.campsite

import com.example.campbnb.models.Campsite
import com.example.campbnb.models.CampsiteModel
import com.example.campbnb.ui.Screens.CampsiteCardData
import com.example.campbnb.ui.details.CampsiteDetails

/** Converts a campsite into the summary card shown in lists (US 01.01.04). */
fun toCardData(campsite: Campsite, model: CampsiteModel): CampsiteCardData {
    return CampsiteCardData(
        id = campsite.id,
        name = campsite.campName,
        location = campsite.campLoc,
        maxOccupancy = campsite.maxOccupancy,
        rating = model.averageRating(campsite.id),
        ratingCount = model.ratingCount(campsite.id)
    )
}

/** Converts a list of campsites into summary cards. */
fun toCardList(campsites: List<Campsite>, model: CampsiteModel): List<CampsiteCardData> {
    val cards = mutableListOf<CampsiteCardData>()
    for (campsite in campsites) {
        cards.add(toCardData(campsite, model))
    }
    return cards
}

/** Converts a campsite into what the Details screen shows (US 01.01.05). */
fun toDetails(campsite: Campsite, model: CampsiteModel): CampsiteDetails {
    return CampsiteDetails(
        id = campsite.id,
        name = campsite.campName,
        rating = model.averageRating(campsite.id),
        reviewCount = model.ratingCount(campsite.id),
        tags = campsite.tags,
        capacityGuests = campsite.maxOccupancy,
        location = campsite.campLoc,
        description = campsite.campDesc
    )
}