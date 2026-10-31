package com.example.service

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.random.Random

/**
 * Manages Mock Location injection modeled after industry standard fake GPS implementations
 * (such as com.blogspot.newapphorizons.fakegps / Lexa Fake GPS).
 *
 * Key criteria for maximum compatibility and realistic emulation:
 * 1. Hardware-accurate provider criteria (GPS requires satellite and POWER_HIGH; Network requires cell/network).
 * 2. Realistic GPS accuracy (3.0m - 4.8m) instead of artificial flat 1.0m.
 * 3. Complete satellite and constellation metadata in extras.
 * 4. Realistic altitude, vertical accuracy, and speed/bearing metrics.
 * 5. No explicit manual `isMock = true` flagging.
 * 6. Clean separation: GPS and Network handled by LocationManager; Fused handled by FusedLocationProviderClient.
 */
class MockLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    private val testProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    private var isMockingActive = false

    // Base realistic altitude (meters above sea level) with micro-fluctuation
    private var baseAltitude = 24.5

    fun isMockLocationPermitted(): Boolean {
        val testProvider = "fake_gps_test_probe"
        return try {
            try {
                locationManager.removeTestProvider(testProvider)
            } catch (_: Exception) {}

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
        } catch (_: Exception) {
            true
        }
    }

    fun startMocking(): Result<Unit> {
        if (isMockingActive) {
            return Result.success(Unit)
        }

        return try {
            // 1. Enable mock mode in Google Play Services Fused Location Client
            try {
                fusedClient.setMockMode(true)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Warning setting fused mock mode", e)
            }

            // 2. Register GPS_PROVIDER with authentic GPS hardware criteria
            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {}

            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, // requiresNetwork
                true,  // requiresSatellite (real GPS requires satellites!)
                false, // requiresCell
                false, // hasMonetaryCost
                true,  // supportsAltitude
                true,  // supportsSpeed
                true,  // supportsBearing
                Criteria.POWER_HIGH, // Real GPS receiver has high power requirement
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

            @Suppress("DEPRECATION")
            locationManager.setTestProviderStatus(
                LocationManager.GPS_PROVIDER,
                android.location.LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
            )

            // 3. Register NETWORK_PROVIDER with authentic Network criteria
            try {
                locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {}

            locationManager.addTestProvider(
                LocationManager.NETWORK_PROVIDER,
                true,  // requiresNetwork
                false, // requiresSatellite
                true,  // requiresCell (cell/wifi)
                false, // hasMonetaryCost
                false, // supportsAltitude
                false, // supportsSpeed
                false, // supportsBearing
                Criteria.POWER_LOW,
                Criteria.ACCURACY_COARSE
            )
            locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

            @Suppress("DEPRECATION")
            locationManager.setTestProviderStatus(
                LocationManager.NETWORK_PROVIDER,
                android.location.LocationProvider.AVAILABLE,
                null,
                System.currentTimeMillis()
            )

            isMockingActive = true
            Result.success(Unit)
        } catch (se: SecurityException) {
            Log.e("MockLocationManager", "SecurityException starting mock provider", se)
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

            // Realistic accuracy between 3.2m and 4.6m (never a static, synthetic 1.0f)
            val randomFactor = (Random.nextInt(0, 15) - 7) * 0.1f // -0.7f to +0.7f
            val realisticGpsAccuracy = 3.8f + randomFactor
            val realisticAltitude = baseAltitude + ((Random.nextInt(0, 10) - 5) * 0.05)

            // 1. Authentic GPS Location with satellite constellation extras
            val gpsExtras = Bundle().apply {
                putInt("satellites", 12)
                putInt("maxSatellites", 16)
                putString("extraType", "gps")
                putInt("ephemerisMask", 0x1FFF)
                putInt("almanacMask", 0x1FFF)
                putInt("usedInFixMask", 0x1FFF)
            }

            val gpsLoc = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = realisticAltitude
                time = nowMs
                elapsedRealtimeNanos = elapsedNanos
                accuracy = realisticGpsAccuracy
                speed = 0.0f
                bearing = 0.0f
                extras = gpsExtras
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 1.6f
                    bearingAccuracyDegrees = 1.0f
                    speedAccuracyMetersPerSecond = 0.1f
                }
            }

            try {
                locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, gpsLoc)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Failed to set GPS test provider location", e)
            }

            // 2. Authentic Network Location (coarse accuracy, wifi/cell extras)
            val netExtras = Bundle().apply {
                putString("networkLocationType", "wifi")
            }

            val netLoc = Location(LocationManager.NETWORK_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                time = nowMs
                elapsedRealtimeNanos = elapsedNanos
                accuracy = 16.0f + (Random.nextInt(0, 40) * 0.1f) // 16.0m - 20.0m coarse
                extras = netExtras
            }

            try {
                locationManager.setTestProviderLocation(LocationManager.NETWORK_PROVIDER, netLoc)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Failed to set Network test provider location", e)
            }

            // 3. Google Play Services Fused Location Client
            val fusedLoc = Location("fused").apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = realisticAltitude
                time = nowMs
                elapsedRealtimeNanos = elapsedNanos
                accuracy = realisticGpsAccuracy
                speed = 0.0f
                bearing = 0.0f
                extras = gpsExtras
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    verticalAccuracyMeters = 1.6f
                    bearingAccuracyDegrees = 1.0f
                    speedAccuracyMetersPerSecond = 0.1f
                }
            }

            try {
                fusedClient.setMockLocation(fusedLoc)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Failed to set fused mock location", e)
            }

            Result.success(Unit)
        } catch (se: SecurityException) {
            Result.failure(se)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopMocking() {
        isMockingActive = false

        try {
            fusedClient.setMockMode(false)
        } catch (e: Exception) {
            Log.w("MockLocationManager", "Failed to disable fused mock mode", e)
        }

        // CRITICAL FIX: Do NOT call setTestProviderEnabled(false) before removeTestProvider.
        // Calling setTestProviderEnabled(false) can leave the physical hardware GPS provider
        // flagged as disabled in system settings. Calling removeTestProvider directly cleanly
        // detaches the mock layer and re-attaches the physical hardware GPS driver.
        for (provider in testProviders) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.w("MockLocationManager", "Failed to clean up test provider $provider", e)
            }
        }

        // Kickstart hardware GPS chip so real satellites and apps like GPS Locker can bind immediately
        kickstartHardwareGps()
    }

    /**
     * Re-awakens the physical hardware GPS driver on the device.
     * When a test provider is removed, the Android LocationManagerService and GNSS HAL
     * may remain in an idle or cold state. A brief hardware update request wakes up
     * the physical GPS receiver so real satellite ephemeris data is acquired without delay.
     */
    private fun kickstartHardwareGps() {
        try {
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: Location) {}
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                android.os.Looper.getMainLooper()
            )

            // Remove listener after 2 seconds to release battery once HAL is re-engaged
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    locationManager.removeUpdates(listener)
                } catch (_: Exception) {}
            }, 2000L)
        } catch (e: Exception) {
            Log.w("MockLocationManager", "kickstartHardwareGps notice: ${e.message}")
        }
    }
}
