package com.parkinson.app.ui.tremor

import android.content.Context
import com.parkinson.app.ParkinsonApp
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.*

data class TremorResult(
    val peakFreqHz: Double,
    val tremorPower: Double,
    val rmsDisplacementMm: Double,
    val score: Double
)

@Composable
fun TremorScreen() {
    val context = LocalContext.current
    val app = LocalContext.current.applicationContext as ParkinsonApp
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accel = remember { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val gyro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(emptyList<com.parkinson.app.data.local.database.entities.TremorResultEntity>()) }
    var selectedHistory by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.TremorResultEntity?>(null) }
    LaunchedEffect(Unit) {
        scope.launch { history = try { app.tremorDao.observeAll().first() } catch (_: Exception) { emptyList() } }
    }

    var mode by remember { mutableStateOf("rest") } // rest | postural | spiral
    var isRecording by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val accelSamples = remember { ArrayList<Float>() }
    val gyroSamples = remember { ArrayList<Float>() }
    var liveTremor by remember { mutableStateOf(0f) }
    var sampleCounter by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<TremorResult?>(null) }
    var message by remember { mutableStateOf("") }

    val listener = remember {
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val norm = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    accelSamples.add(norm)
                    sampleCounter++
                    if (sampleCounter % 5 == 0) liveTremor = kotlin.math.abs(norm - 9.81f)
                } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) gyroSamples.add(norm)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    fun stopSensors() {
        sensorManager.unregisterListener(listener)
    }

    fun computeSpectrum(samples: List<Float>): Pair<Double, Double> {
        if (samples.size < 64) return 0.0 to 0.0
        val n = samples.size
        val fs = 100.0
        val mean = samples.average()
        // Detrend
        val detrended = DoubleArray(n) { samples[it] - mean }
        // FFT (real, power of two)
        val size = 1
        var N = 1
        while (N < n) N *= 2
        val re = DoubleArray(N)
        val im = DoubleArray(N)
        for (i in 0 until n) re[i] = detrended[i]
        // Hann window
        for (i in 0 until n) {
            val w = 0.5 * (1 - cos(2 * PI * i / (n - 1)))
            re[i] *= w
        }
        fft(re, im)
        val df = fs / N
        var peakFreq = 0.0
        var peakPow = 0.0
        var tremorPower = 0.0
        var rmsDisp = 0.0
        for (k in 1 until N / 2) {
            val f = k * df
            val mag = sqrt(re[k] * re[k] + im[k] * im[k]) / N
            // One-sided bin power (Parseval): 2*|X|^2/N^2
            val binPower = 2.0 * mag * mag
            if (f in 3.0..12.0) {
                if (binPower > peakPow) { peakPow = binPower; peakFreq = f }
                tremorPower += binPower
            }
            if (f in 3.0..15.0) {
                val denom = (2 * PI * f)
                if (denom > 0) {
                    val amp = sqrt(binPower)
                    val term = amp / (denom * denom)
                    rmsDisp += term * term
                }
            }
        }
        rmsDisp = sqrt(rmsDisp)
        return peakFreq to tremorPower
    }

    // Spiral state
    val userPoints = remember { mutableStateListOf<Offset>() }
    var spiralRmse by remember { mutableStateOf(0.0) }

    fun scoreFromLogMetric(value: Double, alpha: Double = 1.0, mu: Double = 3.0): Double {
        val v = value.coerceAtLeast(1e-12)
        val logv = ln(v) / ln(10.0)
        val denom = 1.0 + exp(alpha * (logv - mu))
        return 100.0 / denom
    }

    val duration = 30
    fun startRecording() {
        accelSamples.clear()
        gyroSamples.clear()
        result = null
        message = ""
        liveTremor = 0f
        sampleCounter = 0
        isRecording = true
        secondsLeft = duration
        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_FASTEST)
        if (mode == "postural" && gyro != null) {
            sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_FASTEST)
        }
        scope.launch {
            for (i in duration downTo 1) {
                secondsLeft = i
                delay(1000)
            }
            secondsLeft = 0
            stopSensors()
            isRecording = false
            // compute
            val (peakFreq, tremorPower) = computeSpectrum(accelSamples.toList())
            val rms = if (mode == "postural") {
                // simplified: use gyro norm average
                0.0
            } else 0.0
            val score = scoreFromLogMetric(tremorPower, 2.0, -2.0).coerceIn(0.0, 100.0)
            result = TremorResult(peakFreq, tremorPower, rms, (100.0 - score).coerceIn(0.0, 100.0))
            message = "Completado"
            // Guardar automaticamente en historico
            scope.launch {
                try {
                    app.tremorDao.insert(
                        com.parkinson.app.data.local.database.entities.TremorResultEntity(
                            mode = mode,
                            score = result!!.score,
                            peakFreqHz = result!!.peakFreqHz,
                            tremorPower = result!!.tremorPower,
                            rmsDisplacementMm = result!!.rmsDisplacementMm,
                            samplesCsv = accelSamples.joinToString(",")
                        )
                    )
                    history = app.tremorDao.observeAll().first()
                    message = "Guardado en historico"
                } catch (_: Exception) { message = "No se pudo guardar" }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Evaluacion de temblor", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Selecciona la prueba:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "rest", onClick = { mode = "rest" }, label = { Text("Reposo") })
            FilterChip(selected = mode == "postural", onClick = { mode = "postural" }, label = { Text("Postural") })
            FilterChip(selected = mode == "spiral", onClick = { mode = "spiral" }, label = { Text("Espiral") })
        }

        if (mode == "rest") {
            Text("Apoya tu mano sobre una superficie plana sosteniendo el dispositivo de forma relajada.")
        } else if (mode == "postural") {
            Text("Extiende tu brazo frente a ti y manten el dispositivo en el aire.")
        } else {
            Text("Traza con tu dedo la espiral que aparece en pantalla lo mas preciso que puedas.")
        }

         if (mode == "spiral") {
             BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).heightIn(max = 300.dp)) {
                 val w = constraints.maxWidth.toFloat()
                 val h = constraints.maxHeight.toFloat()
                 val targetPoints = remember(w, h) {
                     val cx = w / 2f
                     val cy = h / 2f
                     val maxR = min(w, h) / 2f - 24f
                     mutableStateListOf<Offset>().apply {
                         for (i in 0..720) {
                             val t = i / 720f
                             val theta = t * 4f * kotlin.math.PI.toFloat()
                             val rho = t * maxR
                             add(Offset(cx + rho * cos(theta), cy + rho * sin(theta)))
                         }
                     }
                 }
                 Canvas(
                     modifier = Modifier
                         .fillMaxSize()
                         .pointerInput(Unit) {
                             detectDragGestures(
                                 onDragStart = { offset -> userPoints.add(offset) },
                                 onDrag = { change, _ -> userPoints.add(change.position) },
                                 onDragEnd = {}
                             )
                         }
                 ) {
                     val path = Path()
                     if (targetPoints.isNotEmpty()) {
                         path.moveTo(targetPoints[0].x, targetPoints[0].y)
                         for (pp in targetPoints.drop(1)) path.lineTo(pp.x, pp.y)
                     }
                     drawPath(path, color = Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f))
                     val userPath = Path()
                     if (userPoints.isNotEmpty()) {
                         userPath.moveTo(userPoints[0].x, userPoints[0].y)
                         for (p in userPoints.drop(1)) userPath.lineTo(p.x, p.y)
                     }
                     drawPath(userPath, color = Color.Red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f))
                     for (p in userPoints) {
                         drawCircle(color = Color.Red, radius = 6f, center = p)
                     }
                 }
                 Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(onClick = {
                         if (userPoints.isNotEmpty() && targetPoints.isNotEmpty()) {
                             var sum = 0.0
                             for (p in userPoints) {
                                 var minD = Double.MAX_VALUE
                                 for (t in targetPoints) {
                                     val dx = (p.x - t.x).toDouble()
                                     val dy = (p.y - t.y).toDouble()
                                     val d = sqrt(dx * dx + dy * dy)
                                     if (d < minD) minD = d
                                 }
                                 sum += minD * minD
                             }
                             val rmse = sqrt(sum / userPoints.size)
                             spiralRmse = rmse
                             val score = scoreFromLogMetric(rmse.coerceAtLeast(1e-6), 2.0, 2.0).coerceIn(0.0, 100.0)
                             result = TremorResult(0.0, 0.0, rmse, (100.0 - score).coerceIn(0.0, 100.0))
                             message = "RMSE: ${"%.1f".format(rmse)}"
                             scope.launch {
                                 try {
                                     app.tremorDao.insert(
                                         com.parkinson.app.data.local.database.entities.TremorResultEntity(
                                             mode = mode,
                                             score = result!!.score,
                                             rmsDisplacementMm = result!!.rmsDisplacementMm,
                                             samplesCsv = null
                                         )
                                     )
                                     history = app.tremorDao.observeAll().first()
                                 } catch (_: Exception) { }
                             }
                         }
                     }) { Text("Calcular") }
                     Button(onClick = { userPoints.clear(); spiralRmse = 0.0; result = null; message = "" }) { Text("Limpiar") }
                 }
             }
         } else {
            if (!isRecording) {
                Button(onClick = { startRecording() }) { Text("Iniciar prueba (30s)") }
            } else {
                Text("Grabando... $secondsLeft s", fontWeight = FontWeight.Bold)
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(size.width, size.height) / 2f - 10f
                    drawCircle(color = Color.LightGray, radius = r, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                    val amp = (liveTremor * 40f).coerceIn(0f, r - 18f)
                    val angle = (System.currentTimeMillis() / 40.0) % (2 * PI)
                    val bx = cx + amp * cos(angle).toFloat()
                    val by = cy + amp * sin(angle).toFloat()
                    drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White, Color.Red, Color(0xFF8B0000)), center = Offset(bx, by), radius = 16f), radius = 16f, center = Offset(bx, by))
                    drawCircle(color = Color.Black.copy(alpha = 0.25f), radius = 16f, center = Offset(bx + 4f, by + 4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
            }
        }

        result?.let { r ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (mode != "spiral") {
                        Text("Frecuencia pico: ${"%.2f".format(r.peakFreqHz)} Hz")
                        Text("Potencia temblor: ${"%.4f".format(r.tremorPower)}")
                        if (accelSamples.size > 2) {
                            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                                val maxA = accelSamples.maxOrNull() ?: 1f
                                val minA = accelSamples.minOrNull() ?: 0f
                                val range = (maxA - minA).coerceAtLeast(0.1f)
                                val path = Path()
                                val stepX = size.width / (accelSamples.size - 1)
                                accelSamples.forEachIndexed { i, a ->
                                    val x = i * stepX
                                    val y = size.height - ((a - minA) / range) * size.height * 0.9f - size.height * 0.05f
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color.Blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                            }
                        }
                    }
                    Text("Puntuacion (0-100): ${"%.0f".format(r.score)}", fontWeight = FontWeight.Bold)
                    message.takeIf { it.isNotBlank() }?.let { Text(it) }
                    Text("Resultado guardado automaticamente en el historico", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    Text("Historico de pruebas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    history.take(15).forEach { item ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${item.mode.uppercase()} - ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp))}")
                Text("Puntuacion (0-100, severo): ${"%.0f".format(item.score)}", fontWeight = FontWeight.Bold)
                if (item.mode != "spiral") {
                    TextButton(onClick = { selectedHistory = item }) { Text("Ver grafica") }
                }
            }
        }
    }
    selectedHistory?.let { item ->
        item.samplesCsv?.let { csv ->
            val values = csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (values.isNotEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Grafica: ${item.mode} ${"%.0f".format(item.score)}", fontWeight = FontWeight.Bold)
                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                            val maxA = values.maxOrNull() ?: 1f
                            val minA = values.minOrNull() ?: 0f
                            val range = (maxA - minA).coerceAtLeast(0.1f)
                            val path = Path()
                            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
                            values.forEachIndexed { i, a ->
                                val x = i * stepX
                                val y = size.height - ((a - minA) / range) * size.height * 0.9f - size.height * 0.05f
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color = Color.Blue, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        }
                        Button(onClick = { selectedHistory = null }) { Text("Cerrar") }
                    }
                }
            }
        }
    }
    if (history.isEmpty()) {
        Text("Sin historico guardado", style = MaterialTheme.typography.bodyMedium)
    }

    }
}

// Simple iterative FFT (Cooley-Tukey) for real input arrays.
private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    // bit reversal
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j or bit
        if (i < j) {
            val tr = re[i]; re[i] = re[j]; re[j] = tr
            val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2.0 * PI / len
        val wlenRe = cos(ang)
        val wlenIm = sin(ang)
        var i = 0
        while (i < n) {
            var wRe = 1.0
            var wIm = 0.0
            for (k in 0 until len / 2) {
                val uRe = re[i + k]
                val uIm = im[i + k]
                val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                re[i + k] = uRe + vRe
                im[i + k] = uIm + vIm
                re[i + k + len / 2] = uRe - vRe
                im[i + k + len / 2] = uIm - vIm
                val nwRe = wRe * wlenRe - wIm * wlenIm
                val nwIm = wRe * wlenIm + wIm * wlenRe
                wRe = nwRe
                wIm = nwIm
            }
            i += len
        }
        len = len shl 1
    }
}
