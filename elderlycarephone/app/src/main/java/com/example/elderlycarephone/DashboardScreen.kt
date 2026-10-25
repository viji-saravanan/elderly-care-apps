package com.example.elderlycarephone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavController
import androidx.compose.foundation.BorderStroke

// Permissions
val PERMISSIONS = setOf(
    HealthPermission.getReadPermission(HeartRateRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class),
    HealthPermission.getReadPermission(OxygenSaturationRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(LeanBodyMassRecord::class)
)


@Composable
fun AppNavigation(viewModel: FirebaseViewModel = viewModel()) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeDashboard(navController, viewModel) }
        composable("heart_rate") { HeartRateDetailScreen(viewModel, navController) }
        composable("sleep") { SleepDetailScreen(viewModel, navController) }
        composable("body_comp") { BodyCompDetailScreen(viewModel, navController) }
        composable("activity_detail") { ActivityDetailScreen(viewModel, navController) }
    }
}

// Map old entry point to new Navigation
@Composable
fun DashboardScreen(viewModel: FirebaseViewModel = viewModel()) {
    AppNavigation(viewModel)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(navController: NavController, viewModel: FirebaseViewModel) {
    val healthData by viewModel.healthData.collectAsState()
    val isCaretaker by viewModel.isCaretakerMode.collectAsState()
    val syncMsg by viewModel.syncMessage.collectAsState()
    val context = LocalContext.current
    
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(PERMISSIONS)) {
            viewModel.initializeHealthConnect(context)
        }
    }
    
    // Init (kept for safety)
    LaunchedEffect(Unit) {
        viewModel.initializeHealthConnect(context)
    }
    
    // Feedback Toast
    LaunchedEffect(syncMsg) {
        syncMsg?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Elderly Care", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Daily Vitals Monitor", color = Color.Gray, fontSize = 14.sp)
                }
                // Settings/Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                         checked = isCaretaker, 
                         onCheckedChange = { viewModel.toggleCaretakerMode(it) },
                         colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF00BCD4), uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color.DarkGray)
                    )
                     Spacer(Modifier.width(16.dp))
                    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF00BCD4), Color(0xFF2196F3)))), contentAlignment=Alignment.Center) {
                         Text("A", color=Color.White, fontWeight=FontWeight.Bold, fontSize=18.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Status Card (New)
            item { StatusHeader(healthData) }

            // 1. Daily Activity (Custom Linear)
            item { DailyActivityCardCustom(healthData) { navController.navigate("activity_detail") } }
            
            // 2. Sleep
            item { CustomSleepCard(healthData) { navController.navigate("sleep") } }
            
            // 3. Heart Rate
            item {
                CustomMetricTile(
                    title = "Heart Rate", value = "${healthData.heartRate.toInt()}", unit = "BPM",
                    icon = "❤️", iconColor = Color(0xFFFF5252),
                    contentRight = { RangeBarShort(40f, 150f, healthData.heartRate.toFloat(), Color(0xFFFF5252)) },
                    onClick = { navController.navigate("heart_rate") }
                )
            }
            
            // 4. Stress
            item {
                CustomMetricTile(
                    title = "Stress Level", value = "Low", unit = "", 
                    icon = "🧘", iconColor = Color(0xFF4CAF50),
                    contentRight = { RangeBarShort(0f, 100f, 25f, Color(0xFF4CAF50)) },
                    onClick = { /* No Detail yet */ }
                )
            }
            
            // 5. Blood Oxygen
            item {
                CustomMetricTile(
                    title = "SpO2", value = "${healthData.spo2.toInt()}%", unit = "",
                    subtitle = "Sleep Average",
                    icon = "💧", iconColor = Color(0xFF2196F3),
                    contentRight = { RangeBarShort(85f, 100f, healthData.spo2.toFloat(), Color(0xFF2196F3)) },
                    onClick = { navController.navigate("sleep") }
                )
            }
            
            // 6. Body Composition
            item {
                CustomActionTile(
                    title = "Body Composition", buttonText = "Update", icon = "⚖️", iconColor = Color(0xFF9C27B0),
                    onClick = { navController.navigate("body_comp") }
                )
            }
            
            // 7. Sync Button (Bottom)
             if (!isCaretaker) {
                item {
                    Spacer(Modifier.height(16.dp))
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val healthConnectManager = remember { HealthConnectManager(context) }
                    
                    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
                    val requestPermissions = rememberLauncherForActivityResult(healthConnectManager.requestPermissionsActivityContract()) {
                        viewModel.checkPermissions()
                    }
                    
                    if (permissionsGranted) {
                         Button(
                             onClick = { viewModel.syncHealthConnectData() },
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                             modifier = Modifier.fillMaxWidth().height(50.dp),
                             shape = RoundedCornerShape(24.dp)
                         ) { Text("Sync Now", color = Color.White) }
                    } else {
                         Button(
                             onClick = { requestPermissions.launch(healthConnectManager.permissions) },
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                             modifier = Modifier.fillMaxWidth().height(50.dp),
                             shape = RoundedCornerShape(24.dp)
                         ) { Text("Connect Health", color = Color.Black) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
             }
        }
    }
}

@Composable
fun StatusHeader(data: HealthData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Battery
        Row(verticalAlignment = Alignment.CenterVertically) {
            val batteryColor = if(data.batteryLevel > 20) Color(0xFF4CAF50) else Color(0xFFFF5252)
            Icon(
                painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_lock_idle_charging), // Simple fallback icon or vector
                contentDescription = "Battery",
                tint = batteryColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("${if(data.batteryLevel >= 0) data.batteryLevel else "--"}%", color = Color.Gray, fontSize = 12.sp)
        }
        
        // Wearing Status
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusColor = if(data.isWearing) Color(0xFF00E676) else Color(0xFFFF5252)
            val statusText = if(data.isWearing) "On Wrist" else "Not Wearing"
            
            Box(Modifier.size(8.dp).background(statusColor, androidx.compose.foundation.shape.CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(statusText, color = Color.Gray, fontSize = 12.sp)
        }
        
        // Time
        Text("Updated: ${data.lastUpdated.takeLast(8)}", color = Color.DarkGray, fontSize = 10.sp)
    }
}

// --- Custom "Arya" Components ---

@Composable
fun DailyActivityCardCustom(data: HealthData, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF333333)), 
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { onClick() }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activity Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            
            // Steps
            CustomProgressBar("Steps", data.steps.toFloat(), 6000f, Color(0xFF00BCD4), "${data.steps} / 6000")
            // Active Time
            CustomProgressBar("Active Time", data.activeMinutes.toFloat(), 60f, Color(0xFF00E676), "${data.activeMinutes} / 60 min")
            // Calories
            CustomProgressBar("Calories", data.calories.toFloat(), 500f, Color(0xFFFF4081), "${data.calories} / 500 kcal")
        }
    }
}

