package com.example.elderlycarephone

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HealthData(
    val heartRate: Double = 0.0,
    val steps: Long = 0,
    val calories: Long = 0,
    val spo2: Double = 0.0,
    val bodyFat: Double = 0.0,
    val weight: Double = 0.0,
    val leanBodyMass: Double = 0.0,
    val sleep: Map<String, Long> = emptyMap(),
    val hrHistory: List<Map<String, Any>> = emptyList(),
    val isWearing: Boolean = false,
    val batteryLevel: Int = -1,
    val isCharging: Boolean = false,
    val lastUpdated: String = "--",
    val trends: List<Map<String, Any>> = emptyList(),
    val sleepStages: Map<String, Long> = emptyMap(),
    val bodyCompHistory: List<Map<String, Any>> = emptyList(),
    val stepsHistory: List<Long> = List(24) { 0L },
    val weeklyActivity: List<Map<String, Any>> = emptyList(),
    val activeMinutes: Long = 0
)

class FirebaseViewModel : ViewModel() {

    private val _healthData = MutableStateFlow(HealthData())
    val healthData: StateFlow<HealthData> = _healthData.asStateFlow()
    
    private val _selectedDateMillis = MutableStateFlow<Long>(java.time.LocalDate.now().toEpochDay() * 86400000)
    val selectedDateMillis: StateFlow<Long> = _selectedDateMillis.asStateFlow()

    private val _isCaretakerMode = MutableStateFlow(false)
    val isCaretakerMode: StateFlow<Boolean> = _isCaretakerMode.asStateFlow()
    
    fun toggleCaretakerMode(enabled: Boolean) {
        _isCaretakerMode.value = enabled
    }

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    
    fun clearSyncMessage() { _syncMessage.value = null }
    
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted: StateFlow<Boolean> = _permissionsGranted.asStateFlow()
    
    fun checkPermissions() {
        if (::healthConnectManager.isInitialized) {
            viewModelScope.launch {
                _permissionsGranted.value = healthConnectManager.hasPermissions()
            }
        }
    }

    init {
        startListening()
    }

