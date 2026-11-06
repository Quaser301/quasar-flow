package com.example.campbnb.models

import com.example.campbnb.database.Identifiable
import com.example.campbnb.ui.components.UserRole


class User(val email: String, val name:String, val role: UserRole): Identifiable() {

}