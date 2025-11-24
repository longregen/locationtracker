package com.locationtracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver that automatically starts the LocationService when the device boots.
 * This ensures location tracking continues after a device reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed, starting LocationService")

            // Start the LocationService
            val serviceIntent = Intent(context, LocationService::class.java)
            try {
                context.startForegroundService(serviceIntent)
                Log.d("BootReceiver", "LocationService started successfully")
            } catch (e: Exception) {
                Log.e("BootReceiver", "Failed to start LocationService", e)
            }

            // Schedule periodic checks to ensure service stays running
            LocationServiceScheduler.scheduleServiceCheck(context)
            Log.d("BootReceiver", "Scheduled periodic service health checks")
        }
    }
}
