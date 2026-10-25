package com.example.elderlycarephone

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(LeanBodyMassRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.ExerciseSessionRecord::class)
    )

    suspend fun hasPermissions(): Boolean {
        try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            return granted.containsAll(permissions)
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Permission check failed", e)
            return false
        }
    }

    fun checkAvailability(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }
    
    fun requestPermissionsActivityContract(): androidx.activity.result.contract.ActivityResultContract<Set<String>, Set<String>> {
        return androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    }

    suspend fun readDataForDate(start: Instant, end: Instant): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        if (!hasPermissions()) return result

        val timeRange = TimeRangeFilter.between(start, end)

        try {
            // Read SpO2
            val spo2Records = healthConnectClient.readRecords(
                ReadRecordsRequest(OxygenSaturationRecord::class, timeRange)
            ).records
            if (spo2Records.isNotEmpty()) {
                val avg = spo2Records.map { it.percentage.value }.average()
                result["spo2"] = avg
            }

            // Read Body Fat
            val bodyFatRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(BodyFatRecord::class, timeRange)
            ).records
            if (bodyFatRecords.isNotEmpty()) {
                result["body_fat"] = bodyFatRecords.last().percentage.value
            }

            // Read Weight
            val weightRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(WeightRecord::class, timeRange)
            ).records
            if (weightRecords.isNotEmpty()) {
                result["weight"] = weightRecords.last().weight.inKilograms
            }

            // Read Lean Body Mass
            val lbmRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(LeanBodyMassRecord::class, timeRange)
            ).records
            if (lbmRecords.isNotEmpty()) {
                result["lean_body_mass"] = lbmRecords.last().mass.inKilograms
            }

            // Read Sleep
            val sleepRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(SleepSessionRecord::class, timeRange)
            ).records
            
            if (sleepRecords.isNotEmpty()) {
                val totalDurationSeconds = sleepRecords.sumOf { 
                     java.time.Duration.between(it.startTime, it.endTime).seconds 
                }
                result["sleep_seconds"] = totalDurationSeconds
                
                val stagesMap = mutableMapOf<String, Long>()
                sleepRecords.forEach { session ->
                    session.stages.forEach { stage ->
                        val duration = java.time.Duration.between(stage.startTime, stage.endTime).seconds
                        val stageName = when(stage.stage) {
                            SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
                            SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
                            SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
                            SleepSessionRecord.STAGE_TYPE_REM -> "rem"
                            else -> "other"
                        }
                        stagesMap[stageName] = (stagesMap[stageName] ?: 0L) + duration
                    }
                }
                result["sleep_stages"] = stagesMap
            }
            
            // Read Steps
             val stepsRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(StepsRecord::class, timeRangeFilter = timeRange)
            ).records
            val totalSteps = stepsRecords.sumOf { it.count }
            result["steps"] = totalSteps
            
            // Read Calories (Total)
            val caloriesRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class, timeRange)
            ).records
            val totalCalories = caloriesRecords.sumOf { it.energy.inKilocalories }
            result["calories"] = totalCalories.toLong()

            // Read Active Time (Exercise Sessions)
            val exerciseRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(androidx.health.connect.client.records.ExerciseSessionRecord::class, timeRange)
            ).records
            val activeTimeSeconds = exerciseRecords.sumOf { 
                java.time.Duration.between(it.startTime, it.endTime).seconds 
            }
            // If active time is 0, let's derive roughly from steps (optional fallback: 100 steps = 1 min)
            // But let's stick to true data first.
            result["active_time_minutes"] = activeTimeSeconds / 60
            
            // Bucket Steps by Hour for Graph (0..23)
            val stepsByHour = LongArray(24) { 0 }
            stepsRecords.forEach { record ->
                val hour = record.startTime.atZone(java.time.ZoneId.systemDefault()).hour
                if (hour in 0..23) {
                    stepsByHour[hour] = stepsByHour[hour] + record.count
                }
            }
            result["steps_history"] = stepsByHour.toList()

            // Read HR History for Graph
             val hrRecords = healthConnectClient.readRecords(
                ReadRecordsRequest(HeartRateRecord::class, timeRange)
            ).records
             
             // Convert HR samples to List<Map>
             val hrHistory = hrRecords.flatMap { record ->
                 record.samples.map { sample ->
                     mapOf("time" to sample.time.toEpochMilli(), "value" to sample.beatsPerMinute.toDouble())
                 }
             }.sortedBy { it["time"] as Long }
             
             result["hr_history"] = hrHistory

        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading HC data", e)
        }

        return result
    }

    suspend fun readWeeklyTrends(anchor: Instant = Instant.now()): List<Map<String, Any>> {
        if (!hasPermissions()) return emptyList()

        // Week surrounding the anchor? Or ending at anchor? User screenshot shows "12/11" highlighted in a week row.
        // Usually week view is Sunday-Saturday or Monday-Sunday containing the date.
        // Let's do: Week containing the anchor, assuming Week starts on Sunday.
        val zone = java.time.ZoneId.systemDefault()
        val anchorDate = anchor.atZone(zone).toLocalDate()
        // Find start of week (Sunday)
        val dayOfWeek = anchorDate.dayOfWeek.value % 7 // 1=Mon...7=Sun -> 0..6? No, java DayOfWeek 1(Mon)-7(Sun). %7 => 1=1, 7=0(Sun). if we want Sun start.
        // Let's assume Sun start (0). DayOfWeek.SUNDAY is 7.
        // To get Sunday before or equal:
        val daysToSubtract = if (anchorDate.dayOfWeek == java.time.DayOfWeek.SUNDAY) 0 else anchorDate.dayOfWeek.value
        val startOfWeek = anchorDate.minusDays(daysToSubtract.toLong())
        
        val resultList = mutableListOf<Map<String, Any>>()


        try {
            // Iterate 7 days from startOfWeek
            for (i in 0 until 7) {
                val dayDate = startOfWeek.plusDays(i.toLong())
                val dayStart = dayDate.atStartOfDay(zone).toInstant()
                val dayEnd = dayDate.plusDays(1).atStartOfDay(zone).toInstant()
                val range = TimeRangeFilter.between(dayStart, dayEnd)
                
                // Steps
                val stepsRecords = healthConnectClient.readRecords(
                    ReadRecordsRequest(StepsRecord::class, range)
                ).records
                val totalSteps = stepsRecords.sumOf { it.count }

                // Sleep
                val sleepRecords = healthConnectClient.readRecords(
                    ReadRecordsRequest(SleepSessionRecord::class, range)
                ).records
                val totalSleepSeconds = sleepRecords.sumOf { 
                    java.time.Duration.between(it.startTime, it.endTime).seconds 
                }
                
                // Calories
                val caloriesRecords = healthConnectClient.readRecords(
                    ReadRecordsRequest(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class, range)
                ).records
                val totalCalories = caloriesRecords.sumOf { it.energy.inKilocalories }

                // Active Time
                val exerciseRecords = healthConnectClient.readRecords(
                    ReadRecordsRequest(androidx.health.connect.client.records.ExerciseSessionRecord::class, range)
                ).records
                val activeTimeMinutes = exerciseRecords.sumOf { 
                    java.time.Duration.between(it.startTime, it.endTime).seconds 
                } / 60
                
                resultList.add(mapOf(
                    "date" to dayDate.toEpochDay() * 86400000, // Ms
                    "day_name" to dayDate.dayOfWeek.name.take(1), // S, M, T...
                    "steps" to totalSteps,
                    "calories" to totalCalories.toLong(),
                    "active_minutes" to activeTimeMinutes,
                    "sleep_seconds" to totalSleepSeconds
                ))
            }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading weekly trends", e)
        }
        return resultList
    }
    
    suspend fun readBodyCompHistory(start: java.time.Instant, end: java.time.Instant): List<Map<String, Any>> {
        if (!hasPermissions()) return emptyList()
        val result = mutableListOf<Map<String, Any>>()
        try {
            val request = ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val fatRecords = healthConnectClient.readRecords(request).records
            
            // We also need weight to match
            val valRequest = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
            val weightRecords = healthConnectClient.readRecords(valRequest).records
            
            // Merge by date (approximate) or just return list of points
            // Let's return a list where each entry can have weight or fat or both
            // Map keys: "time", "weight", "body_fat"
            
            // Simple approach: Iterate days? Or just returning raw points.
            // Returning raw points is better for graph.
            val points = mutableMapOf<Long, MutableMap<String, Any>>()
            
            fatRecords.forEach { 
                val time = it.time.toEpochMilli()
                // bucketing by hour/day to merge? let's bucket by day to align
                 val day = it.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                 val p = points.getOrPut(day) { mutableMapOf("date" to day * 86400000) }
                 p["body_fat"] = it.percentage.value
            }
            
            weightRecords.forEach {
                 val day = it.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                 val p = points.getOrPut(day) { mutableMapOf("date" to day * 86400000) }
                 p["weight"] = it.weight.inKilograms
            }
            
            result.addAll(points.values)
            result.sortBy { (it["date"] as Long) }
            
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading body comp history", e)
        }
        return result
    }
}
