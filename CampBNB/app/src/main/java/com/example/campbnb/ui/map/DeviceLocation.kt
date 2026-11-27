package com.example.campbnb.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.campbnb.models.LatLng
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/*
 * Gets the device's location once, for the campsite map. Uses Google's fused location client
 * (no API key needed). Any failure (no permission, no Play services, no fix) reports null and
 * the map falls back to a default centre.
 */

/** True when the user has granted fine or coarse location. */
fun hasLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
}

/** Reads the current location once and hands it to [onResult] (null if it cannot be read). */
@SuppressLint("MissingPermission")
fun fetchDeviceLocation(context: Context, onResult: (LatLng?) -> Unit) {
    if (!hasLocationPermission(context)) {
        onResult(null)
        return
    }
    val client = LocationServices.getFusedLocationProviderClient(context)
    val tokenSource = CancellationTokenSource()
    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
        .addOnSuccessListener { location ->
            if (location != null) {
                onResult(LatLng(location.latitude, location.longitude))
            } else {
                client.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) {
                            onResult(LatLng(last.latitude, last.longitude))
                        } else {
                            onResult(null)
                        }
                    }
                    .addOnFailureListener { onResult(null) }
            }
        }
        .addOnFailureListener { onResult(null) }
}

/**
 * Returns a function that asks for location permission if needed, then reads the location
 * and reports it to [onResult] (null if denied or unavailable).
 */
@Composable
fun rememberLocationRequester(onResult: (LatLng?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latestOnResult = rememberUpdatedState(onResult)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (hasLocationPermission(context)) {
            fetchDeviceLocation(context) { found -> latestOnResult.value(found) }
        } else {
            latestOnResult.value(null)
        }
    }

    return {
        if (hasLocationPermission(context)) {
            fetchDeviceLocation(context) { found -> latestOnResult.value(found) }
        } else {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}