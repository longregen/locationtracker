package com.locationtracker

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * WorkManager Worker that periodically checks if LocationService is running
 * and restarts it if needed. This provides a safety net to ensure continuous
 * location tracking even if the service is stopped unexpectedly.
 */
class LocationServiceWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("LocationServiceWorker", "Checking LocationService status")

        if (!isServiceRunning(applicationContext, LocationService::class.java)) {
            Log.w("LocationServiceWorker", "LocationService not running, attempting to restart")

            val serviceIntent = Intent(applicationContext, LocationService::class.java)
            try {
                applicationContext.startForegroundService(serviceIntent)
                Log.d("LocationServiceWorker", "LocationService restarted successfully")
            } catch (e: Exception) {
                Log.e("LocationServiceWorker", "Failed to restart LocationService", e)
                return Result.retry()
            }
        } else {
            Log.d("LocationServiceWorker", "LocationService is running")
        }

        return Result.success()
    }

    /**
     * Check if a service is currently running
     */
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
