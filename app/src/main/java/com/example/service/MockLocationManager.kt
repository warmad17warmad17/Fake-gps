package com.example.service

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MockLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    fun isMockLocationPermitted(): Boolean {
        val testProvider = "fake_gps_test_probe"
        return try {
            try {
                locationManager.removeTestProvider(testProvider)
            } catch (ignored: Exception) {}

            locationManager.addTestProvider(
                testProvider,
                false, false, false, false, false, false, false,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE
            )
            locationManager.removeTestProvider(testProvider)
            true
        } catch (se: SecurityException) {
            Log.w("MockLocationManager", "Mock location not permitted: ${se.message}")
            false
        } catch (e: Exception) {
            // Some OEM ROMs might reject custom provider names with IllegalArgumentException,
            // but non-SecurityException means mock permissions are likely granted.
            true
        }
    }

    fun startMocking(): Result<Unit> {
        return try {
            // Enable mock mode on Fused Location Client
            try {
                fusedClient.setMockMode(true)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Warning on setMockMode(true)", e)
            }

            // Setup test providers in LocationManager
            for (provider in providers) {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (ignored: Exception) {
                    // Ignore if it was not already registered
                }

                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
            }
            Result.success(Unit)
        } catch (se: SecurityException) {
            Log.e("MockLocationManager", "SecurityException - Mock location not enabled in developer options", se)
            Result.failure(se)
        } catch (e: Exception) {
            Log.e("MockLocationManager", "Exception starting mock provider", e)
            Result.failure(e)
        }
    }

    fun injectLocation(latitude: Double, longitude: Double): Result<Unit> {
        return try {
            val nowMs = System.currentTimeMillis()
            val elapsedNanos = SystemClock.elapsedRealtimeNanos()

            for (provider in providers) {
                val mockLoc = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    altitude = 12.0
                    time = nowMs
                    elapsedRealtimeNanos = elapsedNanos
                    accuracy = 2.0f
                    speed = 0.0f
                    bearing = 0.0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 0.1f
                        verticalAccuracyMeters = 0.5f
                        speedAccuracyMetersPerSecond = 0.1f
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        isMock = true
                    }
                }

                try {
                    locationManager.setTestProviderLocation(provider, mockLoc)
                } catch (e: Exception) {
                    Log.w("MockLocationManager", "Failed to set test provider location for $provider", e)
                }
            }

            // Also inject into FusedLocationProviderClient
            val fusedMockLoc = Location("fused").apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 12.0
                time = nowMs
                elapsedRealtimeNanos = elapsedNanos
                accuracy = 2.0f
                speed = 0.0f
                bearing = 0.0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 0.1f
                    verticalAccuracyMeters = 0.5f
                    speedAccuracyMetersPerSecond = 0.1f
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isMock = true
                }
            }
            fusedClient.setMockLocation(fusedMockLoc)

            Result.success(Unit)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopMocking() {
        try {
            fusedClient.setMockMode(false)
        } catch (e: Exception) {
            Log.w("MockLocationManager", "Failed to disable fused mock mode", e)
        }

        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Failed to clean up test provider $provider", e)
            }
        }
    }
}
