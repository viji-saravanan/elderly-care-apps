package com.example.elderlycarewatch

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages local history of health data (specifically Heart Rate) for standalone operation.
 * Aggregates incoming data into 30-minute buckets and syncs to Firebase.
 */
class HistoryManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: HistoryManager? = null

        fun getInstance(context: Context): HistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }


    private val prefs: SharedPreferences = context.getSharedPreferences("health_history_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-memory buffer for current bucket
    private val currentBucketData = CopyOnWriteArrayList<Double>()
    private var currentBucketSteps: Long = 0
    private var currentBucketStartTime: Long = 0
    
    // Sleep Data
    private var totalSleepSeconds: Long = 0

    // Persisted 24h History: List of Maps { "time": Long, "value": Double }
    private var historyList: MutableList<Map<String, Any>> = mutableListOf()

    init {
        loadHistory()
        // Initialize bucket start time if needed
        if (currentBucketStartTime == 0L) {
            currentBucketStartTime = getBucketStartTime(System.currentTimeMillis())
        }
    }

    /**
     * Adds a new heart rate sample.
     * Checks if it belongs to the current bucket. If time has moved to next bucket,
     * finalizes the old one, saves it, and starts new one.
     */
    fun addHeartRate(bpm: Double) {
        val now = System.currentTimeMillis()
        val bucketStart = getBucketStartTime(now)

        if (bucketStart > currentBucketStartTime) {
            // We moved to a new bucket. Finalize the previous one.
            finalizeCurrentBucket()
            currentBucketStartTime = bucketStart
            currentBucketData.clear()
            currentBucketSteps = 0
        }

        currentBucketData.add(bpm)
    }

    fun addSteps(steps: Long) {
        // We only care about steps *during* this bucket to detect motion
        currentBucketSteps += steps
    }

    private fun finalizeCurrentBucket() {
        if (currentBucketData.isEmpty()) return

        val avg = currentBucketData.average()
        val timestamp = currentBucketStartTime
        
        // --- Sleep Heuristic ---
        checkForSleep(avg, currentBucketSteps, timestamp)

        // Check for Day Change (Trends)
        checkDayChange(timestamp)

        // Add to history
        val entry = mapOf("time" to timestamp, "value" to avg)
        historyList.add(entry)

        // Prune old data (> 24 hours)
        pruneOldHistory()

        // Save local
        saveHistory()

        // Sync to Firebase
        syncToFirebase()
    }
    
    private fun checkForSleep(avgHr: Double, steps: Long, timestamp: Long) {
        val hour = java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), java.time.ZoneId.systemDefault()).hour
        // Night window: 10 PM (22) to 8 AM (8)
        val isNight = hour >= 22 || hour < 8
        
        // Low movement and Low HR
        val isStill = steps < 50 // Minimal movement
        val isResting = avgHr < 75 // Generic resting threshold, ideally should be dynamic
        
        if (isNight && isStill && isResting) {
             // Add 30 minutes (1800 seconds) to sleep total
             totalSleepSeconds += 1800
             Log.d("HistoryManager", "Sleep Detected! Total: $totalSleepSeconds")
        }
    }

    private fun pruneOldHistory() {
        val twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli()
        historyList.removeAll { 
            val time = it["time"] as? Long ?: 0L
            time < twentyFourHoursAgo 
        }
    }

    // Trends Data (Last 7 Days)
    private var trendsList: MutableList<Map<String, Any>> = mutableListOf()
    private var lastDaySaved: Long = 0L // Epoch Day
    private var dailyMaxSteps: Long = 0
    
    fun updateDailyStats(steps: Long) {
        // Track max steps seen today for trends
        if (steps > dailyMaxSteps) dailyMaxSteps = steps
    }

    private fun checkDayChange(timestamp: Long) {
        val currentDay = timestamp / (24 * 60 * 60 * 1000)
        
        if (lastDaySaved == 0L) {
            lastDaySaved = currentDay
            return
        }
        
        if (currentDay > lastDaySaved) {
            // Day changed, save yesterday's stats
            val yesterdayMillis = lastDaySaved * 24 * 60 * 60 * 1000
            
            val entry = mapOf(
                "date" to yesterdayMillis,
                "steps" to dailyMaxSteps,
                "sleep_seconds" to totalSleepSeconds // Assuming this resets too?
            )
            
            trendsList.add(entry)
            
            // Keep only last 7
            if (trendsList.size > 7) trendsList.removeAt(0)
            
            // Reset for new day
            dailyMaxSteps = 0
            // totalSleepSeconds should ideally be reset if it's "last night's sleep". 
            // Simple logic: Reset sleep counter at noon? Or just reset here.
            totalSleepSeconds = 0 
            
            lastDaySaved = currentDay
            saveHistory()
            syncToFirebase()
        }
    }

    private fun syncToFirebase() {
        scope.launch {
            Log.d("HistoryManager", "Syncing history to Firebase: ${historyList.size} entries")
            // Re-using the existing repository methods
            val sleepMap = mapOf("total" to totalSleepSeconds)
            FirebaseRepository.updateHealthData(
                hrHistory = historyList.toList(),
                sleepData = sleepMap,
                trends = trendsList.toList()
            )
        }
    }
    
    private fun getBucketStartTime(timestamp: Long): Long {
        // Round down to nearest 30 minutes
        val thirtyMinMillis = 30 * 60 * 1000
        return (timestamp / thirtyMinMillis) * thirtyMinMillis
    }

    private fun saveHistory() {
        val historyJson = gson.toJson(historyList)
        val trendsJson = gson.toJson(trendsList)
        prefs.edit()
            .putString("hr_history_json", historyJson)
            .putString("trends_json", trendsJson)
            .putLong("last_day_saved", lastDaySaved)
            .apply()
    }

    private fun loadHistory() {
        val json = prefs.getString("hr_history_json", null)
        if (json != null) {
            try {
                val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
                historyList = gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("HistoryManager", "Error loading history", e)
            }
        }
        
        val trendsJson = prefs.getString("trends_json", null)
        if (trendsJson != null) {
            try {
                val type = object : TypeToken<MutableList<Map<String, Any>>>() {}.type
                trendsList = gson.fromJson(trendsJson, type)
            } catch (e: Exception) {
                Log.e("HistoryManager", "Error loading trends", e)
            }
        }
        
        lastDaySaved = prefs.getLong("last_day_saved", 0L)
    }
}
