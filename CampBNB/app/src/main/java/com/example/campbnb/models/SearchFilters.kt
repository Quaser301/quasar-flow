package com.example.campbnb.models


// obtained by prompting Claude Sonnet 5.5

/**
 * The filters an entrant can set when browsing campsites (US 01.01.02).
 *
 * An empty text or a null number means "no filter" for that field, so the default
 * [SearchFilters] lets every campsite through.
 *
 * Functions:
 *  - [activeCount]: how many of the filters are set.
 *
 * @property location Text the campsite's location must contain (ignoring case).
 * @property startDate First date wanted, yyyy-MM-dd. Empty for any date.
 * @property endDate Last date wanted, yyyy-MM-dd. Empty means only [startDate].
 * @property guests How many people the campsite must hold. Null for any number.
 */
data class SearchFilters(
    val location: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val guests: Int? = null
) {

    /** How many of the filters are set. */
    fun activeCount(): Int {
        var count = 0
        if (location.isNotBlank()) {
            count = count + 1
        }
        if (startDate.isNotEmpty()) {
            count = count + 1
        }
        if (guests != null) {
            count = count + 1
        }
        return count
    }
}