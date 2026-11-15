package com.leandro.btbridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { Surface { BridgeScreen() } }
        }
    }
}

private fun requiredPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

@Composable
fun BridgeScreen(vm: BtDevicesViewModel = viewModel()) {
    val ctx = LocalContextSafe()
    val devices by vm.devices.collectAsState()
    val scanning by vm.scanning.collectAsState()

    var hasPerms by remember { mutableStateOf(hasAllPermissions(ctx)) }
    var pcIp by remember { mutableStateOf("") }
    var relayOn by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPerms = result.values.all { it } }

    LaunchedEffect(hasPerms) { if (hasPerms) vm.loadBonded() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("BT Audio Bridge", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Emparejá tus auriculares acá, poné la IP de la PC y activá el puente.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        if (!hasPerms) {
            Button(onClick = { permLauncher.launch(requiredPermissions()) }) {
                Text("Dar permisos de Bluetooth y micrófono")
            }
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (scanning) vm.stopScan() else vm.startScan() },
                enabled = !relayOn
            ) { Text(if (scanning) "Detener escaneo" else "Buscar dispositivos") }
            Spacer(Modifier.width(8.dp))
            if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(devices, key = { it.address }) { d ->
                ListItem(
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text(d.address) },
                    trailingContent = {
                        if (d.bonded) {
                            AssistChip(onClick = {}, label = { Text("Emparejado") })
                        } else {
                            TextButton(onClick = { vm.pair(d.address) }) { Text("Emparejar") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pcIp,
            onValueChange = { pcIp = it },
            label = { Text("IP de la PC (opcional — se detecta sola)") },
            singleLine = true,
            enabled = !relayOn,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (relayOn) {
                    AudioRelayService.stop(ctx); relayOn = false
                } else {
                    AudioRelayService.start(ctx, pcIp.trim()); relayOn = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (relayOn) "Detener puente" else "Activar puente de audio") }
    }
}

private fun hasAllPermissions(ctx: android.content.Context): Boolean =
    requiredPermissions().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun LocalContextSafe() = androidx.compose.ui.platform.LocalContext.current
