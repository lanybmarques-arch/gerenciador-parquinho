package com.example.gerenciadordeparquinho.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper

@SuppressLint("MissingPermission")
@Composable
fun SettingsScreen(
    appName: String,
    onAppNameChange: (String) -> Unit,
    showAppName: Boolean,
    onShowAppNameChange: (Boolean) -> Unit,
    printerMac: String,
    onPrinterChange: (String) -> Unit,
    printerSize: String,
    onPrinterSizeChange: (String) -> Unit,
    isSoundEnabled: Boolean,
    onSoundToggle: (Boolean) -> Unit,
    showLogo: Boolean,
    onShowLogoChange: (Boolean) -> Unit,
    logoBase64: String?,
    onSelectLogo: () -> Unit,
    onNavigateToLayout: () -> Unit,
    isLightMode: Boolean = false,
    hideVirtualKeyboard: Boolean,
    onHideVirtualKeyboardChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
    
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // Gerenciador USB
    val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    val usbDevices = remember { usbManager.deviceList.values.toList() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "CONFIGURAÇÕES", 
                color = IntenseGreen, 
                fontSize = 24.sp, 
                fontWeight = FontWeight.ExtraBold, 
                style = highlightStyle
            )
            IconButton(onClick = { onSoundToggle(!isSoundEnabled) }) {
                Icon(
                    imageVector = if (isSoundEnabled) Icons.Default.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, 
                    contentDescription = "Som", 
                    tint = if (isLightMode) Color.Black else IntenseGreen, 
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("NOME DO ESTABELECIMENTO", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
            Switch(checked = showAppName, onCheckedChange = onShowAppNameChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IntenseGreen))
        }

        if (showAppName) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = appName,
                onValueChange = onAppNameChange,
                label = { Text("Texto", color = if(isLightMode) Color.Black else IntenseGreen) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IntenseGreen,
                    unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    cursorColor = IntenseGreen
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("ATIVAR LOGOTIPO", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
            Switch(checked = showLogo, onCheckedChange = onShowLogoChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IntenseGreen))
        }

        if (showLogo) {
            Spacer(modifier = Modifier.height(12.dp))
            logoBase64?.let { base64 ->
                val bitmap = remember(base64) {
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) { null }
                }
                bitmap?.let {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(if(isLightMode) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                        Image(bitmap = it.asImageBitmap(), contentDescription = "Preview Logo", modifier = Modifier.fillMaxHeight())
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSelectLogo, 
                modifier = Modifier.fillMaxWidth().height(52.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), 
                shape = RoundedCornerShape(26.dp),
                border = buttonBorder
            ) {
                Icon(Icons.Default.AddAPhoto, null, tint = IntenseGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("SELECIONAR LOGOTIPO", color = IntenseGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("OCULTAR TECLADO VIRTUAL", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
            Switch(checked = hideVirtualKeyboard, onCheckedChange = onHideVirtualKeyboardChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = IntenseGreen))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("TAMANHO DA BOBINA", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = printerSize == "58mm", onClick = { onPrinterSizeChange("58mm") }, colors = RadioButtonDefaults.colors(selectedColor = IntenseGreen))
            Text("58mm", color = textColor, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
            Spacer(modifier = Modifier.width(20.dp))
            RadioButton(selected = printerSize == "80mm", onClick = { onPrinterSizeChange("80mm") }, colors = RadioButtonDefaults.colors(selectedColor = IntenseGreen))
            Text("80mm", color = textColor, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = if(isLightMode) Color.LightGray else Color.DarkGray, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(24.dp))

        Text("IMPRESSORA BLUETOOTH", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
        if (pairedDevices.isEmpty()) {
            Text("Nenhum dispositivo Bluetooth pareado.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        pairedDevices.forEach { device ->
            val isSelected = printerMac == device.address
            Row(modifier = Modifier.fillMaxWidth().clickable { onPrinterChange(device.address) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bluetooth, null, tint = if(isSelected) IntenseGreen else if(isLightMode) Color.Black else Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(device.name ?: "Desconhecido", color = if(isSelected) IntenseGreen else textColor, fontWeight = FontWeight.Bold)
                    Text(device.address, color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 12.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("IMPRESSORA USB", color = IntenseGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
        if (usbDevices.isEmpty()) {
            Text("Nenhuma impressora USB detectada.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        }
        usbDevices.forEach { device ->
            val deviceId = "USB:${device.deviceName}"
            val isSelected = printerMac == deviceId
            Row(modifier = Modifier.fillMaxWidth().clickable { onPrinterChange(deviceId) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Usb, null, tint = if(isSelected) IntenseGreen else if(isLightMode) Color.Black else Color.Gray)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(device.productName ?: "Impressora USB", color = if(isSelected) IntenseGreen else textColor, fontWeight = FontWeight.Bold)
                    Text("Device ID: ${device.deviceId}", color = if(isLightMode) Color.DarkGray else Color.Gray, fontSize = 12.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { BluetoothPrinterHelper.printTest(context, printerMac) { _, m -> Toast.makeText(context, m, Toast.LENGTH_SHORT).show() } },
            modifier = Modifier.fillMaxWidth().height(56.dp), 
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), 
            shape = RoundedCornerShape(28.dp),
            border = buttonBorder
        ) {
            Text("TESTAR IMPRESSORA", fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}
