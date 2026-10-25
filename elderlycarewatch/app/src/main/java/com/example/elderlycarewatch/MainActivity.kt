package com.example.elderlycarewatch

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) {
                registerHealthService()
            } else {
                Toast.makeText(this, "Permissions required for monitoring", Toast.LENGTH_SHORT).show()
            }
        }

    private lateinit var healthManager: HealthManager
    private val batteryReceiver = BatteryReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        healthManager = HealthManager(this)
        
        requestPermissionLauncher.launch(permissions)

        setContent {
            WearApp()
        }
    }

    override fun onStart() {
        super.onStart()
        // Start the Foreground Service to keep monitoring active
        val intent = android.content.Intent(this, ActiveHealthService::class.java)
        startForegroundService(intent)
        
        // Ensure HistoryManager is initialized
        HistoryManager.getInstance(this)
        
        // Register Battery Receiver
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        // We do NOT stop the service here, to keep it running in background
        
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun registerHealthService() {
        lifecycleScope.launch {
            try {
                val client = HealthServices.getClient(this@MainActivity).passiveMonitoringClient
                val config = PassiveListenerConfig.builder()
                    .setDataTypes(setOf(
                        DataType.HEART_RATE_BPM, 
                        DataType.STEPS_DAILY, 
                        DataType.CALORIES_DAILY
                    ))
                    .build()
                
                client.setPassiveListenerServiceAsync(
                    HealthService::class.java,
                    config
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Composable
fun WearApp() {
    val heartRate = FirebaseRepository.latestHeartRate.collectAsState(initial = null).value
    val steps = FirebaseRepository.latestSteps.collectAsState(initial = null).value
    val calories = FirebaseRepository.latestCalories.collectAsState(initial = null).value
    val spo2 = FirebaseRepository.latestSpO2.collectAsState(initial = null).value
    
    val isSyncing = FirebaseRepository.isSyncing.collectAsState(initial = false).value
    val error = FirebaseRepository.errorMessage.collectAsState(initial = null).value

    androidx.wear.compose.material.ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Health Monitor",
                style = MaterialTheme.typography.title3,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center
            )
        }

        item {
            // Stats Grid
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Heart Rate
                MetricRow(label = "HR", value = "${heartRate?.toInt() ?: "--"}", unit = "BPM", color = androidx.compose.ui.graphics.Color.Red)
                
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
                
                // SpO2
                MetricRow(label = "SpO2", value = "${spo2?.toInt() ?: "--"}", unit = "%", color = androidx.compose.ui.graphics.Color.Cyan)

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

                // Steps
                MetricRow(label = "Steps", value = "${steps ?: "--"}", unit = "steps", color = androidx.compose.ui.graphics.Color.Green)

                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))

                // Calories
                MetricRow(label = "Kcal", value = "${calories ?: "--"}", unit = "kcal", color = androidx.compose.ui.graphics.Color(0xFFFFA500)) // Orange
            }
        }

        item {
            // Sync Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val statusColor = if (isSyncing) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Gray
                val statusText = if (isSyncing) "Syncing..." else "Idle"
                
                androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = statusColor)
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.caption2
                )
            }
        }

        if (error != null) {
            item {
                Text(
                    text = error,
                    style = MaterialTheme.typography.caption2,
                    color = androidx.compose.ui.graphics.Color.Red,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Buttons removed for automation
    }
}

@Composable
fun MetricRow(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = "$label: ", style = MaterialTheme.typography.caption1, color = androidx.compose.ui.graphics.Color.LightGray)
        Text(text = value, style = MaterialTheme.typography.body1, color = color)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(4.dp))
        Text(text = unit, style = MaterialTheme.typography.caption2, color = androidx.compose.ui.graphics.Color.Gray)
    }
}
