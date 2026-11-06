package com.example.campbnb.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Small factory for ViewModels that need constructor parameters.
 */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> simpleViewModelFactory(
    create: () -> T
): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
            return create() as VM
        }
    }