    private fun startListening() {
        val database = FirebaseDatabase.getInstance()
        val ref = database.getReference("health_data")

        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val heartRate = snapshot.child("heart_rate").getValue(Double::class.java) ?: 0.0
                val steps = snapshot.child("steps").getValue(Long::class.java) ?: 0
                val calories = snapshot.child("calories").getValue(Long::class.java) ?: 0
                val spo2 = snapshot.child("spo2").getValue(Double::class.java) ?: 0.0
                val bodyFat = snapshot.child("body_fat").getValue(Double::class.java) ?: 0.0
                val weight = snapshot.child("weight").getValue(Double::class.java) ?: 0.0
                val leanBodyMass = snapshot.child("lean_body_mass").getValue(Double::class.java) ?: 0.0
                val activeMinutes = snapshot.child("active_minutes").getValue(Long::class.java) ?: 0L
                
                // Parse Sleep
                val sleepSnapshot = snapshot.child("sleep")
                val sleepMap = mutableMapOf<String, Long>()
                sleepSnapshot.children.forEach { child ->
                    child.key?.let { key ->
                        if (key == "stages") return@let // Skip nested stages map
                        try {
                             val value = child.getValue(Long::class.java) ?: 0L
                             sleepMap[key] = value
                        } catch (e: Exception) {
                            Log.w("FirebaseViewModel", "Skipping non-long sleep val: $key")
                        }
                    }
                }
                
                // Parse Sleep Stages (from Firebase for remote view)
                val stagesSnapshot = snapshot.child("sleep").child("stages")
                val stagesMap = mutableMapOf<String, Long>()
                 if (stagesSnapshot.exists()) {
                    stagesSnapshot.children.forEach { child ->
                        child.key?.let { key ->
                             val value = child.getValue(Long::class.java) ?: 0L
                             stagesMap[key] = value
                        }
                    }
                }


                // Parse History
                val historySnapshot = snapshot.child("history").child("heart_rate")
                val hrHistoryList = mutableListOf<Map<String, Any>>()
                if (historySnapshot.exists()) {
                     historySnapshot.children.forEach { child ->
                         val time = child.child("time").getValue(Long::class.java)
                         val value = child.child("value").getValue(Double::class.java)
                         if (time != null && value != null) {
                             hrHistoryList.add(mapOf("time" to time, "value" to value))
                         }
                     }
                }
                
                // Parse Trends
                val trendsSnapshot = snapshot.child("trends")
                val trendsList = mutableListOf<Map<String, Any>>()
                if (trendsSnapshot.exists()) {
                    trendsSnapshot.children.forEach { child ->
                         val date = child.child("date").getValue(Long::class.java) ?: 0L
                         val s = child.child("steps").getValue(Long::class.java) ?: 0L
                         val sl = child.child("sleep_seconds").getValue(Long::class.java) ?: 0L
                         trendsList.add(mapOf("date" to date, "steps" to s, "sleep_seconds" to sl))
                    }
                }
                
                // Parse Body Comp History
                val bcHistorySnapshot = snapshot.child("history").child("body_comp")
                val bcList = mutableListOf<Map<String, Any>>()
                if (bcHistorySnapshot.exists()) {
                     bcHistorySnapshot.children.forEach { child ->
                         val date = child.child("date").getValue(Long::class.java)
                         val w = child.child("weight").getValue(Double::class.java)
                         val bf = child.child("body_fat").getValue(Double::class.java)
                         if (date != null) {
                             val map = mutableMapOf<String, Any>("date" to date)
                             w?.let { map["weight"] = it }
                             bf?.let { map["body_fat"] = it }
                             bcList.add(map)
                         }
                     }
                }

                val isWearing = snapshot.child("is_wearing").getValue(Boolean::class.java) ?: false
                val batteryLevel = snapshot.child("battery_level").getValue(Int::class.java) ?: -1
                val isCharging = snapshot.child("is_charging").getValue(Boolean::class.java) ?: false
                val lastUpdated = snapshot.child("last_updated").getValue(String::class.java) ?: "--"
                
                // Parse Steps History (Hourly)
                val stepsHistSnapshot = snapshot.child("steps_history")
                val stepsHistList = MutableList(24) { 0L }
                if (stepsHistSnapshot.exists()) {
                    stepsHistSnapshot.children.forEach { child ->
                         try {
                            val idx = child.key?.toIntOrNull()
                            val valLong = child.getValue(Long::class.java) ?: 0L
                            if (idx != null && idx in 0..23) {
                                 stepsHistList[idx] = valLong
                            }
                         } catch (e: Exception) { }
                    }
                }

                _healthData.value = HealthData(
                    heartRate = heartRate,
                    steps = steps,
                    calories = calories,
                    spo2 = spo2,
                    bodyFat = bodyFat,
                    weight = weight,
                    leanBodyMass = leanBodyMass,
                    sleep = sleepMap,
                    sleepStages = stagesMap,
                    hrHistory = hrHistoryList,
                    stepsHistory = stepsHistList,
                    isWearing = isWearing,
                    batteryLevel = batteryLevel,
                    isCharging = isCharging,
                    lastUpdated = lastUpdated,
                    trends = trendsList,
                    activeMinutes = activeMinutes
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseViewModel", "Database error: ${error.message}")
            }
        })
    }
    private lateinit var healthConnectManager: HealthConnectManager

    fun initializeHealthConnect(context: android.content.Context) {
        healthConnectManager = HealthConnectManager(context)
        checkPermissions()
    }

    // --- History Navigation ---
    private var currentDateOffset = 0L // 0 = Today, -1 = Yesterday
    
    fun changeDateOffset(offset: Long) {
        currentDateOffset += offset
        if (currentDateOffset > 0) currentDateOffset = 0 // Cannot go to future
        
        updateSelectedDateMillis()
        loadDataForCurrentDate()
    }
    
    fun jumpToDate(dateMillis: Long) {
        // Calculate offset from dateMillis
        val targetDate = java.time.Instant.ofEpochMilli(dateMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val today = java.time.LocalDate.now()
        val requestedOffset = java.time.temporal.ChronoUnit.DAYS.between(today, targetDate)
        
        if (requestedOffset <= 0) {
             currentDateOffset = requestedOffset
             updateSelectedDateMillis()
             loadDataForCurrentDate()
        }
    }
    
    private fun updateSelectedDateMillis() {
        val d = java.time.LocalDate.now().plusDays(currentDateOffset)
        _selectedDateMillis.value = d.toEpochDay() * 86400000
    }
    
    fun getDateTitle(): String {
        return when (currentDateOffset) {
            0L -> "Today"
            -1L -> "Yesterday"
            else -> "${java.time.LocalDate.now().plusDays(currentDateOffset)}"
        }
    }

    fun loadDataForCurrentDate() {
        if (!::healthConnectManager.isInitialized) return
        
        viewModelScope.launch {
            if (healthConnectManager.hasPermissions()) {
                val now = java.time.Instant.now()
                // If Today (0), use simpler "last 24h" or "Start of day to now"
                // Actually user wants "Day by Day", so 00:00 to 23:59 (or now)
                
                val zone = java.time.ZoneId.systemDefault()
                val anchorDate = java.time.LocalDate.now().plusDays(currentDateOffset)
                
                val startOfDay = anchorDate.atStartOfDay(zone).toInstant()
                val endOfDay = anchorDate.plusDays(1).atStartOfDay(zone).toInstant()
                
                // If showing today, clamp end to now
                val effectiveEnd = if(currentDateOffset == 0L) now else endOfDay
                
                val hcData = healthConnectManager.readDataForDate(startOfDay, effectiveEnd)
                val weeklyTrends = healthConnectManager.readWeeklyTrends(anchorDate.atStartOfDay(zone).toInstant())

                Log.d("FirebaseViewModel", "Loaded History ($currentDateOffset): $hcData")
                
                // Update UI State with History Data
                // If it's TODAY, we merge with Live Data (Live takes precedence for real-time like HR)
                // If it's HISTORY, we replace entirely
                
                val newHealthData = if (currentDateOffset == 0L) {
                     // Keep existing live values, but update "Totals" from HC if available
                     _healthData.value.copy(
                         // Maybe update steps/sleep from HC as it's more accurate?
                         steps = (hcData["steps"] as? Long) ?: _healthData.value.steps,
                         // Don't overwrite live HR with average
                         hrHistory = (hcData["hr_history"] as? List<Map<String, Any>>) ?: _healthData.value.hrHistory,
                         sleepStages = (hcData["sleep_stages"] as? Map<String, Long>) ?: emptyMap(),
                         stepsHistory = (hcData["steps_history"] as? List<Long>) ?: _healthData.value.stepsHistory,
                         weeklyActivity = weeklyTrends,
                         calories = (hcData["calories"] as? Long) ?: _healthData.value.calories,
                         activeMinutes = (hcData["active_time_minutes"] as? Long) ?: _healthData.value.activeMinutes
                     )
                } else {
                     // Pure History View
                     HealthData(
                         heartRate = 0.0, // No single "live" HR
                         steps = (hcData["steps"] as? Long) ?: 0L,
                         calories = (hcData["calories"] as? Long) ?: 0L,
                         activeMinutes = (hcData["active_time_minutes"] as? Long) ?: 0L,
                         spo2 = (hcData["spo2"] as? Double) ?: 0.0,
                         bodyFat = (hcData["body_fat"] as? Double) ?: 0.0,
                         weight = (hcData["weight"] as? Double) ?: 0.0,
                         leanBodyMass = (hcData["lean_body_mass"] as? Double) ?: 0.0,
                         sleep = if (hcData["sleep_seconds"] != null) mapOf("total" to (hcData["sleep_seconds"] as Long)) else emptyMap(),
                         sleepStages = (hcData["sleep_stages"] as? Map<String, Long>) ?: emptyMap(),
                         hrHistory = (hcData["hr_history"] as? List<Map<String, Any>>) ?: emptyList(),
                         stepsHistory = (hcData["steps_history"] as? List<Long>) ?: List(24) { 0L },
                         weeklyActivity = weeklyTrends,
                         isWearing = false,
                         lastUpdated = getDateTitle()
                     )
                }
                
                _healthData.value = newHealthData
            }
        }
    }

    fun syncHealthConnectData() {
        if (!::healthConnectManager.isInitialized) {
             _syncMessage.value = "Health Connect not initialized"
             return
        }
        
        viewModelScope.launch {
            _syncMessage.value = "Syncing..."
            if (healthConnectManager.hasPermissions()) {
                val now = java.time.Instant.now()
                // Sync detailed data
                 val hcData = healthConnectManager.readDataForDate(now.minus(24, java.time.temporal.ChronoUnit.HOURS), now)
                 
                if (hcData.isNotEmpty()) {
                    Log.d("FirebaseViewModel", "Syncing HC Data: $hcData")
                    val database = FirebaseDatabase.getInstance()
                    val ref = database.getReference("health_data")
                    
                    val updateMap = mutableMapOf<String, Any>()
                    hcData["spo2"]?.let { updateMap["spo2"] = it }
                    hcData["body_fat"]?.let { updateMap["body_fat"] = it }
                    hcData["weight"]?.let { updateMap["weight"] = it }
                    hcData["lean_body_mass"]?.let { updateMap["lean_body_mass"] = it }
                    
                    hcData["sleep_seconds"]?.let { updateMap["sleep/total"] = it }
                    hcData["sleep_stages"]?.let { updateMap["sleep/stages"] = it }
                    
                    // Add Steps, Calories, Active Time
                    hcData["steps"]?.let { updateMap["steps"] = it }
                    hcData["calories"]?.let { updateMap["calories"] = it }
                    hcData["active_time_minutes"]?.let { updateMap["active_minutes"] = it }
                    
                    // "active_time_minutes" is the key from readDataForDate. Key in HealthData is just internal.
                    // Firebase root usually implies flat structure or specific keys.
                    // Let's assume root "active_minutes" or similar?
                    // Actually, Dashboard reads "activeMinutes" from HealthData, but HealthData is parsed from HC or Firebase.
                    // Firebase listener (Line 77+) doesn't seem to parse 'active_minutes' from root yet!
                    // Wait, I need to check Firebase Listener parsing (Line 77-177).
                    // It parses HR, Steps, Cals, SpO2... 
                    // It does NOT parse active_minutes!
                    // See line 161: activeMinutes = (hcData["active_time_minutes"] as? Long) ?: ...
                    // BUT initialized with default 0 in line 34.
                    // Listener (lines 77-177) does NOT read "active_minutes" or "active_time".
                    // So even if I write it, it won't be read back.
                    // I need to update listener to read it too.
                    hcData["active_time_minutes"]?.let { updateMap["active_minutes"] = it }
                    
                    // Sync Hourly Steps & HR History
                    hcData["steps_history"]?.let { updateMap["steps_history"] = it }
                    hcData["hr_history"]?.let { updateMap["history/heart_rate"] = it }

                    ref.updateChildren(updateMap)
                        .addOnSuccessListener { 
                            _syncMessage.value = "Synced ${hcData.size} data points" 
                        }
                        .addOnFailureListener { e -> 
                            _syncMessage.value = "Sync failed: ${e.message}"
                        }
                        
                    // Sync Trends (7 Days)
                    val trends = healthConnectManager.readWeeklyTrends()
                    if (trends.isNotEmpty()) ref.child("trends").setValue(trends)
                    
                    // Sync Body Comp History
                    val start30 = now.minus(30, java.time.temporal.ChronoUnit.DAYS)
                    val bcHistory = healthConnectManager.readBodyCompHistory(start30, now)
                    if (bcHistory.isNotEmpty()) ref.child("history").child("body_comp").setValue(bcHistory)

                } else {
                    _syncMessage.value = "No new data found in Health Connect"
                }
            } else {
                _syncMessage.value = "Permissions missing!"
                Log.w("FirebaseViewModel", "Health Connect permissions missing")
            }
        }
    }
}
