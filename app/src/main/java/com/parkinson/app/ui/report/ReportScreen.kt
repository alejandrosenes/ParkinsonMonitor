package com.parkinson.app.ui.report

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.parkinson.app.ParkinsonApp
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
    var generating by remember { mutableStateOf(false) }
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

        LargeButton(
            text = if (generating) stringResource(R.string.generando) else "${stringResource(R.string.generar_informe_pdf)} (${selectedDays} ${stringResource(R.string.dias)})",
            onClick = {
                generating = true
                status = null
                generatedFile = null
                scope.launch {
                    try {
                        val f = PdfReportGenerator(app).generate(app.healthConnect, app.medicationRepository, app.tremorDao, selectedDays)
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
                LargeButton(text = "Ver informe", onClick = {
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
                LargeButton(text = stringResource(R.string.compartir_pdf), onClick = {
                    val authority = "${app.packageName}.fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(context, authority, f)
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(share, context.getString(R.string.compartir_informe)))
                })
            }
            LargeButton(text = "Analizar con Parkibot", onClick = {
                scope.launch {
                    try {
                        val tremor = app.tremorDao.observeAll().first()
                        val intakes = app.medicationRepository.getAllIntakes()
                        val sb = StringBuilder()
                        sb.append("Informe Parkinson Monitor (${selectedDays} dias)\n")
                        sb.append("Tomas de medicacion: ${intakes.size}\n")
                        tremor.take(20).forEach { t ->
                            sb.append("Temblor ${t.mode}: score=${"%.0f".format(t.score)}, freq=${"%.2f".format(t.peakFreqHz)} Hz, fecha=${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t.timestamp))}\n")
                        }
                        sb.append("\nAnaliza este informe para optimizar la toma de medicamentos y horarios, cruzando datos de sueno y ejercicio y buscando correspondencias con las tomas de medicamentos.")
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Parkinson report", sb.toString()))
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://parkibot.com/"))
                        context.startActivity(intent)
                        android.widget.Toast.makeText(context, "Resumen copiado. Pegalo en Parkibot para analizar.", android.widget.Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "No se pudo abrir Parkibot: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            })
        }

        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
