package com.example.campbnb.controller

import com.example.campbnb.models.DateUtils
import com.example.campbnb.data.LotteryRepository
import com.example.campbnb.models.LotteryModel
import com.google.firebase.firestore.FirebaseFirestore

class LotteryController (
    private val model: LotteryModel,
    private val repo: LotteryRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val db: FirebaseFirestore
){
    // E, D, A, F
    fun onBackMonitorClicked() {
        TODO()
    }

    /**
     * Checks whether an entrant won the lottery for a specific lottery entry.
     *
     * Returns false, not an error, if the entry doesn't exist, belongs to someone else, or
     * wasn't a win. This is the "if and only if I won" check the reservation flow depends on.
     *
     * @param lotteryEntryId The entry to check.
     * @param entrantId The entrant claiming the win. Must match the entry's owner.
     * @param onSuccess Receives true only if the entry is theirs and its status is WON.
     * @param onFailure Receives any Firestore error.
     * @author Zino
     */
    fun hasWon(
        lotteryEntryId: String,
        entrantId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("LotteryEntries").document(lotteryEntryId).get()
            .addOnSuccessListener { doc ->
                onSuccess(
                    doc.exists() &&
                            doc.getString("entrantId") == entrantId &&
                            doc.getString("status") == "WON"
                )
            }
            .addOnFailureListener(onFailure)
    }

    /**
     * Checks whether a campsite's booking period is open today.
     *
     * The period is inclusive of both its open and close dates. A campsite with no booking
     * period set is treated as closed.
     *
     * @param campsiteId The campsite to check.
     * @param onSuccess Receives true if today falls inside the booking period.
     * @param onFailure Receives any Firestore error.
     */
    fun isBookingOpen(
        campsiteId: String,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection("Campsites").document(campsiteId).get()
            .addOnSuccessListener { doc ->
                val opens = doc.getString("bookingOpenDate")
                val closes = doc.getString("bookingCloseDate")
                val today = DateUtils.today()
                onSuccess(opens != null && closes != null && today >= opens && today <= closes)
            }
            .addOnFailureListener(onFailure)
    }
}