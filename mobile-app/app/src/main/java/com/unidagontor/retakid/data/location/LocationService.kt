package com.unidagontor.retakid.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

data class LocationData(val latitude: Double, val longitude: Double)

class LocationService(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationData? {
        return try {
            val result = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()
            
            result?.let {
                LocationData(it.latitude, it.longitude)
            }
        } catch (e: Exception) {
            null
        }
    }
}
