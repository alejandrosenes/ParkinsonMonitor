package com.parkinson.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.parkinson.app.R
import androidx.health.connect.client.PermissionController
import com.parkinson.app.data.health.inference.PassiveInferenceService
import com.parkinson.app.util.LanguageManager
import com.parkinson.app.ui.caregiver.CaregiverScreen
import com.parkinson.app.ui.dashboard.DashboardScreen
import com.parkinson.app.ui.medication.MedicationScreen
import com.parkinson.app.ui.report.ReportScreen
import com.parkinson.app.ui.settings.SettingsScreen
import com.parkinson.app.ui.theme.ParkinsonTheme
import com.parkinson.app.util.AppSettings

class MainActivity : ComponentActivity() {
    companion object {
        val requestedTab = mutableStateOf(0)
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.prepare(newBase))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab.value = (intent?.getIntExtra("open_tab", requestedTab.value) ?: requestedTab.value)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        requestedTab.value = (intent?.getIntExtra("open_tab", requestedTab.value) ?: requestedTab.value)
        setContent {
            ParkinsonTheme {
                val app = applicationContext as ParkinsonApp
                val context = LocalContext.current
                var showSplash by remember { mutableStateOf(true) }
                var healthIssue by remember { mutableStateOf<String?>(null) }

                if (showSplash) {
                    SplashScreen(
                        onFinished = { ok, msg ->
                            healthIssue = msg
                            showSplash = false
                        }
                    )
                } else {
                    // Notificaciones (Android 13+)
                    var notificationsGranted by remember {
                        mutableStateOf(
                            if (android.os.Build.VERSION.SDK_INT < 33) true
                            else androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val notificationLauncher = rememberLauncherForActivityResult(
                        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                    ) { granted -> notificationsGranted = granted }
                    LaunchedEffect(Unit) {
                        if (!notificationsGranted && android.os.Build.VERSION.SDK_INT >= 33) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Health Connect: intento silencioso; si no hay permisos, la app sigue en modo limitado.
                    LaunchedEffect(Unit) {
                        if (app.healthConnect.isAvailable()) {
                            try {
                                val all = app.healthConnect.hasAllPermissions()
                                if (all) {
                                    val svc = Intent(this@MainActivity, PassiveInferenceService::class.java)
                                    startForegroundService(svc)
                                }
                            } catch (_: Exception) { }
                        }
                    }

                    if (healthIssue != null) {
                        AlertDialog(
                            onDismissRequest = { healthIssue = null },
                            title = { Text("Health Connect") },
                            text = { Text(healthIssue!!) },
                            confirmButton = {
                                TextButton(onClick = { healthIssue = null }) { Text("OK") }
                            }
                        )
                    }

                    var tab by remember { mutableStateOf(requestedTab.value) }
                    LaunchedEffect(requestedTab.value) { tab = requestedTab.value }
                    val tabs = listOf(
                        stringResource(R.string.tab_day),
                        stringResource(R.string.tab_medication),
                        stringResource(R.string.tab_report),
                        "Temblor",
                        stringResource(R.string.tab_settings)
                    )
                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                tabs.forEachIndexed { i, t ->
                                    NavigationBarItem(
                                        selected = tab == i,
                                        onClick = { tab = i },
                                        label = { Text(t, maxLines = 1) },
                                        icon = { Icon(if (i == 4) Icons.Filled.Settings else Icons.Filled.Home, contentDescription = t) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding).fillMaxSize()) {
                            when (tab) {
                                0 -> DashboardScreen(onOpenMedication = { tab = 1 })
                                1 -> MedicationScreen()
                                2 -> CaregiverScreen()
                                3 -> com.parkinson.app.ui.tremor.TremorScreen()
                                4 -> SettingsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onFinished: (Boolean, String?) -> Unit) {
    val app = LocalContext.current.applicationContext as ParkinsonApp
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        var msg: String? = null
        if (app.healthConnect.isAvailable()) {
            try {
                if (!app.healthConnect.hasAllPermissions()) {
                    msg = "Health Connect no tiene permisos. Ve a Ajustes para conectarlo."
                }
            } catch (_: Exception) {
                msg = "Error al comprobar Health Connect. Ve a Ajustes para conectarlo."
            }
        } else {
            msg = "Health Connect no está disponible. Ve a Ajustes para conectarlo."
        }
        kotlinx.coroutines.delay(1800)
        alpha.animateTo(0f, animationSpec = tween(800))
        onFinished(msg == null, msg)
    }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(alpha.value),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Programada por Alejandro Senés 2026",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
