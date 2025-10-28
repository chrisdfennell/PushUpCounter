// app/src/main/java/com/fennell/pushupcounter/presentation/PushupCounterActivity.kt
package com.fennell.pushupcounter.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop // Correct Icon import needed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button // Correct Wear Compose Button
import androidx.wear.compose.material.ButtonDefaults // Correct Wear Compose ButtonDefaults
import androidx.wear.compose.material.Icon // Correct Wear Compose Icon
import androidx.wear.compose.material.MaterialTheme // Correct Wear Compose MaterialTheme
import androidx.wear.compose.material.Scaffold // Correct Wear Compose Scaffold
import androidx.wear.compose.material.Slider // Correct Wear Compose Slider import
import androidx.wear.compose.material.Text // Correct Wear Compose Text
import androidx.wear.compose.material.TimeText // Correct Wear Compose TimeText
import kotlin.math.abs
import kotlin.math.sqrt

class PushupCounterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PushupCounterScreen() }
    }
}

@Composable
private fun PushupCounterScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    var hasPerms by remember { mutableStateOf(true) }
    var isTracking by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf(0) }

    // Sensitivity (g)
    var sensitivity by remember { mutableFloatStateOf(0.35f) }

    // Debug
    var lastAccelMag by remember { mutableFloatStateOf(0f) }
    var lastLinearZ by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res -> hasPerms = res.values.all { it } }

    LaunchedEffect(Unit) {
        val perms = arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS
        )
        val need = perms.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permissionLauncher.launch(perms)
    }

    // Sensor wiring
    DisposableEffect(isTracking, sensitivity) {
        if (isTracking) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            val gravity = FloatArray(3)
            var gravityInit = false
            var phaseDown = false
            var lastRepTs = 0L

            val downThresh = -sensitivity
            val upThresh = sensitivity * 0.55f
            val minRepMs = 350

            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                    if (!gravityInit) {
                        gravity[0] = e.values[0]; gravity[1] = e.values[1]; gravity[2] = e.values[2]
                        gravityInit = true; return
                    }
                    val alpha = 0.8f
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * e.values[0]
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * e.values[1]
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * e.values[2]

                    val linX = e.values[0] - gravity[0]
                    val linY = e.values[1] - gravity[1]
                    val linZ = e.values[2] - gravity[2]

                    val now = SystemClock.elapsedRealtime()
                    lastLinearZ = linZ
                    lastAccelMag = sqrt(linX * linX + linY * linY + linZ * linZ)

                    if (!phaseDown) {
                        if (linZ < downThresh) phaseDown = true
                    } else {
                        if (linZ > upThresh && (now - lastRepTs) > minRepMs) {
                            count += 1
                            lastRepTs = now
                            phaseDown = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        if (abs(linZ) < 0.05f && (now - lastRepTs) > 2000) phaseDown = false
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                // Unregister listener when effect leaves composition or isTracking becomes false
                sm.unregisterListener(listener)
            }
        } else {
            // No setup needed if not tracking
            onDispose {
                // No cleanup needed if listener was never registered
            }
        }
    }


    Scaffold(timeText = { TimeText() }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isTracking) "Push-ups" else "Ready",
                    style = MaterialTheme.typography.title2,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.display2,
                    color = Color.White
                )
                Spacer(Modifier.height(10.dp))

                Text(
                    "Sensitivity: ${"%.2f".format(sensitivity)}g",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))

                Slider( // Use the imported Slider
                    value = sensitivity,
                    onValueChange = { v: Float -> sensitivity = v },
                    valueRange = 0.20f..0.60f,
                    steps = 8
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (!isTracking) count = 0
                        isTracking = !isTracking
                    },
                    colors = ButtonDefaults.primaryButtonColors(),
                    enabled = hasPerms
                ) {
                    Icon(
                        imageVector = if (isTracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isTracking) "Stop" else "Start"
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "linZ=${"%.2f".format(lastLinearZ)}g · |a|=${"%.2f".format(lastAccelMag)}g",
                    style = MaterialTheme.typography.caption3,
                    color = Color(0xFF9AA0A6),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}