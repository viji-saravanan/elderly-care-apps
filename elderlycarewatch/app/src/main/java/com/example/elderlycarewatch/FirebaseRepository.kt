package com.example.elderlycarewatch

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FirebaseRepository {
    private val database = FirebaseDatabase.getInstance()
    private val healthRef = database.getReference("health_data")

    private val _latestHeartRate = MutableStateFlow<Double?>(null)
    val latestHeartRate: StateFlow<Double?> = _latestHeartRate.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _latestSteps = MutableStateFlow<Long?>(null)
    val latestSteps: StateFlow<Long?> = _latestSteps.asStateFlow()

    private val _latestCalories = MutableStateFlow<Long?>(null)
    val latestCalories: StateFlow<Long?> = _latestCalories.asStateFlow()

    private val _latestSpO2 = MutableStateFlow<Double?>(null)
    val latestSpO2: StateFlow<Double?> = _latestSpO2.asStateFlow()

    fun updateHealthData(
        heartRate: Double? = null,
        steps: Long? = null,
        calories: Long? = null,
        spo2: Double? = null,
        bodyFat: Double? = null,
        weight: Double? = null,
        leanBodyMass: Double? = null,
        sleepData: Map<String, Long>? = null, // duration, deep, light, rem, awake
        hrHistory: List<Map<String, Any>>? = null,
        trends: List<Map<String, Any>>? = null,
        isWearing: Boolean? = null,
        batteryLevel: Int? = null,
        isCharging: Boolean? = null
    ) {
        _isSyncing.value = true
        _errorMessage.value = null // Clear previous errors
        val timestamp = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
        
        val updateMap = mutableMapOf<String, Any>(
            "last_updated" to formattedTime,
            "timestamp" to timestamp
        )

        heartRate?.let { 
            if (it > 0) {
                updateMap["heart_rate"] = it
                _latestHeartRate.value = it
            }
        }
        steps?.let { 
            updateMap["steps"] = it 
            _latestSteps.value = it
        }
        calories?.let { 
            updateMap["calories"] = it 
            _latestCalories.value = it
        }
        spo2?.let { 
            if (it > 0) {
                updateMap["spo2"] = it 
                _latestSpO2.value = it
            }
        }
        bodyFat?.let { updateMap["body_fat"] = it }
        weight?.let { updateMap["weight"] = it }
        leanBodyMass?.let { updateMap["lean_body_mass"] = it }
        
        sleepData?.let { 
            updateMap["sleep"] = it 
        }
        
        hrHistory?.let {
            updateMap["history/heart_rate"] = it
        }
        
        trends?.let {
            updateMap["trends"] = it
        }
        
        isWearing?.let { updateMap["is_wearing"] = it }
        
        batteryLevel?.let { updateMap["battery_level"] = it }
        isCharging?.let { updateMap["is_charging"] = it }

        healthRef.updateChildren(updateMap)
            .addOnCompleteListener {
                _isSyncing.value = false
            }
            .addOnFailureListener { e ->
                _isSyncing.value = false
                _errorMessage.value = e.message ?: "Unknown Error"
            }
    }
}
