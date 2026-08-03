package com.example.freeformshell

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BtSubPanel(
    isBtOn: Boolean,
    onToggleBt: (Boolean) -> Unit,
    onBack: () -> Unit,
    cardBg: Color,
    contentColor: Color,
    activeTileBg: Color,
    activeTileContent: Color,
    inactiveTileBg: Color,
    inactiveTileContent: Color,
    launchOptions: Bundle
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDeviceModel>>(emptyList()) }
    var discoveredDevices by remember { mutableStateOf<List<BluetoothDeviceModel>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var isSavedExpanded by remember { mutableStateOf(false) }

    suspend fun performScanAndFetchPaired() {
        if (!isBtOn) return
        withContext(Dispatchers.Main) { isScanning = true }
        try {
            withContext(Dispatchers.IO) {
                try {
                    ShellExecutor.executeCommand("pm grant com.example.freeformshell android.permission.BLUETOOTH_SCAN")
                    ShellExecutor.executeCommand("pm grant com.example.freeformshell android.permission.BLUETOOTH_CONNECT")
                    ShellExecutor.executeCommand("pm grant com.example.freeformshell android.permission.ACCESS_FINE_LOCATION")
                } catch (pe: Exception) {}

                val paired = ShellExecutor.getPairedBluetoothDevices(context)
                withContext(Dispatchers.Main) {
                    pairedDevices = paired
                }

                val adapter = try { android.bluetooth.BluetoothAdapter.getDefaultAdapter() } catch (e: Exception) { null }
                if (adapter != null && adapter.isEnabled) {
                    try {
                        if (adapter.isDiscovering) {
                            adapter.cancelDiscovery()
                        }
                        adapter.startDiscovery()
                    } catch (e: Exception) {
                        Log.e("BtSubPanel", "Error starting discovery", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BtSubPanel", "Error in performScanAndFetchPaired", e)
        } finally {
            withContext(Dispatchers.Main) { isScanning = false }
        }
    }

    // 14-second Auto-Restart Scanning Loop while Bluetooth is ON
    LaunchedEffect(isBtOn) {
        if (isBtOn) {
            performScanAndFetchPaired()
            while (true) {
                delay(14000)
                if (isBtOn) {
                    performScanAndFetchPaired()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                pairedDevices = emptyList()
                discoveredDevices = emptyList()
                isScanning = false
            }
        }
    }

    DisposableEffect(isBtOn) {
        if (!isBtOn) return@DisposableEffect onDispose {}

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val action = intent.action
                if (android.bluetooth.BluetoothDevice.ACTION_FOUND == action) {
                    val device = intent.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) {
                        val name = try { device.name ?: "Unknown Device" } catch (se: SecurityException) { "Unknown Device" }
                        val address = device.address ?: ""
                        if (address.isNotEmpty()) {
                            scope.launch(Dispatchers.Main) {
                                val isPaired = pairedDevices.any { it.address == address }
                                val isDiscovered = discoveredDevices.any { it.address == address }
                                if (!isPaired && !isDiscovered) {
                                    discoveredDevices = discoveredDevices + BluetoothDeviceModel(name, address, false, device)
                                }
                            }
                        }
                    }
                } else if (android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED == action ||
                    android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED == action ||
                    android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED == action ||
                    android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED == action
                ) {
                    scope.launch(Dispatchers.IO) {
                        val paired = ShellExecutor.getPairedBluetoothDevices(context)
                        withContext(Dispatchers.Main) {
                            pairedDevices = paired
                        }
                    }
                } else if (android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED == action) {
                    scope.launch(Dispatchers.Main) { isScanning = true }
                } else if (android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
                    scope.launch(Dispatchers.Main) { isScanning = false }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(android.bluetooth.BluetoothDevice.ACTION_FOUND)
            addAction(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        safeRegisterReceiver(context, receiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
            try {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                if (adapter != null && adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
            } catch (e: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = contentColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .padding(8.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Bluetooth",
                        color = contentColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System Settings",
                        tint = contentColor,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent, launchOptions)
                                } catch (e: Exception) {}
                            }
                            .padding(8.dp)
                    )

                    Switch(
                        checked = isBtOn,
                        onCheckedChange = { onToggleBt(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = activeTileContent,
                            checkedTrackColor = activeTileBg,
                            uncheckedThumbColor = inactiveTileContent,
                            uncheckedTrackColor = inactiveTileBg
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            if (!isBtOn) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Bluetooth is turned off",
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    item {
                        Text(
                            text = "Scanned Devices",
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    if (discoveredDevices.isEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = activeTileBg,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Scanning for nearby devices...",
                                    color = contentColor.copy(alpha = 0.5f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(discoveredDevices) { deviceModel ->
                            val deviceName = deviceModel.name
                            val device = deviceModel.device
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(inactiveTileBg)
                                    .clickable {
                                        try {
                                            device.createBond()
                                            Toast.makeText(context, "Pairing with $deviceName...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("BtSubPanel", "Error creating bond", e)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = deviceName,
                                        color = contentColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Tap to pair",
                                        color = contentColor.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = contentColor.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                    }

                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isSavedExpanded = !isSavedExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved Devices (${pairedDevices.size})",
                                color = contentColor.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (isSavedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle saved devices list",
                                tint = contentColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isSavedExpanded) {
                        if (pairedDevices.isEmpty()) {
                            item {
                                Text(
                                    text = "No saved devices",
                                    color = contentColor.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(pairedDevices) { deviceModel ->
                                val deviceName = deviceModel.name
                                val isConnected = deviceModel.isConnected
                                val deviceAddress = deviceModel.address

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isConnected) activeTileBg else inactiveTileBg)
                                        .clickable {
                                            val actionName = if (isConnected) "Disconnecting" else "Connecting"
                                            Toast.makeText(context, "$actionName $deviceName...", Toast.LENGTH_SHORT).show()
                                            scope.launch(Dispatchers.IO) {
                                                ShellExecutor.toggleDeviceConnection(deviceAddress, !isConnected)
                                                delay(1200)
                                                val paired = ShellExecutor.getPairedBluetoothDevices(context)
                                                withContext(Dispatchers.Main) {
                                                    pairedDevices = paired
                                                }
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = if (isConnected) activeTileContent else contentColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = deviceName,
                                            color = if (isConnected) activeTileContent else contentColor,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (isConnected) {
                                            Text(
                                                text = "Connected",
                                                color = activeTileContent.copy(alpha = 0.8f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
