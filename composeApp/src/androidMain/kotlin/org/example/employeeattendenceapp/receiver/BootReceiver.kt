package org.example.employeeattendenceapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.example.employeeattendenceapp.LocationTrackingService
import org.example.employeeattendenceapp.service.AppBackgroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart background services on boot
            AppBackgroundService.startService(context)
            LocationTrackingService.startService(context)
        }
    }
}
