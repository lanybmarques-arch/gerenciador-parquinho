package com.example.gerenciadordeparquinho.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper
import com.example.gerenciadordeparquinho.utils.normalizeName
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

@Composable
fun QRScannerScreen(
    onClose: () -> Unit,
    onScanResult: (String) -> Unit,
    isPreAutoMode: Boolean = false,
    autoPrint: Boolean,
    onAutoPrintChange: (Boolean) -> Unit,
    printerMac: String,
    printerSize: String,
    logoBase64: String?,
    appName: String,
    isLightMode: Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator
    
    var showResultDialog by remember { mutableStateOf<ScannerSummary?>(null) }
    var showNotFoundDialog by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "scannerLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "lineOffset"
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    val scanner = BarcodeScanning.getClient(options)
                    val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        @SuppressLint("UnsafeOptInUsageError")
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        barcode.rawValue?.let { qrData ->
                                            if (showResultDialog == null && !showNotFoundDialog) {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                                                else vibrator.vibrate(50)
                                                
                                                if (isPreAutoMode) onScanResult(qrData)
                                                else {
                                                    scope.launch {
                                                        val summary = processQRData(qrData, db)
                                                        if (summary != null) {
                                                            showResultDialog = summary
                                                            if (autoPrint && printerMac.isNotEmpty()) {
                                                                BluetoothPrinterHelper.printChildSummary(context, printerMac, summary.name, summary.date, summary.sessions, summary.totalToPay, printerSize, logoBase64, appName)
                                                            }
                                                        } else if (qrData.startsWith("SDR|")) showNotFoundDialog = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else imageProxy.close()
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) { e.printStackTrace() }
                }, androidx.core.content.ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width; val height = size.height; val boxSize = width * 0.7f
            val left = (width - boxSize) / 2; val top = (height - boxSize) / 2
            val cornerLen = 40.dp.toPx(); val strokeWidth = 4.dp.toPx()
            drawLine(IntenseGreen, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
            drawLine(IntenseGreen, Offset(left, top), Offset(left, top + cornerLen), strokeWidth)
            drawLine(IntenseGreen, Offset(left + boxSize, top), Offset(left + boxSize - cornerLen, top), strokeWidth)
            drawLine(IntenseGreen, Offset(left + boxSize, top), Offset(left + boxSize, top + cornerLen), strokeWidth)
            drawLine(IntenseGreen, Offset(left, top + boxSize), Offset(left + cornerLen, top + boxSize), strokeWidth)
            drawLine(IntenseGreen, Offset(left, top + boxSize), Offset(left, top + boxSize - cornerLen), strokeWidth)
            drawLine(IntenseGreen, Offset(left + boxSize, top + boxSize), Offset(left + boxSize - cornerLen, top + boxSize), strokeWidth)
            drawLine(IntenseGreen, Offset(left + boxSize, top + boxSize), Offset(left + boxSize, top + boxSize - cornerLen), strokeWidth)
            drawLine(IntenseGreen.copy(alpha = 0.7f), Offset(left + 10.dp.toPx(), top + (boxSize * lineOffset)), Offset(left + boxSize - 10.dp.toPx(), top + (boxSize * lineOffset)), 2.dp.toPx())
        }

        Row(modifier = Modifier.fillMaxWidth().padding(16.dp).statusBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) { Icon(Icons.Default.Close, null, tint = Color.White) }
            Text(if (isPreAutoMode) "AUTO PREENCHER" else "SCANNER SDR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            IconButton(onClick = { isFlashOn = !isFlashOn; cameraControl?.enableTorch(isFlashOn) }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))) { Icon(if (isFlashOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff, null, tint = Color.White) }
        }

        if (!isPreAutoMode) {
            Card(modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, IntenseGreen.copy(alpha = 0.5f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) { Text("IMPRESSÃO AUTOMÁTICA", color = IntenseGreen, fontSize = 12.sp, fontWeight = FontWeight.Black); Text("Imprimir ao ler QR Code", color = Color.LightGray, fontSize = 10.sp) }
                    Switch(checked = autoPrint, onCheckedChange = onAutoPrintChange, colors = SwitchDefaults.colors(checkedThumbColor = IntenseGreen, checkedTrackColor = IntenseGreen.copy(alpha = 0.5f)))
                }
            }
        }
    }

    if (showResultDialog != null) {
        val summary = showResultDialog!!
        AlertDialog(
            onDismissRequest = { showResultDialog = null }, containerColor = if(isLightMode) Color.White else Color(0xFF121212),
            title = { Column { Text("RESUMO SDR", color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 14.sp); Text(summary.name.uppercase(), color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp); Text("DATA: ${summary.date}", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) } },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp)); Text("ITENS REGISTRADOS:", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).padding(vertical = 8.dp)) {
                        summary.sessions.forEach { session ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) { Text(session.toyName.uppercase(), color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold); if (session.isPaid) Text("PAGO ANTECIPADO", color = IntenseGreen, fontSize = 9.sp, fontWeight = FontWeight.Black) }
                                val valShow = if(session.isFinished) session.totalValueAccumulated else session.calculateCurrentProportionalValue()
                                Text("R$ %.2f".format(valShow), color = if(session.isPaid) IntenseGreen else Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth().background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF1A1A1A), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("TOTAL GERAL", color = if(isLightMode) Color.Black else Color.White, fontSize = 12.sp); Text("R$ %.2f".format(summary.totalAccumulated), color = if(isLightMode) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("VALORES PAGOS", color = IntenseGreen, fontSize = 12.sp); Text("- R$ %.2f".format(summary.totalAlreadyPaid), color = IntenseGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(8.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("SALDO A PAGAR", color = Color.Red, fontWeight = FontWeight.Black, fontSize = 16.sp); Text("R$ %.2f".format(summary.totalToPay), color = Color.Red, fontWeight = FontWeight.Black, fontSize = 24.sp) }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { if (printerMac.isNotEmpty()) BluetoothPrinterHelper.printChildSummary(context, printerMac, summary.name, summary.date, summary.sessions, summary.totalToPay, printerSize, logoBase64, appName) }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(25.dp)) { Icon(Icons.Default.Print, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("IMPRIMIR NOTA", fontWeight = FontWeight.Black) } },
            dismissButton = { TextButton(onClick = { showResultDialog = null }, modifier = Modifier.fillMaxWidth()) { Text("FECHAR", color = Color.Red, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showNotFoundDialog) {
        AlertDialog(
            onDismissRequest = { showNotFoundDialog = false }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            icon = { Icon(Icons.Default.Info, null, tint = Color.Red, modifier = Modifier.size(48.dp)) },
            title = { Text("NÃO ENCONTRADO", color = Color.Red, fontWeight = FontWeight.Black) },
            text = { Text("Nenhum registro ativo ou histórico foi encontrado.", textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
            confirmButton = { Button(onClick = { showNotFoundDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black)) { Text("TENTAR NOVAMENTE", fontWeight = FontWeight.Bold) } }
        )
    }
}

data class ScannerSummary(val name: String, val date: String, val sessions: List<PlaySession>, val totalAccumulated: Double, val totalAlreadyPaid: Double, val totalToPay: Double)

suspend fun processQRData(qrData: String, db: AppDatabase): ScannerSummary? {
    return try {
        val parts = qrData.split("|")
        if (parts.size >= 3) {
            val name = parts[1].normalizeName().trim()
            val date = parts[2].trim()
            val sessions = db.sessionDao().getSessionsByPersonNameAndDate(name, date)
            if (sessions.isNotEmpty()) {
                var totalAcc = 0.0; var totalPaid = 0.0
                sessions.forEach { s ->
                    val currentVal = if(s.isFinished) s.totalValueAccumulated else s.calculateCurrentProportionalValue()
                    totalAcc += currentVal; if (s.isPaid) totalPaid += currentVal
                }
                ScannerSummary(name, date, sessions, totalAcc, totalPaid, (totalAcc - totalPaid).coerceAtLeast(0.0))
            } else null
        } else null
    } catch (e: Exception) { null }
}
