package com.parkinson.app.ui.caregiver

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.R
import com.parkinson.app.ui.components.LargeButton
import com.parkinson.app.ui.medication.MedicationViewModel
import com.parkinson.app.ui.report.PdfReportGenerator
import com.parkinson.app.ui.theme.ParkinsonBlue
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CaregiverScreen() {
    val app = LocalContext.current.applicationContext as ParkinsonApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: MedicationViewModel = viewModel()
    val intakes by vm.intakes.collectAsState()
    var symptomEvents by remember { mutableStateOf(emptyList<com.parkinson.app.domain.models.SymptomEpisode>()) }
    var exerciseEvents by remember { mutableStateOf(emptyList<androidx.health.connect.client.records.ExerciseSessionRecord>()) }
    LaunchedEffect(Unit) {
        scope.launch {
            val end = java.time.Instant.now()
            val start = end.minusSeconds(365L*24*3600)
            symptomEvents = app.healthConnect.readSymptoms(start, end)
            exerciseEvents = app.healthConnect.readExerciseSessions(start, end)
        }
    }
    var careStart by remember { mutableStateOf("") }
    var careEnd by remember { mutableStateOf("") }
    var showCareStartPicker by remember { mutableStateOf(false) }
    var showCareEndPicker by remember { mutableStateOf(false) }
    var editingSymptom by remember { mutableStateOf<com.parkinson.app.domain.models.SymptomEpisode?>(null) }
    var editingIntake by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.MedicationIntake?>(null) }
    var editIntakeDate by remember { mutableStateOf("") }
    var editIntakeHour by remember { mutableStateOf("0") }
    var editIntakeMinute by remember { mutableStateOf("0") }
    var editIntakeDose by remember { mutableStateOf("") }
    var showEditIntakeDatePicker by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf("") }
    var editHour by remember { mutableStateOf("0") }
    var editMinute by remember { mutableStateOf("0") }
    var editSymptomType by remember { mutableStateOf(com.parkinson.app.domain.models.SymptomType.ON) }
    var editMood by remember { mutableStateOf(3f) }
    var showEditDatePicker by remember { mutableStateOf(false) }
    val filteredIntakes = intakes.filter { intake ->
        val s = try { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(careStart)?.time } catch (_: Exception) { null } ?: Long.MIN_VALUE
        val e = try { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(careEnd)?.time } catch (_: Exception) { null }?.let { it + 86400000 } ?: Long.MAX_VALUE
        intake.intakeTimeMillis in s..e
    }

    data class PatientEvent(val time: Long, val label: String, val intake: com.parkinson.app.data.local.database.entities.MedicationIntake?, val symptom: com.parkinson.app.domain.models.SymptomEpisode?)
    val rangeStart = try { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(careStart)?.time } catch (_: Exception) { null } ?: Long.MIN_VALUE
    val rangeEnd = try { java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(careEnd)?.time } catch (_: Exception) { null }?.let { it + 86400000 } ?: Long.MAX_VALUE
    val patientEvents = (intakes.map { PatientEvent(it.intakeTimeMillis, "${it.medicationName} ${it.doseMg.toInt()} mg", it, null) } +
        symptomEvents.filter { it.type == com.parkinson.app.domain.models.SymptomType.ON || it.type == com.parkinson.app.domain.models.SymptomType.OFF || it.type == com.parkinson.app.domain.models.SymptomType.MOOD }.map { e ->
            PatientEvent(
                e.startTime,
                when (e.type) {
                    com.parkinson.app.domain.models.SymptomType.ON -> "Entrada en ON"
                    com.parkinson.app.domain.models.SymptomType.OFF -> "Entrada en OFF"
                    com.parkinson.app.domain.models.SymptomType.MOOD -> "Estado de animo ${e.severity.toInt()}"
                    else -> e.type.name
                },
                null,
                e
            )
        } + exerciseEvents.filter { com.parkinson.app.data.health.TremorParser.parseRecord(it) == null }.map { e ->
            PatientEvent(
                e.startTime.toEpochMilli(),
                "Ejercicio (Health Connect): ${(e.title?.takeIf { it.isNotBlank() } ?: "Deporte")} ${java.time.Duration.between(e.startTime, e.endTime).toMinutes()}m",
                null,
                null
            )
        } ).filter { it.time in rangeStart..rangeEnd }.sortedByDescending { it.time }

    val periods = listOf(7, 30, 90)
    var selectedDays by remember { mutableStateOf(30) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var generatedFile by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.informes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Caregiver read-only intakes
        Text(stringResource(R.string.ultimas_tomas_del_paciente), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = careStart,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.desde)) },
                trailingIcon = { TextButton(onClick = {
    val c = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(context, { _, y, m, d -> careStart = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}) { Icon(Icons.Filled.DateRange, contentDescription = "Fecha") } },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = careEnd,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.hasta)) },
                trailingIcon = { TextButton(onClick = {
    val c = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(context, { _, y, m, d -> careEnd = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}) { Icon(Icons.Filled.DateRange, contentDescription = "Fecha") } },
                modifier = Modifier.weight(1f)
            )
        }
        if (showCareStartPicker) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showCareStartPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            careStart = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                        }
                        showCareStartPicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showCareStartPicker = false }) { Text(stringResource(R.string.cancelar)) } }
            ) { DatePicker(state = pickerState) }
        }
        if (showCareEndPicker) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showCareEndPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            careEnd = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                        }
                        showCareEndPicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showCareEndPicker = false }) { Text(stringResource(R.string.cancelar)) } }
            ) { DatePicker(state = pickerState) }
        }
        if (showEditDatePicker) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showEditDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            editDate = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                        }
                        showEditDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showEditDatePicker = false }) { Text(stringResource(R.string.cancelar)) } }
            ) { DatePicker(state = pickerState) }
        }
        if (showEditIntakeDatePicker) {
            val pickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showEditIntakeDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                            editIntakeDate = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                        }
                        showEditIntakeDatePicker = false
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { showEditIntakeDatePicker = false }) { Text(stringResource(R.string.cancelar)) } }
            ) { DatePicker(state = pickerState) }
        }
        editingIntake?.let { intake ->
            AlertDialog(
                onDismissRequest = { editingIntake = null },
                title = { Text("Editar toma") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showEditIntakeDatePicker = true }) { Text(if (editIntakeDate.isBlank()) "Fecha" else editIntakeDate) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = editIntakeHour, onValueChange = { editIntakeHour = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Hora") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = editIntakeMinute, onValueChange = { editIntakeMinute = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Minuto") }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = editIntakeDose, onValueChange = { editIntakeDose = it }, label = { Text("Dosis mg") })
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parts = editIntakeDate.split("/")
                        val newTime = try {
                            val cal = java.util.Calendar.getInstance()
                            if (parts.size == 3) {
                                cal.set(java.util.Calendar.DAY_OF_MONTH, parts[0].toInt())
                                cal.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                                cal.set(java.util.Calendar.YEAR, parts[2].toInt())
                            } else {
                                cal.time = java.util.Date(intake.intakeTimeMillis)
                            }
                            cal.set(java.util.Calendar.HOUR_OF_DAY, editIntakeHour.toIntOrNull() ?: 0)
                            cal.set(java.util.Calendar.MINUTE, editIntakeMinute.toIntOrNull() ?: 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.timeInMillis
                        } catch (_: Exception) { intake.intakeTimeMillis }
                        val newDose = editIntakeDose.toFloatOrNull() ?: intake.doseMg
                        vm.updateIntake(intake.copy(intakeTimeMillis = newTime, doseMg = newDose))
                        editingIntake = null
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { editingIntake = null }) { Text(stringResource(R.string.cancelar)) } }
            )
        }

        editingSymptom?.let { symp ->
            AlertDialog(
                onDismissRequest = { editingSymptom = null },
                title = { Text("Editar evento") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(com.parkinson.app.domain.models.SymptomType.ON, com.parkinson.app.domain.models.SymptomType.OFF, com.parkinson.app.domain.models.SymptomType.MOOD).forEach { t ->
                                FilterChip(selected = editSymptomType == t, onClick = { editSymptomType = t }, label = { Text(if (t == com.parkinson.app.domain.models.SymptomType.ON) "ON" else if (t == com.parkinson.app.domain.models.SymptomType.OFF) "OFF" else "Animo") })
                            }
                        }
                        Button(onClick = { showEditDatePicker = true }) { Text(if (editDate.isBlank()) "Fecha" else editDate) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = editHour, onValueChange = { editHour = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Hora") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = editMinute, onValueChange = { editMinute = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Minuto") }, modifier = Modifier.weight(1f))
                        }
                        if (editSymptomType == com.parkinson.app.domain.models.SymptomType.MOOD) {
                            Text("Estado de animo: ${editMood.toInt()}")
                            Slider(value = editMood, onValueChange = { editMood = it }, valueRange = 1f..5f, steps = 3)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val parts = editDate.split("/")
                        val newStart = try {
                            val cal = java.util.Calendar.getInstance()
                            if (parts.size == 3) {
                                cal.set(java.util.Calendar.DAY_OF_MONTH, parts[0].toInt())
                                cal.set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                                cal.set(java.util.Calendar.YEAR, parts[2].toInt())
                            } else {
                                cal.time = java.util.Date(symp.startTime)
                            }
                            cal.set(java.util.Calendar.HOUR_OF_DAY, editHour.toIntOrNull() ?: 0)
                            cal.set(java.util.Calendar.MINUTE, editMinute.toIntOrNull() ?: 0)
                            cal.set(java.util.Calendar.SECOND, 0)
                            cal.timeInMillis
                        } catch (_: Exception) { symp.startTime }
                        val newSeverity = if (editSymptomType == com.parkinson.app.domain.models.SymptomType.MOOD) editMood else 1f
                        val newEp = com.parkinson.app.domain.models.SymptomEpisode(
                            id = symp.id,
                            type = editSymptomType,
                            severity = newSeverity,
                            startTime = newStart,
                            endTime = newStart + 60000,
                            notes = if (editSymptomType == com.parkinson.app.domain.models.SymptomType.MOOD) "mood=${editMood.toInt()}" else symp.notes
                        )
                        scope.launch {
                            val ok = app.healthConnect.updateSymptom(symp, newEp)
                            if (ok) {
                                val end = java.time.Instant.now()
                                val start = end.minusSeconds(365L*24*3600)
                                symptomEvents = app.healthConnect.readSymptoms(start, end)
                                exerciseEvents = app.healthConnect.readExerciseSessions(start, end)
                                editingSymptom = null
                            }
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { editingSymptom = null }) { Text(stringResource(R.string.cancelar)) } }
            )
        }

        patientEvents.take(10).forEach { ev ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ev.label, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ev.time)), style = MaterialTheme.typography.bodyMedium)
                    ev.intake?.let { intake ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            editingIntake = intake
                            val d = java.util.Date(intake.intakeTimeMillis)
                            editIntakeDate = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
                            val cal = java.util.Calendar.getInstance()
                            cal.time = d
                            editIntakeHour = cal.get(java.util.Calendar.HOUR_OF_DAY).toString()
                            editIntakeMinute = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
                            editIntakeDose = intake.doseMg.toString()
                        }) { Text("Editar") }
                        TextButton(onClick = { vm.deleteIntake(intake) }) { Text(stringResource(R.string.anular)) }
                        }
                    }
                    ev.symptom?.let { symp ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            editingSymptom = symp
                            editSymptomType = symp.type
                            val d = java.util.Date(symp.startTime)
                            editDate = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
                            val cal = java.util.Calendar.getInstance()
                            cal.time = d
                            editHour = cal.get(java.util.Calendar.HOUR_OF_DAY).toString()
                            editMinute = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
                            if (symp.type == com.parkinson.app.domain.models.SymptomType.MOOD) editMood = symp.severity
                        }) { Text("Editar") }
                        TextButton(onClick = {
                            scope.launch {
                                app.healthConnect.deleteSymptom(symp)
                                val end = java.time.Instant.now()
                                val start = end.minusSeconds(365L*24*3600)
                                symptomEvents = app.healthConnect.readSymptoms(start, end)
                                exerciseEvents = app.healthConnect.readExerciseSessions(start, end)
                            }
                        }) { Text(stringResource(R.string.anular)) }
                        }
                    }
                }
            }
        }
        if (patientEvents.isEmpty()) {
            Text(stringResource(R.string.sin_registros_de_tomas), style = MaterialTheme.typography.bodyLarge)
        }

        // Report generation merged
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.generar_informe), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.periodo), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            periods.forEach { d ->
                FilterChip(
                    selected = selectedDays == d,
                    onClick = { selectedDays = d },
                    label = { Text("$d ${stringResource(R.string.dias)}") }
                )
            }
        }
        LargeButton(
            text = if (generating) stringResource(R.string.generando) else "${stringResource(R.string.generar_informe_pdf)} (${selectedDays} ${stringResource(R.string.dias)})",
            onClick = {
                generating = true
                status = null
                generatedFile = null
                scope.launch {
                    try {
                        val f = PdfReportGenerator(app).generate(app.healthConnect, app.medicationRepository, selectedDays)
                        generatedFile = f
                        status = "PDF guardado en: ${f.absolutePath}"
                    } catch (e: Exception) {
                        status = "Error: ${e.message}"
                    } finally {
                        generating = false
                    }
                }
            },
            enabled = !generating
        )
        generatedFile?.let { f ->
            LargeButton(text = stringResource(R.string.compartir_pdf), onClick = {
                try {
                    val authority = "${app.packageName}.fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(context, authority, f)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.compartir_informe)))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, context.getString(R.string.no_se_pudo_compartir) + ": ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            })
        }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

