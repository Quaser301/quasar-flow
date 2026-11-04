package com.example.campbnb.models

import com.example.campbnb.models.DateUtils
import com.google.firebase.firestore.FirebaseFirestore

class Availability(private val db: FirebaseFirestore /* , your existing params */) {

    /**
     * Gets the dates a campsite is open for booking, before subtracting existing reservations.
     *
     * The open dates are every date from the campsite's available start date to its available
     * end date, minus any owner-blocked dates and any date already in the past. Dates already
     * booked by reservations are removed later, by the controller.
     *
     * @param campsiteId The campsite to look up.
     * @param onSuccess Receives the open dates as yyyy-MM-dd strings. Empty if the owner
     * hasn't set an availability range.
     * @param onFailure Receives any Firestore error, or a parse error if a stored date is malformed.
     */
    fun getOpenDates(
        campsiteId: String,
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("Campsites").document(campsiteId).get()
            .addOnSuccessListener { doc ->
                try {
                    val start = doc.getString("availableStartDate")
                    val end = doc.getString("availableEndDate")
                    if (start == null || end == null) {
                        onSuccess(emptySet())
                        return@addOnSuccessListener
                    }
                    @Suppress("UNCHECKED_CAST")
                    val blocked = (doc.get("blockedDates") as? List<String>).orEmpty().toSet()
                    val today = DateUtils.today()

                    onSuccess(
                        DateUtils.datesBetween(start, end)
                            .filter { it >= today && it !in blocked }
                            .toSet()
                    )
                } catch (e: Exception) {
                    onFailure(e)
                }
            }
            .addOnFailureListener(onFailure)
    }
}