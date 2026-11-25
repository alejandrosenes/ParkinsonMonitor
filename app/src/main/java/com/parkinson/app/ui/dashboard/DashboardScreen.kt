package com.parkinson.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.parkinson.app.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parkinson.app.ui.components.LargeButton
import com.parkinson.app.ui.theme.ParkinsonGreen
import com.parkinson.app.ui.theme.ParkinsonRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onOpenMedication: () -> Unit
) {
    val vm: DashboardViewModel = viewModel()
    val state by vm.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshNow()
                kotlinx.coroutines.delay(10000)
            }
        }
    }
    val lastMotorOn = LocalContext.current.getSharedPreferences("parkinson_motor", android.content.Context.MODE_PRIVATE).getBoolean("last_on", false)
    var motorOn by remember { mutableStateOf(lastMotorOn) }
    val onColor = MaterialTheme.colorScheme.primary
    val offColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var doseStruck by remember { mutableStateOf(false) }
    var showMoodEffect by remember { mutableStateOf(false) }
    var obsText by remember { mutableStateOf("") }
    var obsDate by remember { mutableStateOf("") }
    var struckDoseText by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        // ON/OFF circular summary
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(220.dp).clickable {
            val newOn = !motorOn
            motorOn = newOn
            vm.recordMotorState(newOn)
        }) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val stroke = 36f
                val offSweep = if (motorOn) 0f else 360f
                val onSweep = if (motorOn) 360f else 0f
                // OFF arc
                drawArc(
                    color = offColor,
                    startAngle = -90f,
                    sweepAngle = offSweep,
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
                // ON arc
                drawArc(
                    color = onColor,
                    startAngle = -90f + offSweep,
                    sweepAngle = onSweep,
                    useCenter = false,
                    style = Stroke(width = stroke)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (motorOn) "ON" else "OFF", color = if (motorOn) onColor else offColor, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text(if (motorOn) "Pulsa para marcar OFF" else "Pulsa para marcar ON", fontSize = 12.sp)
            }
        }

        // Next dose card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.pr_xima_dosis), style = MaterialTheme.typography.titleLarge)
                val next = state.nextDoseMillis
                val doseText = if (next != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(next)) + "  ${state.nextDoseName ?: ""}" else stringResource(R.string.sin_programar)
                if (doseStruck && struckDoseText != null) {
                    Text(
                        struckDoseText!!,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = TextDecoration.LineThrough,
                        color = Color.Gray
                    )
                }
                Text(
                    doseText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Unspecified
                )
                Text(stringResource(R.string.horarios_cargados) + ": ${state.schedules.size}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LargeButton(text = stringResource(R.string.marcar_como_tomada), onClick = {
                    struckDoseText = doseText
                    doseStruck = true
                    vm.markDoseTaken()
                    scope.launch {
                        kotlinx.coroutines.delay(1500)
                        doseStruck = false
                        struckDoseText = null
                    }
                }, enabled = next != null)
            }
        }

        // Google Health synced items
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.items_sincronizados_con_google_health), style = MaterialTheme.typography.titleLarge)
                Text(state.healthSummary.ifBlank { "Sin datos de Health Connect" }, style = MaterialTheme.typography.bodyMedium)
            }
        }




        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.estado_animo_titulo), style = MaterialTheme.typography.titleLarge)
                var selectedMood by remember { mutableStateOf(3) }
                val selectedEmoji = when (selectedMood) {
                    1 -> "\uD83D\uDE22"; 2 -> "\uD83D\uDE41"; 3 -> "\uD83D\uDE10"; 4 -> "\uD83D\uDE42"; else -> "\uD83D\uDE04"
                }
                Text(selectedEmoji, fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Slider(
                    value = selectedMood.toFloat(),
                    onValueChange = { selectedMood = Math.round(it).coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 \uD83D\uDE22")
                    Text("2 \uD83D\uDE41")
                    Text("3 \uD83D\uDE10")
                    Text("4 \uD83D\uDE42")
                    Text("5 \uD83D\uDE04")
                }
                Button(onClick = {
                    vm.recordMood(selectedMood)
                    showMoodEffect = true
                    scope.launch {
                        kotlinx.coroutines.delay(2200)
                        showMoodEffect = false
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.registrar_estado_animo) + " ($selectedMood)")
                }
            }
        }


        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Observaciones", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = obsText,
                    onValueChange = { obsText = it },
                    label = { Text("Observacion") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val c = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(ctx, { _, y, m, d -> obsDate = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }) { Text(if (obsDate.isBlank()) "Fecha" else obsDate) }
                Button(onClick = {
                    val atMillis = try {
                        val parts = obsDate.split("/")
                        val cal = java.util.Calendar.getInstance()
                        if (parts.size == 3) {
                            cal.set(java.util.Calendar.DAY_OF_MONTH, parts[0].toInt())
                            cal.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                            cal.set(java.util.Calendar.YEAR, parts[2].toInt())
                        }
                        cal.set(java.util.Calendar.HOUR_OF_DAY, java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
                        cal.set(java.util.Calendar.MINUTE, java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE))
                        cal.set(java.util.Calendar.SECOND, 0)
                        cal.timeInMillis
                    } catch (_: Exception) { System.currentTimeMillis() }
                    scope.launch {
                        val app = ctx.applicationContext as com.parkinson.app.ParkinsonApp
                        app.healthConnect.writeSymptom(
                            com.parkinson.app.domain.models.SymptomEpisode(
                                type = com.parkinson.app.domain.models.SymptomType.OBSERVATION,
                                severity = 0f,
                                startTime = atMillis,
                                endTime = atMillis + 60 * 1000L,
                                source = "dashboard_observation",
                                notes = obsText
                            )
                        )
                        showMoodEffect = true
                        kotlinx.coroutines.delay(2200)
                        showMoodEffect = false
                        obsText = ""
                        obsDate = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Registrar observacion")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

    }
    if (showMoodEffect) {
        Box(
            Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(Modifier.fillMaxSize(), color = Color(0xCC1B5E20)) {}
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("\u2713", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Estado de animo registrado", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
}
