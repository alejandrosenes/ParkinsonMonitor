package com.parkinson.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import com.parkinson.app.R
import com.parkinson.app.ParkinsonApp
import com.parkinson.app.ui.components.LargeButton
import com.parkinson.app.ui.report.PdfReportGenerator
import com.parkinson.app.util.AppSettings
import com.parkinson.app.util.BackupManager
import com.parkinson.app.util.CustomMedStore
import com.parkinson.app.util.FavoriteMedStore
import com.parkinson.app.util.MedicationAlarmScheduler
import com.parkinson.app.util.LanguageManager
import java.io.File

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf(LanguageManager.get(context)) }
    var selectedPalette by remember { mutableStateOf(AppSettings.colorPalette.value) }
    var selectedScale by remember { mutableStateOf(AppSettings.textScale.value) }
    val app = context.applicationContext as ParkinsonApp
    val scope = rememberCoroutineScope()
    var healthAvailable by remember { mutableStateOf(false) }
    var healthGranted by remember { mutableStateOf(false) }
    var healthMessage by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        healthAvailable = app.healthConnect.isAvailable()
        healthGranted = if (healthAvailable) {
            try { app.healthConnect.hasAllPermissions() } catch (_: Exception) { false }
        } else false
        healthMessage = when {
            !healthAvailable -> context.getString(R.string.health_settings_unavailable)
            healthGranted -> context.getString(R.string.health_settings_granted)
            else -> context.getString(R.string.health_settings_missing)
        }
    }
    var reportGenerating by remember { mutableStateOf(false) }
    var generatedReportFile by remember { mutableStateOf<File?>(null) }
    val reportDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && generatedReportFile != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        generatedReportFile!!.inputStream().use { input -> input.copyTo(out) }
                    }
                    Toast.makeText(context, "Informe descargado", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo descargar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        scope.launch {
            healthGranted = if (healthAvailable) {
                try { app.healthConnect.hasAllPermissions() } catch (_: Exception) { false }
            } else false
            healthMessage = when {
                !healthAvailable -> context.getString(R.string.health_settings_unavailable)
                healthGranted -> context.getString(R.string.health_settings_granted)
                else -> context.getString(R.string.health_settings_missing)
            }
        }
    }
    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupManager.createBackup(context, uri)
            Toast.makeText(context, if (ok) R.string.backup_ok else R.string.backup_fail, Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val ok = BackupManager.restoreBackup(context, uri)
            Toast.makeText(context, if (ok) R.string.backup_restore_ok else R.string.backup_restore_fail, Toast.LENGTH_SHORT).show()
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
        LanguageManager.codes.forEach { code ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedLang == code, onClick = {
                    selectedLang = code
                    LanguageManager.apply(context, code)
                })
                Spacer(Modifier.width(8.dp))
                Text(LanguageManager.labels[code] ?: code, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.gama_de_colores), style = MaterialTheme.typography.titleLarge)
        AppSettings.paletteOptions.forEach { (code, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedPalette == code, onClick = {
                    selectedPalette = code
                    AppSettings.applyPalette(context, code)
                })
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.tama_o_del_texto), style = MaterialTheme.typography.titleLarge)
        AppSettings.textScaleOptions.forEach { (scale, label) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedScale == scale, onClick = {
                    selectedScale = scale
                    AppSettings.applyTextScale(context, scale)
                })
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.los_cambios_de_idioma_color_y_texto_se_aplican_inmediatamente), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.health_settings_title), style = MaterialTheme.typography.titleLarge)
        Text("Estado: $healthMessage", style = MaterialTheme.typography.bodyLarge)
        if (healthAvailable && !healthGranted) {
            Button(onClick = { try { healthLauncher.launch(app.healthConnect.permissions) } catch (e: Exception) { } }) {
                Text(stringResource(R.string.health_settings_grant))
            }
        }
        OutlinedButton(onClick = {
            try {
                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                context.startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.no_se_pudo_abrir_salud_conectada), android.widget.Toast.LENGTH_SHORT).show()
            }
        }) { Text(stringResource(R.string.health_settings_open)) }
        Text(stringResource(R.string.health_settings_note), style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(8.dp))
        Text("Informe medico", style = MaterialTheme.typography.titleLarge)
        LargeButton(
            text = if (reportGenerating) "Generando..." else "Generar informe PDF",
            onClick = {
                reportGenerating = true
                scope.launch {
                    try {
                        val f = PdfReportGenerator(app).generate(app.healthConnect, app.medicationRepository, app.tremorDao, 30)
                        generatedReportFile = f
                        Toast.makeText(context, "PDF guardado en: ${f.absolutePath}", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        reportGenerating = false
                    }
                }
            },
            enabled = !reportGenerating
        )
        generatedReportFile?.let { file ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LargeButton(
                    text = "Ver informe",
                    onClick = {
                        val authority = "${app.packageName}.fileprovider"
                        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                        val view = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(view, "Ver informe"))
                    }
                )
                LargeButton(
                    text = "Compartir",
                    onClick = {
                        val authority = "${app.packageName}.fileprovider"
                        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Compartir informe"))
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { createBackupLauncher.launch("parkinson_backup.zip") }) {
                Text(stringResource(R.string.backup_create))
            }
            Button(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text(stringResource(R.string.backup_restore))
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showDeleteDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.backup_delete))
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(R.string.backup_delete)) },
                text = { Text(stringResource(R.string.backup_delete_confirm)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            val app = context.applicationContext as ParkinsonApp
                            try {
                                app.medicationRepository.clearAll()
                                app.tremorDao.clearAll()
                                CustomMedStore.clearAll(context)
                                FavoriteMedStore.clearAll(context)
                                // Delete ON/OFF symptom episodes from Health Connect
                                scope.launch {
                                    val end = java.time.Instant.now()
                                    val start = end.minusSeconds(3650L*24*3600)
                                    val symptoms = app.healthConnect.readSymptoms(start, end)
                                    symptoms.forEach { app.healthConnect.deleteSymptom(it) }
                                }
                                MedicationAlarmScheduler.scheduleAll(context, emptyList())
                                AppSettings.applyPalette(context, "blue")
                                AppSettings.applyTextScale(context, 1f)
                                Toast.makeText(context, R.string.backup_delete_ok, Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, R.string.backup_delete_fail, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text(stringResource(android.R.string.yes)) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(android.R.string.no))
                    }
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Acerca de", style = MaterialTheme.typography.titleLarge)
        Text("Este grupo de Telegram tiene como objetivo\ncompartir experiencias, información actual, objetiva, relevante sobre la enfermedad de Párkinson.\nLas normas de uso las puedes encontrar en el mensaje fijado en la sección general.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+UiP8FW022MQxNmJk"))
            context.startActivity(intent)
        }) { Text(stringResource(R.string.abrir_grupo_telegram)) }

    }
}
