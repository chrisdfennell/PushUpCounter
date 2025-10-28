package com.example.pushupcounter.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Push-up detector using accelerometer:
 * - Estimates gravity via LPF
 * - Projects linear accel onto gravity axis (orientation-agnostic)
 * - Detects trough -> peak within time/size bounds as one rep
 */
class PushupDetector(
    private val onCountChanged: (Int) -> Unit,
    private val onLiveValues: (verticalG: Float) -> Unit = { _ -> }
) : SensorEventListener {

    data class Config(
        val gravityAlpha: Float = 0.1f,          // LPF for gravity
        val verticalEmaAlpha: Float = 0.2f,      // EMA for vertical accel
        val troughMinG: Float = -0.20f,          // downward threshold (g)
        val peakMinG: Float = 0.20f,             // upward threshold (g)
        val maxTroughToPeakMs: Long = 1200L,     // must see peak within this time
        val repDebounceMs: Long = 600L,          // min time between reps
        val sensitivityScale: Float = 1.0f       // multiply thresholds
    )

    private var cfg = Config()
    fun updateConfig(newCfg: Config) { cfg = newCfg }

    private var count = 0
    fun reset() {
        count = 0
        onCountChanged(count)
        lastRepAt = 0
        state = State.IDLE
        troughAt = 0
        troughVal = 0f
    }

    // Gravity estimate (LPF)
    private var gX = 0f
    private var gY = 0f
    private var gZ = 0f

    // Smoothed vertical accel
    private var vEma = 0f

    private enum class State { IDLE, SEEN_TROUGH }
    private var state = State.IDLE
    private var troughVal = 0f
    private var troughAt = 0L
    private var lastRepAt = 0L

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Low-pass gravity
        val a = cfg.gravityAlpha
        gX = (1 - a) * gX + a * e.values[0]
        gY = (1 - a) * gY + a * e.values[1]
        gZ = (1 - a) * gZ + a * e.values[2]

        val gNorm = max(1e-3f, sqrt(gX * gX + gY * gY + gZ * gZ))

        // Linear accel
        val lx = e.values[0] - gX
        val ly = e.values[1] - gY
        val lz = e.values[2] - gZ

        // Project onto gravity axis, convert to g
        val dot = (lx * gX + ly * gY + lz * gZ)
        val vertical = (dot / gNorm) / 9.81f

        // Smooth
        val b = cfg.verticalEmaAlpha
        vEma = (1 - b) * vEma + b * vertical

        onLiveValues(vEma)

        val now = System.currentTimeMillis()
        val troughMin = cfg.troughMinG * cfg.sensitivityScale
        val peakMin = cfg.peakMinG * cfg.sensitivityScale

        when (state) {
            State.IDLE -> {
                if (vEma < troughMin) {
                    troughVal = vEma
                    troughAt = now
                    state = State.SEEN_TROUGH
                }
            }
            State.SEEN_TROUGH -> {
                troughVal = min(troughVal, vEma)
                if (now - troughAt > cfg.maxTroughToPeakMs) {
                    state = State.IDLE
                } else if (vEma > peakMin) {
                    if (now - lastRepAt >= cfg.repDebounceMs) {
                        count++
                        onCountChanged(count)
                        lastRepAt = now
                    }
                    state = State.IDLE
                }
            }
        }
    }
}