@Composable
fun CustomProgressBar(label: String, current: Float, max: Float, color: Color, valueText: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(valueText, fontSize = 12.sp, color = Color.LightGray)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = (current / max).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = Color(0xFF333333)
        )
    }
}

@Composable
fun CustomSleepCard(data: HealthData, onClick: () -> Unit) {
     Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF333333)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Sleep Score", color = Color.Gray, fontSize = 12.sp)
                Text("86", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Good Quality", color = Color(0xFF00BCD4), fontSize = 14.sp)
            }
            // Stats
            Column(horizontalAlignment = Alignment.End) {
                 val h = (data.sleep["total"] ?: 0) / 3600
                 val m = ((data.sleep["total"] ?: 0) % 3600) / 60
                 Text("${h}h ${m}m", color=Color.White, fontSize=20.sp, fontWeight=FontWeight.Bold)
                 Text("Duration", color=Color.Gray, fontSize=12.sp)
            }
        }
    }
}

@Composable
fun CustomMetricTile(title: String, value: String, unit: String, subtitle: String? = null, icon: String, iconColor: Color, contentRight: @Composable () -> Unit, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF333333)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(iconColor.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape), contentAlignment=Alignment.Center) {
                Text(icon, fontSize=20.sp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                 Text(title, color = Color.Gray, fontSize = 12.sp)
                 Row(verticalAlignment = Alignment.Bottom) {
                     Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                     if(unit.isNotEmpty()) Text(" $unit", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom=2.dp))
                 }
            }
            contentRight()
        }
    }
}

