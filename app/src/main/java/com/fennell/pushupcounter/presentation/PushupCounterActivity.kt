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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Slider
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
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
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

    // Sensitivity knob: higher = stricter (less sensitive)
    var sensitivity by remember { mutableFloatStateOf(0.55f) } // good middle ground

    // Debug
    var lastLinZ by remember { mutableFloatStateOf(0f) }
    var zSmooth by remember { mutableFloatStateOf(0f) }
    var zDelta by remember { mutableFloatStateOf(0f) }
    var lastAccelMag by remember { mutableFloatStateOf(0f) }

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

    // === SENSOR WIRED LOGIC ===
    DisposableEffect(isTracking, sensitivity) {
        if (isTracking) {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

            // Gravity removal (low-pass on gravity)
            val gravity = FloatArray(3)
            var gravityInit = false

            // Mild smoothing + adaptive baseline
            var smooth = 0f
            var baseline = 0f
            val smoothAlpha = 0.25f   // mild smoothing
            val baseAlpha = 0.02f     // very slow drift tracking

            // State machine
            var phaseDown = false
            var downStart = 0L
            var lastRepTs = 0L

            // Timing guards (medium)
            val minDownHoldMs = 120L
            val minRepMs = 650L
            val neutralTimeoutMs = 1500L

            // Thresholds (medium)
            // Down requires stronger negative; up slightly easier → hysteresis
            fun downThresh(): Float = -0.38f * sensitivity        // ~-0.21g at sens 0.55
            fun upThresh(): Float =  0.26f * sensitivity          // ~0.14g at sens 0.55
            val neutralBand = 0.07f                                // near-zero band for reset

            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

                    if (!gravityInit) {
                        gravity[0] = e.values[0]
                        gravity[1] = e.values[1]
                        gravity[2] = e.values[2]
                        gravityInit = true
                        return
                    }

                    // Update gravity (LPF)
                    val gAlpha = 0.8f
                    gravity[0] = gAlpha * gravity[0] + (1 - gAlpha) * e.values[0]
                    gravity[1] = gAlpha * gravity[1] + (1 - gAlpha) * e.values[1]
                    gravity[2] = gAlpha * gravity[2] + (1 - gAlpha) * e.values[2]

                    // Linear acceleration (remove gravity)
                    val linX = e.values[0] - gravity[0]
                    val linY = e.values[1] - gravity[1]
                    val linZ = e.values[2] - gravity[2]

                    // Mild smoothing & adaptive baseline → delta
                    smooth += smoothAlpha * (linZ - smooth)
                    baseline += baseAlpha * (smooth - baseline)
                    val delta = smooth - baseline

                    // Expose debug
                    lastLinZ = linZ
                    zSmooth = smooth
                    zDelta = delta
                    lastAccelMag = sqrt(linX * linX + linY * linY + linZ * linZ)

                    val now = SystemClock.elapsedRealtime()

                    if (!phaseDown) {
                        // Look for a clear "down" first
                        if (delta < downThresh()) {
                            phaseDown = true
                            downStart = now
                        }
                    } else {
                        // After down, require brief hold + "up" to count
                        val heldLongEnough = (now - downStart) >= minDownHoldMs
                        val cooledDown = (now - lastRepTs) >= minRepMs
                        if (heldLongEnough && cooledDown && delta > upThresh()) {
                            count += 1
                            lastRepTs = now
                            phaseDown = false
                            // ✅ use captured haptics from the composable, on main thread
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }

                        // If signal settles near neutral too long, reset phase
                        if (abs(delta) < neutralBand && (now - downStart) > neutralTimeoutMs) {
                            phaseDown = false
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }

            // Slightly quicker than UI but not as noisy as GAME
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)

            onDispose { sm.unregisterListener(listener) }
        } else {
            onDispose { /* no-op */ }
        }
    }

    // === UI ===
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
                    "Sensitivity: ${"%.2f".format(sensitivity)}",
                    style = MaterialTheme.typography.caption3,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))

                // Higher = stricter (less sensitive). Range set around the “medium” region.
                Slider(
                    value = sensitivity,
                    onValueChange = { v -> sensitivity = v },
                    valueRange = 0.40f..0.80f,
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
                    text = "z=${"%.2f".format(lastLinZ)}  sm=${"%.2f".format(zSmooth)}  d=${"%.2f".format(zDelta)}  |a|=${"%.2f".format(lastAccelMag)}",
                    style = MaterialTheme.typography.caption3,
                    color = Color(0xFF9AA0A6),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}