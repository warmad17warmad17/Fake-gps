package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.FakeGpsApp
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockLocationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var mockJob: Job? = null
    private lateinit var mockManager: MockLocationManager

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_UPDATE_COORDS = "com.example.service.ACTION_UPDATE_COORDS"

        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"

        private val _isMockingRunning = MutableStateFlow(false)
        val isMockingRunning = _isMockingRunning.asStateFlow()

        private val _currentMockCoords = MutableStateFlow<Pair<Double, Double>?>(null)
        val currentMockCoords = _currentMockCoords.asStateFlow()

        private val _mockError = MutableStateFlow<String?>(null)
        val mockError = _mockError.asStateFlow()

        fun clearError() {
            _mockError.value = null
        }

        fun setMockError(message: String) {
            _mockError.value = message
        }

        fun start(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateLocation(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_UPDATE_COORDS
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mockManager = MockLocationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)

                // CRITICAL: Call startForeground IMMEDIATELY to satisfy Android requirement
                val notification = buildNotification(lat, lng)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        FakeGpsApp.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(FakeGpsApp.NOTIFICATION_ID, notification)
                }

                startMockLoop(lat, lng)
            }
            ACTION_UPDATE_COORDS -> {
                val lat = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                _currentMockCoords.value = Pair(lat, lng)
                updateNotification(lat, lng)
            }
            ACTION_STOP -> {
                stopMockLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMockLoop(latitude: Double, longitude: Double) {
        val startResult = mockManager.startMocking()
        if (startResult.isFailure) {
            val ex = startResult.exceptionOrNull()
            if (ex is SecurityException) {
                _mockError.value = "Aplikasi belum diatur sebagai 'Aplikasi Lokasi Tiruan' di Opsi Pengembang (Developer Options)."
            } else {
                _mockError.value = "Gagal mengaktifkan mock location: ${ex?.localizedMessage ?: "Unknown error"}"
            }
            _isMockingRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        _isMockingRunning.value = true
        _currentMockCoords.value = Pair(latitude, longitude)

        mockJob?.cancel()
        mockJob = serviceScope.launch {
            while (isActive && _isMockingRunning.value) {
                val coords = _currentMockCoords.value ?: Pair(latitude, longitude)
                val injectResult = mockManager.injectLocation(coords.first, coords.second)
                if (injectResult.isFailure) {
                    val ex = injectResult.exceptionOrNull()
                    if (ex is SecurityException) {
                        _mockError.value = "Izin mock location dicabut. Aktifkan di Opsi Pengembang."
                        _isMockingRunning.value = false
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                }
                delay(1000) // Continuous pulse every 1s
            }
        }
    }

    private fun stopMockLoop() {
        mockJob?.cancel()
        mockJob = null
        mockManager.stopMocking()
        _isMockingRunning.value = false
        _currentMockCoords.value = null
    }

    private fun buildNotification(latitude: Double, longitude: Double): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MockLocationService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FakeGpsApp.CHANNEL_ID)
            .setContentTitle("Fake GPS Aktif")
            .setContentText("Lokasi: %.6f, %.6f".format(latitude, longitude))
            .setSmallIcon(R.drawable.fake_gps_icon_1792932593937)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Berhenti", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(latitude: Double, longitude: Double) {
        val notification = buildNotification(latitude, longitude)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(FakeGpsApp.NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        stopMockLoop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
