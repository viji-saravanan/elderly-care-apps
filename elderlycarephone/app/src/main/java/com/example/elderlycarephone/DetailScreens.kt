package com.example.elderlycarephone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

// --- Detail Screens ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateDetailScreen(viewModel: FirebaseViewModel, navController: NavController) {
    val healthData by viewModel.healthData.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Heart Rate") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("⬅")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Large Current Value
                Text("${healthData.heartRate} bpm", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("Last updated: ${healthData.lastUpdated}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            item {
                // The Graph
                HeartRateGraphCard(healthData.hrHistory)
            }
            item {
                Text("Recent History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            // List of readings
            items(healthData.hrHistory.takeLast(20).reversed()) { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val time = entry["time"] as? Long ?: 0L
                    val date = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault())
                    Text("${date.hour}:${date.minute.toString().padStart(2,'0')}", style = MaterialTheme.typography.bodyMedium)
                    Text("${entry["value"]} bpm", fontWeight = FontWeight.Bold)
                }
                Divider(color = Color.LightGray.copy(alpha=0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepDetailScreen(viewModel: FirebaseViewModel, navController: NavController) {
    val healthData by viewModel.healthData.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                navigationIcon = {
                     IconButton(onClick = { navController.popBackStack() }) { Text("⬅") }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                val totalSeconds = healthData.sleep["total"] ?: 0L
                val h = totalSeconds / 3600
                val m = (totalSeconds % 3600) / 60
                
                Text("${h}h ${m}m", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Text("Total Sleep Duration", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
            item {
                SleepStagesCard(healthData.sleepStages)
            }
            item {
                Text("Blood Oxygen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(
                     colors = CardDefaults.cardColors(containerColor = Color.White),
                     modifier = Modifier.fillMaxWidth()
                ) {
                     Column(Modifier.padding(16.dp)) {
                         Text("Average", fontSize = 14.sp, color = Color.Gray)
                         Row(verticalAlignment = Alignment.Bottom) {
                             Text("${healthData.spo2}%", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                             Spacer(Modifier.width(8.dp))
                             Text("(Sleep Avg)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(bottom=6.dp))
                         }
                         
                         // Visual Indicator
                         Spacer(Modifier.height(8.dp))
                         LinearProgressIndicator(
                             progress = (healthData.spo2.toFloat() / 100f).coerceIn(0f, 1f),
                             modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                             color = if(healthData.spo2 > 90) Color(0xFF4CAF50) else Color(0xFFFF5252),
                             trackColor = Color.LightGray.copy(alpha=0.3f)
                         )
                         if (healthData.spo2 > 0 && healthData.spo2 < 90) {
                             Spacer(Modifier.height(4.dp))
                             Text("Low Oxygen Level Detected", color = Color.Red, fontSize = 12.sp)
                         }
                     }
                }
            }
            item {
                Text("Weekly Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TrendsCard(healthData.trends)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyCompDetailScreen(viewModel: FirebaseViewModel, navController: NavController) {
    val healthData by viewModel.healthData.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar( 
                title = { Text("Body Composition") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Text("⬅") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
             item {
                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Text("${healthData.weight} kg", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                         Text("Weight", style = MaterialTheme.typography.bodySmall)
                     }
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Text("${healthData.bodyFat} %", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                         Text("Body Fat", style = MaterialTheme.typography.bodySmall)
                     }
                 }
             }
             item {
                 BodyCompHistoryCard(healthData.bodyCompHistory)
             }
             item {
                 Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
             }
             items(healthData.bodyCompHistory.takeLast(10).reversed()) { entry ->
                 Row(modifier = Modifier.fillMaxWidth().padding(vertical=8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                     val date = entry["date"] as? Long ?: 0L
                     val dateStr = java.time.LocalDate.ofEpochDay(date / 86400000).toString()
                     Text(dateStr, style = MaterialTheme.typography.bodyMedium)
                     Row {
                         if (entry.containsKey("weight")) Text("${entry["weight"]}kg ", fontWeight = FontWeight.Bold)
                         if (entry.containsKey("body_fat")) Text("(${entry["body_fat"]}%)", color = Color.Gray)
                     }
                 }
                 Divider()
             }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(viewModel: FirebaseViewModel, navController: NavController) {
    val healthData by viewModel.healthData.collectAsState()
    
    // Auto-load if empty (optional, but good for safety)
    LaunchedEffect(Unit) {
        viewModel.loadDataForCurrentDate()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Daily activity", color = Color.White)
                        Text(healthData.lastUpdated, color = Color.Gray, fontSize = 12.sp) // Shows "Today", "Yesterday", etc.
                    }
                },
                navigationIcon = {
                     IconButton(onClick = { navController.popBackStack() }) {
                         Text("⬅", color=Color.White)
                     }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                actions = {
                    IconButton(onClick = { viewModel.changeDateOffset(-1) }) { Text("◀", color=Color.White) }
                    IconButton(onClick = { viewModel.changeDateOffset(1) }) { Text("▶", color=Color.White) }
                    IconButton(onClick = {}) { Text("📅", fontSize=20.sp) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Week View (Real Data)
            item {
                val selectedDateMillis by viewModel.selectedDateMillis.collectAsState()
                
                val scrollState = androidx.compose.foundation.rememberScrollState()
                
                // Fallback for empty data (show last 7 days empty)
                val displayData = if (healthData.weeklyActivity.isNotEmpty()) healthData.weeklyActivity else {
                    (6 downTo 0).map { i ->
                        val date = java.time.LocalDate.now().minusDays(i.toLong())
                        mapOf(
                            "date" to date.toEpochDay() * 86400000,
                            "steps" to 0L,
                            "calories" to 0L,
                            "active_minutes" to 0L
                        )
                    }
                }
                
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 4.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp) 
                ) {
                    displayData.forEach { dayMap ->
                         val steps = (dayMap["steps"] as? Number)?.toLong() ?: 0L
                         val calories = (dayMap["calories"] as? Number)?.toLong() ?: 0L
                         val activeMin = (dayMap["active_minutes"] as? Number)?.toLong() ?: 0L
                         val dateMillis = dayMap["date"] as? Long ?: 0L
                         
                         // Format Date: DD/MM
                         val dateObj = java.util.Date(dateMillis)
                         val dateLabel = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(dateObj)
                         val isSelected = (dateMillis == selectedDateMillis)
                         
                         Column(
                             horizontalAlignment = Alignment.CenterHorizontally,
                             modifier = Modifier
                                .clickable { viewModel.jumpToDate(dateMillis) }
                                .padding(4.dp)
                         ) {
                             // Highlight background if selected
                             Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF333333) else Color.Transparent)
                                    .border(if (isSelected) 1.dp else 0.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                    .padding(4.dp), 
                                contentAlignment=Alignment.Center
                             ) {
                                  // Triple Rings (Concentric)
                                  Canvas(Modifier.size(34.dp)) {
                                      // Steps (Outer - Green)
                                      val r = size.minDimension / 2
                                      val inset = 3.dp.toPx()
                                      val stroke = 2.dp.toPx()
                                      
                                      // Ring 1 (Steps)
                                      val r1 = r - inset/2
                                      drawArc(Color(0xFF00E676).copy(alpha=0.2f), 0f, 360f, false, style=Stroke(stroke))
                                      drawArc(Color(0xFF00E676), -90f, ((steps/6000f).coerceIn(0f,1f))*360f, false, style=Stroke(stroke, cap=StrokeCap.Round))

                                      // Ring 2 (Active Time)
                                      val inset2 = inset
                                      drawArc(Color(0xFF00B0FF).copy(alpha=0.2f), 0f, 360f, false, style=Stroke(stroke), topLeft=Offset(inset2, inset2), size=Size(size.width-2*inset2, size.height-2*inset2))
                                      drawArc(Color(0xFF00B0FF), -90f, ((activeMin/60f).coerceIn(0f,1f))*360f, false, style=Stroke(stroke, cap=StrokeCap.Round), topLeft=Offset(inset2, inset2), size=Size(size.width-2*inset2, size.height-2*inset2))

                                      // Ring 3 (Calories)
                                      val inset3 = 2*inset
                                      drawArc(Color(0xFFFF4081).copy(alpha=0.2f), 0f, 360f, false, style=Stroke(stroke), topLeft=Offset(inset3, inset3), size=Size(size.width-2*inset3, size.height-2*inset3))
                                      drawArc(Color(0xFFFF4081), -90f, ((calories/500f).coerceIn(0f,1f))*360f, false, style=Stroke(stroke, cap=StrokeCap.Round), topLeft=Offset(inset3, inset3), size=Size(size.width-2*inset3, size.height-2*inset3))
                                  }
                             }
                             Spacer(Modifier.height(4.dp))
                             Text(dateLabel, color = if(isSelected) Color.White else Color.Gray, fontSize=10.sp, fontWeight=if(isSelected) FontWeight.Bold else FontWeight.Normal)
                         }
                    }
                }
            }
            
            // 2. Main Large Rings
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LargeActivityRings(healthData)
                }
            }
            
            // 3. Stats Row
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ActivityStat("Steps", "${healthData.steps}", "/6,000", Color(0xFF00E676))
                    ActivityStat("Active time", "${healthData.activeMinutes}", "/60 mins", Color(0xFF00B0FF))
                    ActivityStat("Exercise calories", "${healthData.calories}", "/500 Cal", Color(0xFFFF4081))
                }
            }
            
            // 4. Totals (Mocked for now as we don't have this breakdown yet, using placeholders)
            item {
                 Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                          Text("Total burned calories", color = Color.Gray)
                          Text("--", color = Color.White, fontWeight = FontWeight.Bold)
                      }
                      Divider(color = Color.DarkGray)
                      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                          Text("Distance while active", color = Color.Gray)
                          Text("--", color = Color.White, fontWeight = FontWeight.Bold)
                      }
                 }
            }
            
            // 5. Graphs
            item {
                 Text("Steps", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                 Spacer(Modifier.height(16.dp))
                 ActivityBarChart(healthData.stepsHistory, Color(0xFF00E676), "Steps")
            }
            
            item {
                 Text("Active time", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                 Spacer(Modifier.height(16.dp))
                 // Active Time Graph (Mock/Empty if not available in HealthData historical breakdown logic yet)
                 ActivityBarChart(List(24){ 0L }, Color(0xFF00B0FF), "Mins")
            }
            
            item {
                 Text("Exercise calories", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                 Spacer(Modifier.height(30.dp))
                 Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                     Text("No recorded data", color = Color.Gray)
                 }
            }
        }
    }
}

@Composable
fun ActivityStat(label: String, value: String, goal: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
             Text("👟", fontSize=12.sp, color = color) // Icon placeholder
             Spacer(Modifier.width(4.dp))
             Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(goal, color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun LargeActivityRings(data: HealthData) {
    val size = 200.dp 
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 20.dp.toPx()
        val cap = StrokeCap.Round
        
        // Steps (Outer - Green)
        val stepsProg = (data.steps / 6000f).coerceIn(0f, 1f)
        drawArc(Color(0xFF00E676).copy(alpha=0.2f), 0f, 360f, false, style=Stroke(strokeWidth))
        drawArc(Color(0xFF00E676), -90f, stepsProg * 360f, false, style=Stroke(strokeWidth, cap=cap))
        
        // Time (Middle - Blue)
        val timeWidth = size.toPx() - (strokeWidth * 2.5f)
        val timeStartOff = strokeWidth * 1.25f
        val timeProg = (data.activeMinutes / 60f).coerceIn(0f, 1f)
        drawArc(Color(0xFF00B0FF).copy(alpha=0.2f), 0f, 360f, false, topLeft=Offset(timeStartOff, timeStartOff), size=Size(timeWidth, timeWidth), style=Stroke(strokeWidth))
        drawArc(Color(0xFF00B0FF), -90f, timeProg * 360f, false, topLeft=Offset(timeStartOff, timeStartOff), size=Size(timeWidth, timeWidth), style=Stroke(strokeWidth, cap=cap))

        // Cals (Inner - Pink)
        val calWidth = size.toPx() - (strokeWidth * 5f)
        val calStartOff = strokeWidth * 2.5f
        val calProg = (data.calories / 500f).coerceIn(0f, 1f)
        drawArc(Color(0xFFFF4081).copy(alpha=0.2f), 0f, 360f, false, topLeft=Offset(calStartOff, calStartOff), size=Size(calWidth, calWidth), style=Stroke(strokeWidth))
        drawArc(Color(0xFFFF4081), -90f, calProg * 360f, false, topLeft=Offset(calStartOff, calStartOff), size=Size(calWidth, calWidth), style=Stroke(strokeWidth, cap=cap))
    }
}

@Composable
fun ActivityBarChart(data: List<Long>, color: Color, unit: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), modifier = Modifier.fillMaxWidth().height(150.dp)) {
        Box(Modifier.padding(16.dp).fillMaxSize()) {
            if (data.all { it == 0L }) {
                Text("No Data", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val max = data.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
                    val barWidth = size.width / 24f * 0.6f
                    val step = size.width / 24f
                    
                    data.forEachIndexed { i, value ->
                        val h = (value / max) * size.height
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(i * step + (step - barWidth)/2, size.height - h),
                            size = Size(barWidth, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                    
                    // X Axis Labels (0, 6, 12, 18)
                    val p = android.graphics.Paint().apply { 
                        setColor(android.graphics.Color.GRAY)
                        textSize = 24f 
                    }
                    drawContext.canvas.nativeCanvas.drawText("12 AM", 0f, size.height + 30f, p)
                    drawContext.canvas.nativeCanvas.drawText("12 PM", size.width/2, size.height + 30f, p)
                    drawContext.canvas.nativeCanvas.drawText("(h)", size.width - 40f, size.height + 30f, p)
                }
            }
        }
    }
}
