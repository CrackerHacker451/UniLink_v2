package com.example.unilink.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import java.util.concurrent.Executors

object LocationHelper {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getLastLocation(context: Context, onResult: (lat: Double?, lng: Double?, city: String?, state: String?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onResult(null, null, null, null)
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    getAddressFromLocation(context, location.latitude, location.longitude) { city, state ->
                        onResult(location.latitude, location.longitude, city, state)
                    }
                } else {
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (lastLoc != null) {
                            getAddressFromLocation(context, lastLoc.latitude, lastLoc.longitude) { city, state ->
                                onResult(lastLoc.latitude, lastLoc.longitude, city, state)
                            }
                        } else {
                            onResult(null, null, null, null)
                        }
                    }.addOnFailureListener {
                        onResult(null, null, null, null)
                    }
                }
            }.addOnFailureListener {
                onResult(null, null, null, null)
            }
    }

    private fun getAddressFromLocation(context: Context, lat: Double, lng: Double, callback: (String?, String?) -> Unit) {
        val geocoder = Geocoder(context, Locale.getDefault())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1) { addresses ->
                mainHandler.post {
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val city = address.locality ?: address.subLocality ?: address.subAdminArea
                        val state = address.adminArea
                        callback(city, state)
                    } else {
                        callback(null, null)
                    }
                }
            }
        } else {
            executor.execute {
                try {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    mainHandler.post {
                        if (addresses?.isNotEmpty() == true) {
                            val address = addresses[0]
                            val city = address.locality ?: address.subLocality ?: address.subAdminArea
                            val state = address.adminArea
                            callback(city, state)
                        } else {
                            callback(null, null)
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post { callback(null, null) }
                }
            }
        }
    }
}
