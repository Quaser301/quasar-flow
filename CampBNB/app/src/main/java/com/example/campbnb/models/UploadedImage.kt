package com.example.campbnb.models

/*
 * UploadedImage.kt
 *
 * Role: model. One picture someone has uploaded, as the admin sees it when browsing the
 * uploaded images (US 03.06.01) and removing one (US 03.03.01). A view over saved campsites
 * and users: nothing here is written to the database.
 */

/** The two places a picture can be uploaded today. */
enum class ImageKind(val label: String) {
    /** The picture an owner gave a campsite (US 02.01.02). */
    CAMPSITE("Campsite"),

    /** The picture an entrant gave their profile (US 01.08.02). */
    PROFILE("Profile")
}

/**
 * One uploaded picture.
 *
 * @property kind Which kind of picture it is.
 * @property sourceId The id of the campsite or user the picture belongs to; the admin's
 * removal is addressed to it.
 * @property title The campsite's or user's display name.
 * @property image The picture as stored text (Base64 JPEG); never blank.
 */
class UploadedImage(
    val kind: ImageKind,
    val sourceId: String,
    val title: String,
    val image: String
) {
    /** A key no other picture shares, for lists and test tags. */
    val key: String get() = kind.name + "_" + sourceId
}

/**
 * Every uploaded picture of the given campsites and users: campsites first, then profiles,
 * each group ordered by name. Campsites and users with no picture are left out.
 */
fun uploadedImagesFor(campsites: List<Campsite>, users: List<User>): List<UploadedImage> {
    val found = mutableListOf<UploadedImage>()
    for (campsite in campsites.sortedBy { it.campName.lowercase() }) {
        if (campsite.campImg.isNotBlank()) {
            found.add(UploadedImage(ImageKind.CAMPSITE, campsite.id, campsite.campName.ifBlank { "(no name)" }, campsite.campImg))
        }
    }
    for (user in users.sortedBy { it.name.lowercase() }) {
        if (user.profileImg.isNotBlank()) {
            found.add(UploadedImage(ImageKind.PROFILE, user.id, user.name.ifBlank { "(no name)" }, user.profileImg))
        }
    }
    return found
}