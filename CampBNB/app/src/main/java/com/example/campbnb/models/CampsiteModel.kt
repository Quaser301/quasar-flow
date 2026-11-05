package com.example.campbnb.models

import com.example.campbnb.database.Identifiable

class CampsiteModel {
    data class CampsiteModel(

        val availableStartDate: String = "",       // yyyy-MM-dd
        val availableEndDate: String = "",         // yyyy-MM-dd
        val blockedDates: List<String> = emptyList()
    ) : Identifiable()}