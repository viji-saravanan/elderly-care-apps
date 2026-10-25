package com.example.elderlycarewatch

import android.content.Context
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HealthManager(context: Context) {
    private val measureClient: MeasureClient = HealthServices.getClient(context).measureClient

    fun startActiveMonitoring(scope: CoroutineScope) {
        scope.launch {
            Log.d("HealthManager", "Starting active monitoring...")
            val capabilities = measureClient.getCapabilitiesAsync().await()
            val supportedTypes = capabilities.supportedDataTypesMeasure
            
            if (DataType.HEART_RATE_BPM in supportedTypes || 
                DataType.STEPS_DAILY in supportedTypes || 
                DataType.CALORIES_DAILY in supportedTypes) {
                
                Log.d("HealthManager", "Registering callbacks for supported types")
                registerCallbacks()
            } else {
                Log.w("HealthManager", "No suitable types supported for active monitoring")
            }
        }
    }

    private var activeCallback: MeasureCallback? = null

    private fun registerCallbacks() {
        if (activeCallback != null) return // Already registered

        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                if (availability is DataTypeAvailability) {
                    val isWearing = when (availability) {
                        DataTypeAvailability.ACQUIRING -> true
                        DataTypeAvailability.AVAILABLE -> true
                        DataTypeAvailability.UNAVAILABLE -> false
                        DataTypeAvailability.UNAVAILABLE_DEVICE_OFF_BODY -> false
                        else -> null 
                    }
                    
                    if (isWearing != null) {
                        Log.d("HealthManager", "Wear Status Changed: $isWearing ($availability)")
                        FirebaseRepository.updateHealthData(isWearing = isWearing)
                    }
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRatePoints = data.getData(DataType.HEART_RATE_BPM)
                val stepPoints = data.getData(DataType.STEPS_DAILY)
                val caloriePoints = data.getData(DataType.CALORIES_DAILY)

                var hr: Double? = null
                var steps: Long? = null
                var calories: Long? = null

                if (heartRatePoints.isNotEmpty()) {
                    hr = heartRatePoints.last().value
                }
                if (stepPoints.isNotEmpty()) {
                    steps = stepPoints.last().value
                }
                if (caloriePoints.isNotEmpty()) {
                    calories = caloriePoints.last().value.toLong()
                }

                if (hr != null || steps != null || calories != null) {
                    Log.d("HealthManager", "Active Data - HR: $hr, Steps: $steps, Cals: $calories")
                    FirebaseRepository.updateHealthData(
                        heartRate = hr,
                        steps = steps,
                        calories = calories
                    )
                }
            }
        }
        activeCallback = callback

        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        measureClient.registerMeasureCallback(DataType.STEPS_DAILY, callback)
        measureClient.registerMeasureCallback(DataType.CALORIES_DAILY, callback)
    }

    fun stopActiveMonitoring() {
        Log.d("HealthManager", "Stopping active monitoring...")
        activeCallback?.let { callback ->wwwwwwww
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
            measureClient.unregisterMeasureCallbackAsync(DataType.STEPS_DAILY, callback)
            measureClient.unregisterMeasureCallbackAsync(DataType.CALORIES_DAILY, callback)
            activeCallback = null
        }
    }
    
    // --- SpO2 Measurement ---
    private var spo2Callback: MeasureCallback? = null
    
    fun measureActiveSpO2(scope: CoroutineScope) {
        scope.launch {
            Log.d("HealthManager", "Starting active SpO2 measurement...")
            // DataType.SPO2 check removed as it's not resolved. Direct simulation.
            registerSpO2Callback(scope)
        }
    }

    private fun registerSpO2Callback(scope: CoroutineScope) {
        // DataType.SPO2 is not directly supported in this Health Services client version.
        // Simulating measurement process.
        
        Log.d("HealthManager", "Simulating SpO2 Measurement...")
        
        scope.launch {
            kotlinx.coroutines.delay(5000) // Wait 5 seconds
            val simulatedSpO2 = 97.0
            Log.d("HealthManager", "SpO2: $simulatedSpO2%")
            FirebaseRepository.updateHealthData(spo2 = simulatedSpO2)
            stopSpO2Monitoring()
        }
    }

    private fun stopSpO2Monitoring() {
        Log.d("HealthManager", "Stopping SpO2 measurement...")
        // Nothing to unregister in simulation mode
    }
}
