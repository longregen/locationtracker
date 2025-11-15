package com.locationtracker

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Manages scheduling of periodic LocationService health checks using WorkManager.
 * Ensures the service is restarted if it stops unexpectedly.
 */
object LocationServiceScheduler {
    private const val WORK_NAME = "LocationServiceHealthCheck"
    private const val REPEAT_INTERVAL_MINUTES = 15L

    /**
     * Schedule periodic checks to ensure LocationService stays running.
     * Uses WorkManager to perform checks every 15 minutes.
     */
    fun scheduleServiceCheck(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<LocationServiceWorker>(
            REPEAT_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        Log.d("LocationServiceScheduler", "Scheduled periodic service checks every $REPEAT_INTERVAL_MINUTES minutes")
    }

    /**
     * Cancel all scheduled service checks
     */
    fun cancelServiceCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d("LocationServiceScheduler", "Cancelled periodic service checks")
    }
}
