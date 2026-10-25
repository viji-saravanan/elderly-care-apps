package com.example.elderlycarewatch

import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import com.example.elderlycarewatch.FirebaseRepository

class HealthService : PassiveListenerService() {

    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val heartRatePoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        val stepPoints = dataPoints.getData(DataType.STEPS_DAILY)
        val caloriePoints = dataPoints.getData(DataType.CALORIES_DAILY)
        // Note: SPO2 might not be available via PassiveListener in all versions/devices usually requires Active Measurement or Health Connect.
        // We will focus on HR, Steps, Calories for passive background.

        var latestHr: Double? = null
        var latestSteps: Long? = null
        var latestCalories: Long? = null


        if (heartRatePoints.isNotEmpty()) {
            latestHr = heartRatePoints.last().value
            Log.d("HealthService", "Heart Rate: $latestHr")
            
            // Feed into History Manager for aggregation
            HistoryManager.getInstance(this).addHeartRate(latestHr)
        }

        if (stepPoints.isNotEmpty()) {
            latestSteps = stepPoints.last().value
            Log.d("HealthService", "Steps: $latestSteps")
            
            // Feed steps into History Manager for sleep detection
            HistoryManager.getInstance(this).addSteps(latestSteps)
            // Feed for Daily Trends
            HistoryManager.getInstance(this).updateDailyStats(latestSteps)
        }

        if (caloriePoints.isNotEmpty()) {
            latestCalories = caloriePoints.last().value.toLong()
            Log.d("HealthService", "Calories: $latestCalories")
        }
        
        // Battery & Wearing
        val batteryManager = getSystemService(android.content.Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isWearing = true // If we are receiving data, we are likely wearing it
        
        if (latestHr != null || latestSteps != null || latestCalories != null) {
            FirebaseRepository.updateHealthData(
                heartRate = latestHr,
                steps = latestSteps,
                calories = latestCalories,
                batteryLevel = batteryLevel,
                isWearing = isWearing,
                isCharging = false // Simplified
            )
        }
    }
}