@Composable
fun CustomActionTile(title: String, buttonText: String, icon: String, iconColor: Color, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        border = BorderStroke(1.dp, Color(0xFF333333)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
             Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(iconColor.copy(alpha=0.1f), androidx.compose.foundation.shape.CircleShape), contentAlignment=Alignment.Center) {
                    Text(icon, fontSize=20.sp)
                }
                Spacer(Modifier.width(16.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
             }
             Button(
                 onClick = onClick,
                 colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                 contentPadding = PaddingValues(horizontal = 16.dp),
                 modifier = Modifier.height(32.dp)
             ) { Text(buttonText, fontSize=12.sp) }
        }
    }
}

@Composable
fun RangeBarShort(min: Float, max: Float, current: Float, tint: Color) {
    Box(Modifier.width(80.dp).height(24.dp)) {
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF333333)))
        val range = max - min
        val fraction = ((current - min) / range).coerceIn(0f, 1f)
        Box(Modifier.align(Alignment.CenterStart).padding(start = (80.dp * fraction)).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(tint))
    }
}

// --- Legacy Cards (Preserved for DetailScreens.kt) ---

@Composable
fun SleepStagesCard(stages: Map<String, Long>) {
    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("Sleep Stages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if(stages.isEmpty()) { Text("No Data") } else {
                val total = stages.values.sum().toFloat()
                Row(modifier = Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp))) {
                    stages["awake"]?.let { if(it>0) Box(Modifier.weight(it.toFloat()).fillMaxHeight().background(Color(0xFFFF9800))) }
                    stages["rem"]?.let { if(it>0) Box(Modifier.weight(it.toFloat()).fillMaxHeight().background(Color(0xFF9C27B0))) }
                    stages["light"]?.let { if(it>0) Box(Modifier.weight(it.toFloat()).fillMaxHeight().background(Color(0xFF03A9F4))) }
                    stages["deep"]?.let { if(it>0) Box(Modifier.weight(it.toFloat()).fillMaxHeight().background(Color(0xFF3F51B5))) }
                }
            }
        }
    }
}

@Composable
fun TrendsCard(trends: List<Map<String, Any>>) {
    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("7 Day Sleep Trend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Graph Placeholder") }
        }
    }
}

@Composable
fun HeartRateGraphCard(history: List<Map<String, Any>>) {
     Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text("Heart Rate (24h)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
             if(history.isEmpty()) Text("No Data")
             else {
                 val points = history.mapNotNull { (it["value"] as? Number)?.toFloat() }
                 if(points.isNotEmpty()) {
                     Canvas(Modifier.fillMaxSize()) {
                         val max = points.maxOrNull()?:100f; val min = points.minOrNull()?:40f
                         val path = Path(); val w = size.width; val h = size.height
                         points.forEachIndexed { i, v -> 
                             val x = i * (w / (points.size-1)); val y = h - ((v-min)/(max-min)*h)
                             if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                         }
                         drawPath(path, Color.Red, style = Stroke(3f))
                     }
                 }
             }
        }
     }
}

@Composable
fun BodyCompHistoryCard(history: List<Map<String, Any>>) {
     Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().height(250.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
         Column(Modifier.padding(16.dp)) {
             Text("Weight History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              if(history.isEmpty()) Text("No Data")
              else {
                  val points = history.mapNotNull { (it["weight"] as? Number)?.toFloat() }
                   if(points.isNotEmpty()) {
                     Canvas(Modifier.fillMaxSize()) {
                         val max = points.maxOrNull()?:100f; val min = points.minOrNull()?:0f
                         val path = Path(); val w = size.width; val h = size.height
                         points.forEachIndexed { i, v -> 
                             val x = i * (w / (points.size-1).coerceAtLeast(1)); val y = h - ((v-min)/(max-min).coerceAtLeast(1f)*h)
                             if(i==0) path.moveTo(x,y) else path.lineTo(x,y)
                         }
                         drawPath(path, Color.Green, style = Stroke(3f))
                     }
                   }
              }
         }
     }
}
