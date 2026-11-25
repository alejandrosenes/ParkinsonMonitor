package com.parkinson.app.ui.report

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.util.AppSettings
import com.parkinson.app.R
import com.parkinson.app.ui.components.LargeButton
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
fun ReportScreen() {
    val app = LocalContext.current.applicationContext as ParkinsonApp
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val periods = listOf(7, 30, 90)
    var selectedDays by remember { mutableIntStateOf(30) }
    var reportStart by remember { mutableStateOf("") }
    var reportEnd by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var includeMetrics by remember { mutableStateOf(true) }
    var includeDaily by remember { mutableStateOf(true) }
    var includeIntakes by remember { mutableStateOf(true) }
    var includeEvents by remember { mutableStateOf(true) }
    var includeSports by remember { mutableStateOf(true) }
    var includeMixed by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var generatedFile by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.informes_medicos), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.genera_un_pdf_estructurado_y_compartelo_con_el_neurologo), style = MaterialTheme.typography.bodyLarge)

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

        Text("Secciones del informe", style = MaterialTheme.typography.titleMedium)
        Column {
            Row { Checkbox(checked = includeMetrics, onCheckedChange = { includeMetrics = it }); Text("Metrica") }
            Row { Checkbox(checked = includeDaily, onCheckedChange = { includeDaily = it }); Text("Metricas diarias de Health Connect") }
            Row { Checkbox(checked = includeIntakes, onCheckedChange = { includeIntakes = it }); Text("Detalle de tomas") }
            Row { Checkbox(checked = includeEvents, onCheckedChange = { includeEvents = it }); Text("Eventos ON/OFF y estado de animo") }
            Row { Checkbox(checked = includeSports, onCheckedChange = { includeSports = it }); Text("Actividades deportivas") }
            Row { Checkbox(checked = includeMixed, onCheckedChange = { includeMixed = it }); Text("Informe mixto") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = reportStart,
                onValueChange = {},
                readOnly = true,
                label = { Text("Desde") },
                trailingIcon = { TextButton(onClick = {
                    val c = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(context, { _, y, m, d -> reportStart = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }) { Icon(androidx.compose.material.icons.Icons.Filled.DateRange, contentDescription = "Fecha") } },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = reportEnd,
                onValueChange = {},
                readOnly = true,
                label = { Text("Hasta") },
                trailingIcon = { TextButton(onClick = {
                    val c = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(context, { _, y, m, d -> reportEnd = "%02d/%02d/%04d".format(d, m + 1, y) }, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH)).show()
                }) { Icon(androidx.compose.material.icons.Icons.Filled.DateRange, contentDescription = "Fecha") } },
                modifier = Modifier.weight(1f)
            )
        }

        LargeButton(
            text = if (generating) stringResource(R.string.generando) else "${stringResource(R.string.generar_informe_pdf)} (${selectedDays} ${stringResource(R.string.dias)})",
            onClick = {
                generating = true
                status = null
                generatedFile = null
                scope.launch {
                    try {
                        val startMs = if (reportStart.isNotBlank()) try {
                            val p = reportStart.split("/")
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.DAY_OF_MONTH, p[0].toInt())
                            cal.set(java.util.Calendar.MONTH, p[1].toInt() - 1)
                            cal.set(java.util.Calendar.YEAR, p[2].toInt())
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0); cal.set(java.util.Calendar.SECOND, 0)
                            cal.timeInMillis
                        } catch (_: Exception) { null } else null
                        val endMs = if (reportEnd.isNotBlank()) try {
                            val p = reportEnd.split("/")
                            val cal = java.util.Calendar.getInstance()
                            cal.set(java.util.Calendar.DAY_OF_MONTH, p[0].toInt())
                            cal.set(java.util.Calendar.MONTH, p[1].toInt() - 1)
                            cal.set(java.util.Calendar.YEAR, p[2].toInt())
                            cal.set(java.util.Calendar.HOUR_OF_DAY, 23); cal.set(java.util.Calendar.MINUTE, 59); cal.set(java.util.Calendar.SECOND, 59)
                            cal.timeInMillis
                        } catch (_: Exception) { null } else null
                        val f = PdfReportGenerator(app).generate(app.healthConnect, app.medicationRepository, app.tremorDao, selectedDays, startMs, endMs, includeMetrics, includeDaily, includeIntakes, includeEvents, includeSports, includeMixed, AppSettings.patientName.value)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LargeButton(text = "Abrir informe", onClick = {
                    try {
                        val authority = "${app.packageName}.fileprovider"
                        val uri: Uri = FileProvider.getUriForFile(context, authority, f)
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(view, "Ver informe"))
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No se pudo abrir: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                })
            }

        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
