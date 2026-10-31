package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Bridge and synchronization manager between My Maps (Fake GPS) and GPS Locker by SilentLexx UA
 * (package: com.silentlexx.gpslock / com.silentlexx.gpslockprime / com.silentlexx.gpslocker).
 *
 * Requirements:
 * 1. When Mock Location is ACTIVE:
 *    - Automatically stop/pause GPS Locker so it does not conflict with mock coordinates
 *      or lock the physical GPS receiver while spoofing.
 *
 * 2. When Mock Location is INACTIVE for more than 10 seconds:
 *    - Allow and command GPS Locker to resume its normal function (locking real satellite GPS).
 *    - Provide a 10-second stabilization cooldown window so that the Android physical GNSS HAL
 *      can successfully re-bind, download ephemeris, and acquire real satellites before GPS Locker locks.
 */
object GpsLockerBridge {

    private const val TAG = "GpsLockerBridge"

    private val scope = CoroutineScope(Dispatchers.Default)
    private var resumeJob: Job? = null

    // Target packages of GPS Locker by SilentLexx UA
    val TARGET_PACKAGES = listOf(
        "com.silentlexx.gpslock",
        "com.silentlexx.gpslockprime",
        "com.silentlexx.gpslocker"
    )

    // Stop actions for external triggers
    private val STOP_ACTIONS = listOf(
        "com.silentlexx.gpslock.STOP",
        "com.silentlexx.gpslock.ACTION_STOP",
        "com.silentlexx.gpslock.UNLOCK",
        "com.silentlexx.gpslock.LOCK_OFF",
        "com.silentlexx.gpslock.ACTION_LOCK_OFF",
        "com.silentlexx.gpslock.PAUSE",
        "com.silentlexx.gpslocker.STOP",
        "com.silentlexx.gpslocker.ACTION_STOP",
        "com.silentlexx.gpslocker.UNLOCK",
        "com.silentlexx.gpslocker.LOCK_OFF"
    )

    // Start / Lock actions for external triggers
    private val START_ACTIONS = listOf(
        "com.silentlexx.gpslock.START",
        "com.silentlexx.gpslock.ACTION_START",
        "com.silentlexx.gpslock.LOCK",
        "com.silentlexx.gpslock.LOCK_ON",
        "com.silentlexx.gpslock.ACTION_LOCK_ON",
        "com.silentlexx.gpslock.RESUME",
        "com.silentlexx.gpslocker.START",
        "com.silentlexx.gpslocker.ACTION_START",
        "com.silentlexx.gpslocker.LOCK",
        "com.silentlexx.gpslocker.LOCK_ON"
    )

    private val _isWaitingCooldown = MutableStateFlow(false)
    val isWaitingCooldown = _isWaitingCooldown.asStateFlow()

    private val _cooldownSecondsRemaining = MutableStateFlow(0)
    val cooldownSecondsRemaining = _cooldownSecondsRemaining.asStateFlow()

    private val _lastStatusMessage = MutableStateFlow<String?>(null)
    val lastStatusMessage = _lastStatusMessage.asStateFlow()

    fun isGpsLockerInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in TARGET_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return true
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return false
    }

    fun getInstalledPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in TARGET_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    /**
     * Called immediately when Mock Location is STARTED.
     * Cancels any pending resume job and forces GPS Locker to stop locking.
     */
    fun onMockStarted(context: Context) {
        // 1. Cancel any pending countdown timer
        resumeJob?.cancel()
        resumeJob = null
        _isWaitingCooldown.value = false
        _cooldownSecondsRemaining.value = 0

        Log.d(TAG, "Mock location active: commanding GPS Locker to stop/pause lock")
        _lastStatusMessage.value = "Mock aktif: GPS Locker dihentikan sementara agar tidak bentrok."

        // 2. Broadcast stop intents to all potential packages and actions
        for (pkg in TARGET_PACKAGES) {
            for (action in STOP_ACTIONS) {
                try {
                    val intent = Intent(action).apply {
                        setPackage(pkg)
                        flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                    }
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending broadcast $action to $pkg: ${e.message}")
                }
            }

            // Also try stopping known service components directly if running
            val serviceNames = listOf(
                "$pkg.LockerService",
                "$pkg.service.LockerService",
                "$pkg.GpsLockService",
                "$pkg.service.GpsLockService"
            )
            for (serviceName in serviceNames) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName(pkg, serviceName)
                    }
                    context.stopService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Called when Mock Location is STOPPED.
     * Enforces a 10-second hardware stabilization cooldown.
     * Once non-active for > 10 seconds, triggers GPS Locker to resume its lock function.
     */
    fun onMockStopped(context: Context) {
        resumeJob?.cancel()

        resumeJob = scope.launch {
            _isWaitingCooldown.value = true
            _lastStatusMessage.value = "Mock non-aktif: Memulihkan GPS fisik (stabilisasi 10 detik)..."

            // Countdown from 10 to 1 seconds
            for (sec in 10 downTo 1) {
                _cooldownSecondsRemaining.value = sec
                delay(1000)
            }

            _isWaitingCooldown.value = false
            _cooldownSecondsRemaining.value = 0
            _lastStatusMessage.value = "GPS fisik stabil: Mengizinkan GPS Locker mengunci kembali sinyal GPS."

            Log.d(TAG, "10-second cooldown elapsed: Commanding GPS Locker to resume locking real GPS")
            commandGpsLockerResume(context)
        }
    }

    /**
     * Sends resume / lock commands to GPS Locker after the 10-second cooldown has elapsed.
     */
    private fun commandGpsLockerResume(context: Context) {
        for (pkg in TARGET_PACKAGES) {
            for (action in START_ACTIONS) {
                try {
                    val intent = Intent(action).apply {
                        setPackage(pkg)
                        flags = Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                    }
                    context.sendBroadcast(intent)
                } catch (e: Exception) {
                    Log.w(TAG, "Error sending start broadcast $action to $pkg: ${e.message}")
                }
            }

            // Attempt to launch or start the service for GPS Locker if installed
            val serviceNames = listOf(
                "$pkg.LockerService",
                "$pkg.service.LockerService",
                "$pkg.GpsLockService",
                "$pkg.service.GpsLockService"
            )
            for (serviceName in serviceNames) {
                try {
                    val intent = Intent().apply {
                        component = ComponentName(pkg, serviceName)
                    }
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun openGpsLockerApp(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in TARGET_PACKAGES) {
            try {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }
}
