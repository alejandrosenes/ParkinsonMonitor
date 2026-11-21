package com.parkinson.app.ui.medication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.parkinson.app.R
import com.parkinson.app.ui.components.LargeButton
import com.parkinson.app.util.CustomMedStore
import com.parkinson.app.util.FavoriteMedStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen() {
    val vm: MedicationViewModel = viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schedules by vm.schedules.collectAsState()
    val intakes by vm.intakes.collectAsState()
    var showTakenEffect by remember { mutableStateOf(false) }
    var takenMedName by remember { mutableStateOf("") }

    fun markTakenWithEffect(name: String, dose: Float) {
        takenMedName = name
        showTakenEffect = true
        vm.markTaken(name, dose)
        scope.launch {
            kotlinx.coroutines.delay(2200)
            showTakenEffect = false
        }
    }

    var favorites by remember { mutableStateOf(FavoriteMedStore.getAll(context)) }
    var showAdd by remember { mutableStateOf(false) }
    var showAddMed by remember { mutableStateOf(false) }
    var customTick by remember { mutableStateOf(0) }
    var editingIntake by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.MedicationIntake?>(null) }
    var editingSchedule by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.MedicationSchedule?>(null) }
    var editingCustomMed by remember { mutableStateOf<com.parkinson.app.ui.medication.MedItem?>(null) }
    var adjustingMed by remember { mutableStateOf<com.parkinson.app.ui.medication.MedItem?>(null) }
    var pendingCustomMed by remember { mutableStateOf<com.parkinson.app.ui.medication.MedItem?>(null) }
    var pendingSchedule by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.MedicationSchedule?>(null) }
    var pendingIntake by remember { mutableStateOf<com.parkinson.app.data.local.database.entities.MedicationIntake?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var histStart by remember { mutableStateOf("") }
    var histEnd by remember { mutableStateOf("") }
    var showHistStartPicker by remember { mutableStateOf(false) }
    var showHistEndPicker by remember { mutableStateOf(false) }

    val customMeds = remember(customTick) { CustomMedStore.getAll(context) }
    val allMeds = remember(favorites, customTick) {
        val base = com.parkinson.app.ui.medication.ParkinsonMeds.list.map { com.parkinson.app.ui.medication.MedItem(it.name, it.doseMg) } +
            customMeds.map { com.parkinson.app.ui.medication.MedItem(it.name, it.doseMg) }
        base.sortedWith(compareByDescending<com.parkinson.app.ui.medication.MedItem> { favorites.contains(it.name) }.thenBy { it.name })
    }

    var name by remember { mutableStateOf("Levodopa") }
    var dose by remember { mutableStateOf("100") }
    var hour by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY).toString()) }
    var minute by remember { mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE).toString()) }
    var expandedMed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.medication_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) { Text(stringResource(R.string.medicacion), modifier = Modifier.padding(8.dp)) }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) { Text(stringResource(R.string.horarios), modifier = Modifier.padding(8.dp)) }
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) { Text(stringResource(R.string.historial), modifier = Modifier.padding(8.dp)) }
        }

        when (selectedTab) {
            0 -> {
                // Catalog + take now
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.parkinson_meds_list), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.marca_con_estrella_tus_favoritos_aparecen_primero), style = MaterialTheme.typography.bodyMedium)
                    allMeds.forEach { med ->
                        val isCustom = customMeds.any { it.name == med.name }
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        FavoriteMedStore.toggle(context, med.name)
                                        favorites = FavoriteMedStore.getAll(context)
                                    }) {
                                        Icon(
                                            if (favorites.contains(med.name)) Icons.Filled.Star else Icons.Outlined.Star,
                                            contentDescription = "Favorito",
                                            tint = if (favorites.contains(med.name)) Color(0xFFFFC107) else Color.Gray
                                        )
                                    }
                                    Text(med.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                }
                                Text("${med.doseMg.toInt()} mg (dosis habitual habitual del tratamiento)", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { markTakenWithEffect(med.name, med.doseMg) }) {
                                        Text(stringResource(R.string.take_now))
                                    }
                                    TextButton(onClick = { if (isCustom) editingCustomMed = med else adjustingMed = med }) {
                                        Text(stringResource(R.string.editar))
                                    }
                                    if (isCustom) {
                                        OutlinedButton(onClick = { pendingCustomMed = med }) { Text(stringResource(R.string.x)) }
                                    }
                                }
                            }
                        }
                    }
                    LargeButton(text = "Anadir medicamento propio", onClick = { showAddMed = true }, color = MaterialTheme.colorScheme.secondary)
                }
            }
            1 -> {
                // Schedules
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.horarios_programados), style = MaterialTheme.typography.titleLarge)
                    if (schedules.isEmpty()) {
                        Text(stringResource(R.string.no_hay_horarios_anade_uno), style = MaterialTheme.typography.bodyLarge)
                    }
                    schedules.forEach { s ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editingSchedule = s }) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("%02d:%02d".format(s.hour, s.minute), fontWeight = FontWeight.Bold)
                                    Text("${s.name} ${s.doseMg.toInt()}mg", fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { markTakenWithEffect(s.name, s.doseMg) }) { Text(stringResource(R.string.tomar)) }
                                    TextButton(onClick = { editingSchedule = s }) { Text(stringResource(R.string.editar)) }
                                    OutlinedButton(onClick = { pendingSchedule = s }) { Text(stringResource(R.string.x)) }
                                }
                            }
                        }
                    }
                    LargeButton(text = stringResource(R.string.add_schedule), onClick = {
                        val c = java.util.Calendar.getInstance()
                        hour = c.get(java.util.Calendar.HOUR_OF_DAY).toString()
                        minute = c.get(java.util.Calendar.MINUTE).toString()
                        showAdd = true
                    }, color = MaterialTheme.colorScheme.primary)
                }
            }
            2 -> {
                val filteredIntakes = intakes.filter { intake ->
                    val start = try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(histStart)?.time } catch (_: Exception) { null } ?: Long.MIN_VALUE
                    val end = try { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(histEnd)?.time } catch (_: Exception) { null }?.let { it + 86400000 } ?: Long.MAX_VALUE
                    intake.intakeTimeMillis in start..end
                }
                // History
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.historial_de_tomas), style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = histStart,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.desde)) },
                            trailingIcon = { TextButton(onClick = {
    val c = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(context, { _, y, m, d -> histStart = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}) { Icon(Icons.Filled.DateRange, contentDescription = "Fecha") } },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = histEnd,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.hasta)) },
                            trailingIcon = { TextButton(onClick = {
    val c = java.util.Calendar.getInstance()
    android.app.DatePickerDialog(context, { _, y, m, d -> histEnd = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
}) { Icon(Icons.Filled.DateRange, contentDescription = "Fecha") } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showHistStartPicker) {
                        val pickerState = rememberDatePickerState()
                        DatePickerDialog(
                            onDismissRequest = { showHistStartPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    pickerState.selectedDateMillis?.let { millis ->
                                        val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                                        histStart = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                                    }
                                    showHistStartPicker = false
                                }) { Text(stringResource(R.string.ok)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showHistStartPicker = false }) { Text(stringResource(R.string.cancelar)) }
                            }
                        ) { DatePicker(state = pickerState) }
                    }
                    if (showHistEndPicker) {
                        val pickerState = rememberDatePickerState()
                        DatePickerDialog(
                            onDismissRequest = { showHistEndPicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    pickerState.selectedDateMillis?.let { millis ->
                                        val ld = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.of("UTC")).toLocalDate()
                                        histEnd = "%02d/%02d/%04d".format(ld.dayOfMonth, ld.monthValue, ld.year)
                                    }
                                    showHistEndPicker = false
                                }) { Text(stringResource(R.string.ok)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showHistEndPicker = false }) { Text(stringResource(R.string.cancelar)) }
                            }
                        ) { DatePicker(state = pickerState) }
                    }
                    if (filteredIntakes.isEmpty()) {
                        Text(stringResource(R.string.sin_tomas_registradas), style = MaterialTheme.typography.bodyMedium)
                    }
                    filteredIntakes.take(30).forEach { intake ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${intake.medicationName} ${intake.doseMg.toInt()} mg", fontWeight = FontWeight.Bold)
                                    Text(SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(intake.intakeTimeMillis)), style = MaterialTheme.typography.bodyMedium)
                                }
                                TextButton(onClick = { editingIntake = intake }) { Text(stringResource(R.string.editar)) }
                                TextButton(onClick = { pendingIntake = intake }) { Text(stringResource(R.string.anular)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTakenEffect) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xCC1B5E20)
            ) {}
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("\u2713", style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Medicacion tomada", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(takenMedName, style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.nuevo_horario_de_medicacion)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(expanded = expandedMed, onExpandedChange = { expandedMed = !expandedMed }) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.medicamento)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMed) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expandedMed, onDismissRequest = { expandedMed = false }) {
                            allMeds.forEach { med ->
                                DropdownMenuItem(
                                    text = { Text("${med.name} - ${med.doseMg.toInt()} mg") },
                                    onClick = {
                                        name = med.name
                                        dose = med.doseMg.toInt().toString()
                                        expandedMed = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = hour, onValueChange = { hour = it }, label = { Text(stringResource(R.string.hora)) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = minute, onValueChange = { minute = it }, label = { Text(stringResource(R.string.minuto)) }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = dose.toFloatOrNull() ?: 100f
                    val h = hour.toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                    val m = minute.toIntOrNull() ?: java.util.Calendar.getInstance().get(java.util.Calendar.MINUTE)
                    if (name.isNotBlank()) {
                        vm.addSchedule(name, d, h, m)
                        android.widget.Toast.makeText(context, context.getString(R.string.horario_guardado), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.indica_medicamento), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    showAdd = false
                }) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    if (showAddMed) {
        var newMedName by remember { mutableStateOf("") }
        var newMedDose by remember { mutableStateOf("100") }
        AlertDialog(
            onDismissRequest = { showAddMed = false },
            title = { Text(stringResource(R.string.nuevo_medicamento_parkinson)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newMedName, onValueChange = { newMedName = it }, label = { Text(stringResource(R.string.nombre)) })
                    OutlinedTextField(value = newMedDose, onValueChange = { newMedDose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = newMedDose.toFloatOrNull() ?: 100f
                    if (newMedName.isNotBlank()) {
                        CustomMedStore.add(context, newMedName, d)
                        customTick++
                    }
                    showAddMed = false
                }) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddMed = false }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    editingIntake?.let { intake ->
        var eName by remember(intake.id) { mutableStateOf(intake.medicationName) }
        var eDose by remember(intake.id) { mutableStateOf(intake.doseMg.toInt().toString()) }
        var eHour by remember(intake.id) { mutableStateOf(java.util.Calendar.getInstance().apply { timeInMillis = intake.intakeTimeMillis }.get(java.util.Calendar.HOUR_OF_DAY).toString()) }
        var eMinute by remember(intake.id) { mutableStateOf(java.util.Calendar.getInstance().apply { timeInMillis = intake.intakeTimeMillis }.get(java.util.Calendar.MINUTE).toString()) }

        AlertDialog(
            onDismissRequest = { editingIntake = null },
            title = { Text(stringResource(R.string.editar_toma)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(stringResource(R.string.medicamento)) })
                    OutlinedTextField(value = eDose, onValueChange = { eDose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = eHour, onValueChange = { eHour = it }, label = { Text(stringResource(R.string.hora)) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = eMinute, onValueChange = { eMinute = it }, label = { Text(stringResource(R.string.minuto)) }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = intake.intakeTimeMillis }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, eHour.toIntOrNull() ?: cal.get(java.util.Calendar.HOUR_OF_DAY))
                    cal.set(java.util.Calendar.MINUTE, eMinute.toIntOrNull() ?: cal.get(java.util.Calendar.MINUTE))
                    cal.set(java.util.Calendar.SECOND, 0)
                    val updated = intake.copy(
                        medicationName = eName.ifBlank { intake.medicationName },
                        doseMg = eDose.toFloatOrNull() ?: intake.doseMg,
                        intakeTimeMillis = cal.timeInMillis
                    )
                    vm.updateIntake(updated)
                    editingIntake = null
                }) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { editingIntake = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    editingSchedule?.let { sch ->
        var eName by remember(sch.id) { mutableStateOf(sch.name) }
        var eDose by remember(sch.id) { mutableStateOf(sch.doseMg.toInt().toString()) }
        var eHour by remember(sch.id) { mutableStateOf(sch.hour.toString()) }
        var eMinute by remember(sch.id) { mutableStateOf(sch.minute.toString()) }
        AlertDialog(
            onDismissRequest = { editingSchedule = null },
            title = { Text(stringResource(R.string.editar_horario)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(stringResource(R.string.medicamento)) })
                    OutlinedTextField(value = eDose, onValueChange = { eDose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = eHour, onValueChange = { eHour = it }, label = { Text(stringResource(R.string.hora)) }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = eMinute, onValueChange = { eMinute = it }, label = { Text(stringResource(R.string.minuto)) }, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSchedule(
                        sch.copy(
                            name = eName.ifBlank { sch.name },
                            doseMg = eDose.toFloatOrNull() ?: sch.doseMg,
                            hour = eHour.toIntOrNull() ?: sch.hour,
                            minute = eMinute.toIntOrNull() ?: sch.minute
                        )
                    )
                    editingSchedule = null
                }) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { editingSchedule = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    editingCustomMed?.let { med ->
        var eName by remember(med.name) { mutableStateOf(med.name) }
        var eDose by remember(med.name) { mutableStateOf(med.doseMg.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { editingCustomMed = null },
            title = { Text(stringResource(R.string.editar_medicamento_propio)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = eName, onValueChange = { eName = it }, label = { Text(stringResource(R.string.nombre)) })
                    OutlinedTextField(value = eDose, onValueChange = { eDose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = eDose.toFloatOrNull() ?: med.doseMg
                    CustomMedStore.update(context, med.name, eName.ifBlank { med.name }, d)
                    customTick++
                    editingCustomMed = null
                }) { Text(stringResource(R.string.guardar)) }
            },
            dismissButton = {
                TextButton(onClick = { editingCustomMed = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    adjustingMed?.let { med ->
        var aDose by remember(med.name) { mutableStateOf(med.doseMg.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { adjustingMed = null },
            title = { Text(stringResource(R.string.ajustar_dosis) + " - ${med.name}") },
            text = {
                OutlinedTextField(value = aDose, onValueChange = { aDose = it }, label = { Text(stringResource(R.string.dosis_mg)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    val d = aDose.toFloatOrNull() ?: med.doseMg
                    markTakenWithEffect(med.name, d)
                    adjustingMed = null
                }) { Text(stringResource(R.string.tomar_con_esta_dosis)) }
            },
            dismissButton = {
                TextButton(onClick = { adjustingMed = null }) { Text(stringResource(R.string.cancelar)) }
            }
        )
    }

    pendingCustomMed?.let { med ->
        AlertDialog(
            onDismissRequest = { pendingCustomMed = null },
            title = { Text("Eliminar medicamento") },
            text = { Text("¿Estás seguro de eliminar ${med.name}?") },
            confirmButton = {
                TextButton(onClick = {
                    CustomMedStore.remove(context, med.name)
                    customTick++
                    pendingCustomMed = null
                }) { Text("Sí") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCustomMed = null }) { Text("No") }
            }
        )
    }

    pendingSchedule?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingSchedule = null },
            title = { Text("Eliminar horario") },
            text = { Text("¿Estás seguro de eliminar este horario?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeSchedule(s.id)
                    pendingSchedule = null
                }) { Text("Sí") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSchedule = null }) { Text("No") }
            }
        )
    }

    pendingIntake?.let { intake ->
        AlertDialog(
            onDismissRequest = { pendingIntake = null },
            title = { Text("Anular toma") },
            text = { Text("¿Estás seguro de anular esta toma?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteIntake(intake)
                    pendingIntake = null
                }) { Text("Sí") }
            },
            dismissButton = {
                TextButton(onClick = { pendingIntake = null }) { Text("No") }
            }
        )
    }

    }
}

