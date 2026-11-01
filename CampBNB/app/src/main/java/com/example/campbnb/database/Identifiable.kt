package com.example.campbnb.database

import com.google.firebase.firestore.DocumentId
import java.util.UUID

/**
 * Automatically provides a [UUID] to any new data class that inherits it.
 *
 * All data classes must be [Identifiable] to enable writing via [WriteEngine].
 *
 * NOTE: During reading, Firestore instantiates an empty class value. Because this class creates a
 * UUID on instantiation, `id` is temporarily set to a random value before Firestore reads
 * the [DocumentId] annotation and repopulates it. It is not expected for this to cause bugs.
 *
 * @property id The random UUID set on instantiation.
 */
abstract class Identifiable {
    @get:DocumentId
    @set:DocumentId
    open var id: String = UUID.randomUUID().toString()
}