package com.example.campbnb.models

/**
 * The identities a user can hold: an entrant books and joins lotteries, an owner runs their
 * own campsites, an admin browses everything. A user document may hold more than one role,
 * and the app view a person sees is decided by which of these they hold.
 */
enum class Role { ENTRANT, OWNER, ADMIN }