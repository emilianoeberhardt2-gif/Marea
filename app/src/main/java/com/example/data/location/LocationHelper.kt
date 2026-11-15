package com.example.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationHelper {
    fun hasLocationPermission(context: Context): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    fun getLastKnownCoordinates(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var bestLocation: Location? = null
        for (provider in providers) {
            val loc = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (bestLocation == null || loc.time > bestLocation.time) {
                bestLocation = loc
            }
        }
        return bestLocation?.let { it.latitude to it.longitude }
    }

    @SuppressLint("MissingPermission")
    suspend fun getFreshOrLastCoordinates(context: Context): Pair<Double, Double>? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.GPS_PROVIDER
                runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                val fresh = withTimeoutOrNull(4_000L) {
                    suspendCancellableCoroutine { cont ->
                        val signal = CancellationSignal()
                        cont.invokeOnCancellation { signal.cancel() }
                        runCatching {
                            manager.getCurrentLocation(
                                provider,
                                signal,
                                ContextCompat.getMainExecutor(context)
                            ) { loc ->
                                if (cont.isActive) {
                                    cont.resume(loc?.let { it.latitude to it.longitude })
                                }
                            }
                        }.onFailure {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                if (fresh != null) return fresh
            }
        }
        return getLastKnownCoordinates(context)
    }
}
