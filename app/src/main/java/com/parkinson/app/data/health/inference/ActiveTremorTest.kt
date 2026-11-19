package com.parkinson.app.data.health.inference

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.parkinson.app.util.Fft
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Active tremor test: user holds the phone for ~10 seconds while the
 * accelerometer is sampled. The magnitude signal is run through an FFT and the
 * 3.5 Hz - 7.5 Hz band is isolated to confirm tremor and estimate severity.
 */
class ActiveTremorTest(private val context: Context) {

    data class Result(
        val dominantFrequencyHz: Double,
        val severity: Float, // 0..1
        val isTremor: Boolean
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun collect(durationMillis: Long = 10_000L, sampleRateHz: Int = 50): Flow<DoubleArray?> {
        return callbackFlow {
            if (accelerometer == null) {
                trySend(null)
                close()
                return@callbackFlow
            }
            val samples = ArrayList<Double>(sampleRateHz * (durationMillis / 1000).toInt() + 64)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val mag = sqrt(
                        event.values[0] * event.values[0] +
                            event.values[1] * event.values[1] +
                            event.values[2] * event.values[2]
                    )
                    samples.add(mag.toDouble())
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            kotlinx.coroutines.delay(durationMillis)
            sensorManager.unregisterListener(listener)
            trySend(samples.toDoubleArray())
            close()
            awaitClose { sensorManager.unregisterListener(listener) }
        }
    }

    fun analyze(samples: DoubleArray, sampleRate: Double = 50.0): Result {
        val (freq, sev) = Fft.dominantFrequency(samples, sampleRate, 3.5, 7.5)
        val isTremor = freq in 3.5..7.5 && sev >= 0.15
        return Result(freq, sev.toFloat(), isTremor)
    }
}
