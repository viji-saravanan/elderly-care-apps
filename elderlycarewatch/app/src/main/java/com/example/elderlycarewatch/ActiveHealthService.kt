package com.example.elderlycarewatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ActiveHealthService : LifecycleService() {

    private val binder = LocalBinder()
    private lateinit var healthManager: HealthManager
    private var isMonitoring = false

    inner class LocalBinder : Binder() {
        fun getService(): ActiveHealthService = this@ActiveHealthService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ActiveHealthService", "Service Created")
        healthManager = HealthManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        Log.d("ActiveHealthService", "Service Started")
        startForeground(NOTIFICATION_ID, buildNotification())
        
        if (!isMonitoring) {
            healthManager.startActiveMonitoring(lifecycleScope)
            startPeriodicSpO2() // Start the loop
            isMonitoring = true
        }

        return START_STICKY
    }

    private fun startPeriodicSpO2() {
        lifecycleScope.launch {
            while (true) {
                Log.d("ActiveHealthService", "Triggering periodic SpO2 & Battery check...")
                healthManager.measureActiveSpO2(lifecycleScope)
                pollBatteryStatus()
                // Wait 60 seconds
                delay(60 * 1000L)
            }
        }
    }

    private fun pollBatteryStatus() {
        val batteryStatus: Intent? = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level: Int = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
            (level * 100 / scale.toFloat()).toInt()
        } ?: -1
        
        val status: Int = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == android.os.BatteryManager.BATTERY_STATUS_FULL

        Log.d("ActiveHealthService", "Battery: $level%, Charging: $isCharging")
        FirebaseRepository.updateHealthData(batteryLevel = level, isCharging = isCharging)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        // Ensure monitoring is active when bound
        if (!isMonitoring) {
            healthManager.startActiveMonitoring(lifecycleScope)
            isMonitoring = true
        }
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ActiveHealthService", "Service Destroyed")
        healthManager.stopActiveMonitoring()
        isMonitoring = false
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Elderly Care Monitor")
            .setContentText("Monitoring health sensors active...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Low priority to minimize distraction but keep alive
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Health Monitoring Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "health_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
