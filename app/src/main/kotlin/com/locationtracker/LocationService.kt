package com.locationtracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {

    companion object {
        private const val CHANNEL_ID = "LocationTrackerChannel"
        private const val NOTIFICATION_ID = 1
        const val EXTRA_TEST_MODE = "test_mode"
    }

    private var locationManager: LocationManager? = null
    private lateinit var databaseHelper: DatabaseHelper
    private var testMode = false

    override fun onCreate() {
        super.onCreate()
        databaseHelper = DatabaseHelper(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        testMode = intent?.getBooleanExtra(EXTRA_TEST_MODE, false) ?: false
        if (testMode) {
            android.util.Log.d("LocationService", "Running in TEST MODE - will use GPS_PROVIDER")
        }
        startLocationUpdates()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.location_service_running))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            // In test mode, use GPS_PROVIDER to receive emulator geo fix commands
            // In production, use PASSIVE_PROVIDER for battery efficiency
            val provider = if (testMode) {
                android.util.Log.d("LocationService", "Using GPS_PROVIDER for testing")
                LocationManager.GPS_PROVIDER
            } else {
                LocationManager.PASSIVE_PROVIDER
            }

            locationManager?.requestLocationUpdates(
                provider,
                0L,      // Minimum time interval (0 = as fast as possible in test mode)
                0f,      // Minimum distance (0 = any distance)
                this
            )

            // In test mode, also try NETWORK_PROVIDER as fallback
            if (testMode) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        0L,
                        0f,
                        this
                    )
                    android.util.Log.d("LocationService", "Also listening to NETWORK_PROVIDER")
                } catch (e: Exception) {
                    android.util.Log.w("LocationService", "Could not register NETWORK_PROVIDER: ${e.message}")
                }
            }

            android.util.Log.d("LocationService", "Location updates started with provider: $provider")
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Failed to start location updates: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        android.util.Log.d("LocationService", "Location changed: (${location.latitude}, ${location.longitude}) from provider: ${location.provider}")
        databaseHelper.insertOrUpdateLocation(
            location.latitude,
            location.longitude,
            System.currentTimeMillis()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
