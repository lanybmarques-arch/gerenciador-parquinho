package com.example.gerenciadordeparquinho.ui.screens

import android.app.DatePickerDialog
import android.media.MediaPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gerenciadordeparquinho.R
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.example.gerenciadordeparquinho.data.model.Toy
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper
import com.example.gerenciadordeparquinho.utils.base64ToBitmap
import com.example.gerenciadordeparquinho.utils.normalizeName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Enum para os estados de arrastar (Swipe)
enum class DragAnchors {
    Start,
    Center, End
}

@Composable
fun HomeScreen(
    appName: String, isSoundEnabled: Boolean = true, printerMac: String = "", printerSize: String = "58mm",
    logoBase64: String? = null, isLightMode: Boolean = false, autoPrintEntrance: Boolean = false,
    onAutoPrintEntranceChange: (Boolean) -> Unit = {}, autoPrintExit: Boolean = false,
    onAutoPrintExitChange: (Boolean) -> Unit = {}, autoPrintSDR: Boolean = false,
    onAutoPrintSDRChange: (Boolean) -> Unit = {}, autoPrintScannerSummary: Boolean = false,
    onAutoPrintScannerSummaryChange: (Boolean) -> Unit = {}, isPreAutoEnabled: Boolean = false,
    onPreAutoToggle: (Boolean) -> Unit = {}, isAdmin: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryColor = if (isLightMode) Color.DarkGray else Color.Gray
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
    val topIconColor = if (isLightMode) Color.Black else IntenseGreen

    var childName by rememberSaveable { mutableStateOf("") }
    var selectedToy by remember { mutableStateOf<Toy?>(null) }
    var showToyPicker by remember { mutableStateOf(false) }
    var showTicketsDialog by remember { mutableStateOf(false) }
    var isCashierMode by remember { mutableStateOf(false) }
    var showDuplicateNameDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var isPreAutoScannerActive by remember { mutableStateOf(false) }
    var showCheckoutPDV by remember { mutableStateOf<Pair<String, List<PlaySession>>?>(null) }
    
    val toys by db.toyDao().getAllToys().collectAsState(initial = emptyList())
    val activeSessionsFromDb by db.sessionDao().getActiveSessions().collectAsState(initial = emptyList())
    val mediaPlayer = remember { MediaPlayer.create(context, R.raw.admin_alert) }
    val clickPlayer = remember { MediaPlayer.create(context, R.raw.click) }
    val cashierPlayer = remember { MediaPlayer.create(context, R.raw.caixa) }

    val activeSessionsState = rememberUpdatedState(activeSessionsFromDb)
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            activeSessionsState.value.forEach { session ->
                if (!session.isFinished && !session.isPaused) {
                    if (session.remainingSeconds > 0) {
                        val diffSeconds = (now - session.lastUpdateTimestamp) / 1000
                        if (diffSeconds >= 1) {
                            val newRemaining = (session.remainingSeconds - diffSeconds).coerceAtLeast(0L)
                            db.sessionDao().insertSession(session.copy(remainingSeconds = newRemaining, elapsedSecondsInCurrentCycle = session.elapsedSecondsInCurrentCycle + diffSeconds, lastUpdateTimestamp = now))
                        }
                    } else {
                        if (isSoundEnabled) { try { if (!mediaPlayer.isPlaying) mediaPlayer.start() } catch (_: Exception) {} }
                        if (autoPrintExit && !session.notified && printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printEntranceTicket(printerMac, session, printerSize, logoBase64, appName)
                            db.sessionDao().insertSession(session.copy(notified = true, lastUpdateTimestamp = now))
                        } else { db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now)) }
                    }
                } else if (session.isPaused) { db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now)) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { isCashierMode = true; showTicketsDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp), modifier = Modifier.height(42.dp)
            ) {
                Icon(Icons.Default.PointOfSale, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("COBRAR", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Row {
                IconButton(onClick = { showScanner = true }) { Icon(Icons.Default.QrCodeScanner, null, tint = topIconColor, modifier = Modifier.size(28.dp)) }
                IconButton(onClick = { isCashierMode = false; showTicketsDialog = true }) { Icon(Icons.Default.ConfirmationNumber, null, tint = topIconColor, modifier = Modifier.size(28.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = childName, onValueChange = { childName = it.normalizeName() }, label = { Text("NOME DA CRIANÇA", color = secondaryColor, fontWeight = FontWeight.Bold) },
            modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IntenseGreen, unfocusedBorderColor = secondaryColor, focusedLabelColor = IntenseGreen, cursorColor = IntenseGreen),
            shape = RoundedCornerShape(12.dp), singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (isLightMode) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)).clickable { showToyPicker = true }.border(1.dp, if (selectedToy != null) IntenseGreen else secondaryColor, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(selectedToy?.name?.uppercase() ?: "ESCOLHA O BRINQUEDO", color = if (selectedToy != null) textColor else secondaryColor, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ArrowDropDown, null, tint = IntenseGreen, modifier = Modifier.align(Alignment.CenterEnd))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PRE AUTO", color = secondaryColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Switch(checked = isPreAutoEnabled, onCheckedChange = onPreAutoToggle, colors = SwitchDefaults.colors(checkedThumbColor = if(isLightMode) Color.Black else IntenseGreen, checkedTrackColor = IntenseGreen.copy(alpha = 0.5f), uncheckedThumbColor = if(isLightMode) Color.Black else Color.Gray))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text("IMPRESSÃO AUTOMÁTICA:", color = secondaryColor, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                Checkbox(checked = autoPrintEntrance, onCheckedChange = onAutoPrintEntranceChange, colors = CheckboxDefaults.colors(checkedColor = IntenseGreen, checkmarkColor = Color.Black))
                Text("Entrada", color = textColor, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.width(12.dp))
                Checkbox(checked = autoPrintExit, onCheckedChange = onAutoPrintExitChange, colors = CheckboxDefaults.colors(checkedColor = IntenseGreen, checkmarkColor = Color.Black))
                Text("Saída", color = textColor, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                Spacer(modifier = Modifier.width(12.dp))
                Checkbox(checked = autoPrintSDR, onCheckedChange = onAutoPrintSDRChange, colors = CheckboxDefaults.colors(checkedColor = IntenseGreen, checkmarkColor = Color.Black))
                Text("SDR", color = textColor, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(if (isPressed) 0.92f else 1f, label = "buttonScale")

        Button(
            onClick = {
                try { clickPlayer?.start() } catch (_: Exception) {}
                val currentChildName = childName.normalizeName().trim()
                if (activeSessionsFromDb.any { it.personName.equals(currentChildName, ignoreCase = true) }) { showDuplicateNameDialog = true } else {
                    val t = selectedToy!!
                    val newSession = PlaySession(personName = currentChildName, toyName = t.name, toyPrice = t.price, toyTimeMinutes = t.timeMinutes, remainingSeconds = (t.timeMinutes * 60).toLong(), lastUpdateTimestamp = System.currentTimeMillis())
                    scope.launch { 
                        val alreadyToday = db.sessionDao().getSessionsByPersonNameAndDate(currentChildName, newSession.date).isNotEmpty()
                        db.sessionDao().insertSession(newSession)
                        if (autoPrintEntrance && printerMac.isNotEmpty()) { BluetoothPrinterHelper.printEntranceTicket(printerMac, newSession, printerSize, logoBase64, appName); delay(1500) }
                        if (autoPrintSDR && printerMac.isNotEmpty() && !alreadyToday) { BluetoothPrinterHelper.printSTRQRCode(printerMac, newSession, printerSize) }
                    }
                    childName = ""; selectedToy = null
                }
            },
            enabled = childName.isNotBlank() && selectedToy != null, interactionSource = interactionSource, modifier = Modifier.fillMaxWidth().height(54.dp).graphicsLayer(scaleX = scale, scaleY = scale),
            colors = ButtonDefaults.buttonColors(containerColor = if (childName.isNotBlank() && selectedToy != null) IntenseGreen else Color(0xFF333333)), shape = RoundedCornerShape(27.dp), border = if(childName.isNotBlank() && selectedToy != null) buttonBorder else null
        ) { Text("INICIAR TEMPO", fontWeight = FontWeight.Black) }
        Spacer(modifier = Modifier.height(32.dp))
        Text("CRONÔMETROS ATIVOS", color = IntenseGreen, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(activeSessionsFromDb, key = { it.id }) { session ->
                ActiveTimerItemUI(session, toys, activeSessionsFromDb, printerMac, printerSize, logoBase64, appName, isLightMode, onFinish = { s -> scope.launch { db.sessionDao().insertSession(s.copy(isFinished = true, endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()), totalValueAccumulated = s.calculateCurrentProportionalValue())) } }, onContinue = { s -> scope.launch { db.sessionDao().insertSession(s.copy(totalValueAccumulated = s.totalValueAccumulated + s.toyPrice, elapsedSecondsInCurrentCycle = 0, remainingSeconds = (s.toyTimeMinutes * 60).toLong(), notified = false, lastUpdateTimestamp = System.currentTimeMillis())) } }, onToyChanged = { updated -> scope.launch { db.sessionDao().insertSession(updated.copy(lastUpdateTimestamp = System.currentTimeMillis())) } }, onPauseToggle = { paused, release -> scope.launch { db.sessionDao().insertSession(session.copy(isPaused = paused, isToyReleased = release, lastUpdateTimestamp = System.currentTimeMillis())) } }, onPaidToggle = { paid -> scope.launch { db.sessionDao().insertSession(session.copy(isPaid = paid)) } })
            }
        }
    }

    if (showTicketsDialog) {
        var selectedDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
        val ticketsOfDay by db.sessionDao().getSessionsByDate(selectedDate).collectAsState(initial = emptyList())
        var expandedName by remember { mutableStateOf<String?>(null) }
        val grouped = remember(ticketsOfDay, isCashierMode) { 
            val allGrouped = ticketsOfDay.groupBy { it.personName.trim().uppercase() }
            if (isCashierMode) {
                allGrouped.filter { (_, sessions) -> sessions.any { !it.isPaid || !it.isFinished } }
            } else {
                allGrouped
            }
        }

        AlertDialog(
            onDismissRequest = { showTicketsDialog = false }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if(isCashierMode) "COBRAR CONSUMO" else "TICKETS DO DIA", color = IntenseGreen, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, style = highlightStyle)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { DatePickerDialog(context, { _, y, m, d -> val c = Calendar.getInstance(); c.set(y, m, d); selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(c.time) }, Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).show() }) { Icon(Icons.Default.CalendarToday, null, tint = IntenseGreen, modifier = Modifier.size(22.dp)) }
                }
            },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                        grouped.forEach { (name, sessions) ->
                            item {
                                val total = sessions.sumOf { it.calculateCurrentProportionalValue() }
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { expandedName = if (expandedName == name) null else name }, colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF222222)), shape = RoundedCornerShape(12.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if(expandedName == name) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = IntenseGreen, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(name, color = textColor, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                            if (isCashierMode) {
                                                Button(onClick = { showCheckoutPDV = name to sessions; showTicketsDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) { Text("COBRAR", fontWeight = FontWeight.Black, fontSize = 11.sp) }
                                            } else {
                                                Row {
                                                    IconButton(onClick = { if(printerMac.isNotEmpty()) BluetoothPrinterHelper.printSTRQRCode(printerMac, sessions.first(), printerSize) }) { Icon(Icons.Default.QrCode, null, tint = topIconColor) }
                                                    IconButton(onClick = { if(printerMac.isNotEmpty()) BluetoothPrinterHelper.printChildSummary(printerMac, name, sessions.first().date, sessions, total, printerSize, logoBase64, appName) }) { Icon(Icons.Default.Print, null, tint = topIconColor) }
                                                }
                                            }
                                        }
                                        if (expandedName == name) {
                                            Spacer(Modifier.height(8.dp))
                                            sessions.forEach { s -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { 
                                                val currentV = s.calculateCurrentProportionalValue()
                                                val qty = if (s.toyPrice > 0) currentV / s.toyPrice else 1.0
                                                val qtyDisplay = if (qty % 1.0 == 0.0) qty.toInt().toString() else "%.1f".format(qty).replace(".", ",")
                                                
                                                Text("${s.toyName.uppercase()} ${qtyDisplay}X", color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("R$ %.2f".format(currentV), color = if(s.isPaid) IntenseGreen else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                    if(s.isPaid) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, null, tint = IntenseGreen, modifier = Modifier.size(14.dp)) }
                                                }
                                            } }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = textColor.copy(alpha = 0.1f))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("TOTAL:", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold); Text("R$ %.2f".format(total), color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Black) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("TOTAL GERAL: R$ %.2f".format(grouped.values.flatten().sumOf { it.calculateCurrentProportionalValue() }), color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton(onClick = { showTicketsDialog = false }) { Text("FECHAR", color = IntenseGreen, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showToyPicker) {
        AlertDialog(
            onDismissRequest = { showToyPicker = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("ESCOLHA O BRINQUEDO", color = IntenseGreen, style = highlightStyle) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(toys) { toy ->
                        val isOccupied = !toy.isAlwaysFree && activeSessionsFromDb.any { it.toyName == toy.name && !it.isFinished && !it.isToyReleased }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isOccupied) { 
                                    selectedToy = toy
                                    showToyPicker = false 
                                    if (isPreAutoEnabled && childName.trim().isEmpty()) { isPreAutoScannerActive = true }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(50.dp).background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF222222), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                val bitmap = base64ToBitmap(toy.imageBase64)
                                if (bitmap != null) {
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Icon(Icons.Default.Toys, null, tint = IntenseGreen, modifier = Modifier.size(24.dp))
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(toy.name.uppercase(), color = if(isOccupied) Color.Red else textColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if(isOccupied) Text("OCUPADO", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Text("R$ %.2f".format(toy.price), color = if(isOccupied) Color.Red else IntenseGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showToyPicker = false }) { Text("FECHAR", color = IntenseGreen, fontWeight = FontWeight.Bold) } }
        )
    }

    showCheckoutPDV?.let { (name, sessions) ->
        CheckoutPDVDialog(
            name = name, sessions = sessions, 
            onDismiss = { showCheckoutPDV = null }, 
            onConfirmPayment = { updated, cash, pix, card, totalBruto, paidSoFarNow, change -> 
                scope.launch { 
                    try { cashierPlayer?.start() } catch(_:Exception){}
                    val nowTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val finalizedSessions = updated.map { s ->
                        s.copy(
                            isPaid = true,
                            isFinished = true,
                            endTime = nowTime,
                            totalValueAccumulated = s.calculateCurrentProportionalValue(),
                            remainingSeconds = 0,
                            isPaused = false,
                            isToyReleased = true
                        )
                    }
                    finalizedSessions.forEach { db.sessionDao().insertSession(it) }
                    if (printerMac.isNotEmpty()) {
                        val alreadyPaid = sessions.filter { it.isPaid }.sumOf { it.totalValueAccumulated }
                        BluetoothPrinterHelper.printCheckoutReceipt(
                            printerMac, name, sessions, totalBruto, alreadyPaid, cash, pix, card, change, 
                            printerSize, logoBase64, appName
                        )
                    }
                    showCheckoutPDV = null 
                } 
            }, 
            isLightMode = isLightMode
        )
    }

    if (showDuplicateNameDialog) { AlertDialog(onDismissRequest = { showDuplicateNameDialog = false }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A), title = { Text("NOME EM USO!", color = Color.Red, fontWeight = FontWeight.Bold) }, text = { Text("Já existe um tempo aberto para '$childName'.", color = if(isLightMode) Color.Black else Color.White) }, confirmButton = { TextButton(onClick = { showDuplicateNameDialog = false }) { Text("ENTENDI", color = IntenseGreen, fontWeight = FontWeight.Bold) } }) }
    if (showScanner || isPreAutoScannerActive) { QRScannerScreen(onClose = { showScanner = false; isPreAutoScannerActive = false }, onScanResult = { qrData -> if (isPreAutoScannerActive) { val parts = qrData.split("|"); if (parts.size >= 2) childName = parts[1].normalizeName().trim().uppercase(); isPreAutoScannerActive = false } else showScanner = false }, isPreAutoMode = isPreAutoScannerActive, autoPrint = autoPrintScannerSummary, onAutoPrintChange = onAutoPrintScannerSummaryChange, printerMac = printerMac, printerSize = printerSize, logoBase64 = logoBase64, appName = appName, isLightMode = isLightMode) }
    
    val expiredSession = activeSessionsFromDb.find { it.remainingSeconds <= 0 && !it.isFinished && !it.isPaused }
    if (expiredSession != null) {
        val currentVal = expiredSession.calculateCurrentProportionalValue()
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { 
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text("TEMPO ESGOTADO!", color = Color.Red, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (expiredSession.isPaid) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).rotate(15f).offset(x = 10.dp, y = (-10).dp),
                            color = Color.Transparent, border = BorderStroke(3.dp, IntenseGreen), shape = RoundedCornerShape(8.dp)
                        ) { Text("PAGO", color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                    }
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(expiredSession.personName.uppercase(), color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(expiredSession.toyName.uppercase(), color = IntenseGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("VALOR A PAGAR:", color = Color.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Black)
                    Text("R$ %.2f".format(currentVal), color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 48.sp, style = highlightStyle)
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { 
                            try { if (mediaPlayer.isPlaying) { mediaPlayer.pause(); mediaPlayer.seekTo(0) } } catch (_: Exception) {}
                            scope.launch { db.sessionDao().insertSession(expiredSession.copy(totalValueAccumulated = expiredSession.totalValueAccumulated + expiredSession.toyPrice, elapsedSecondsInCurrentCycle = 0, remainingSeconds = (expiredSession.toyTimeMinutes * 60).toLong(), notified = false, lastUpdateTimestamp = System.currentTimeMillis())) }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(26.dp)
                    ) { Text("CONTINUAR (MAIS ${expiredSession.toyTimeMinutes} MIN)", fontWeight = FontWeight.ExtraBold) }
                    Button(
                        onClick = { 
                            try { if (mediaPlayer.isPlaying) { mediaPlayer.pause(); mediaPlayer.seekTo(0) } } catch (_: Exception) {}
                            scope.launch { db.sessionDao().insertSession(expiredSession.copy(isFinished = true, endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()), totalValueAccumulated = currentVal)) }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)), shape = RoundedCornerShape(26.dp)
                    ) { Text("ENCERRAR E SALVAR", fontWeight = FontWeight.ExtraBold, color = Color.White) }
                }
            }
        )
    }
}

@Composable
fun CheckoutPDVDialog(
    name: String, 
    sessions: List<PlaySession>, 
    onDismiss: () -> Unit, 
    onConfirmPayment: (List<PlaySession>, Double, Double, Double, Double, Double, Double) -> Unit, 
    isLightMode: Boolean
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    var cash by remember { mutableStateOf("") }
    var pix by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }
    
    val totalBruto = sessions.sumOf { it.calculateCurrentProportionalValue() }
    val alreadyPaid = sessions.filter { it.isPaid }.sumOf { it.totalValueAccumulated }
    val totalToPayNow = (totalBruto - alreadyPaid).coerceAtLeast(0.0)
    
    val paidSoFarNow = (cash.toDoubleOrNull() ?: 0.0) + (pix.toDoubleOrNull() ?: 0.0) + (card.toDoubleOrNull() ?: 0.0)
    val remaining = (totalToPayNow - paidSoFarNow).coerceAtLeast(0.0)
    val change = (paidSoFarNow - totalToPayNow).coerceAtLeast(0.0)
    
    val bgColor = if (isLightMode) Color.White else Color(0xFF121212)
    val textColor = if (isLightMode) Color.Black else Color.White
    val cardBg = if (isLightMode) Color(0xFFF5F5F5) else Color(0xFF1E1E1E)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = IntenseGreen) }
                    Text("CAIXA PDV - ${name.uppercase()}", fontWeight = FontWeight.ExtraBold, color = IntenseGreen, fontSize = 20.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(48.dp))
                }
                if (isTablet) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) { 
                            PDVDetailsSection(sessions, totalBruto, alreadyPaid, cardBg, textColor) 
                        }
                        Column(modifier = Modifier.weight(1f)) { 
                            PDVPaymentSection(cash, { cash = it }, pix, { pix = it }, card, { card = it }, totalToPayNow, paidSoFarNow, remaining, change, onConfirm = { 
                                onConfirmPayment(sessions, cash.toDoubleOrNull() ?: 0.0, pix.toDoubleOrNull() ?: 0.0, card.toDoubleOrNull() ?: 0.0, totalBruto, paidSoFarNow, change) 
                            }, textColor) 
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                        PDVDetailsSection(sessions, totalBruto, alreadyPaid, cardBg, textColor)
                        Spacer(Modifier.height(24.dp))
                        PDVPaymentSection(cash, { cash = it }, pix, { pix = it }, card, { card = it }, totalToPayNow, paidSoFarNow, remaining, change, onConfirm = { 
                            onConfirmPayment(sessions, cash.toDoubleOrNull() ?: 0.0, pix.toDoubleOrNull() ?: 0.0, card.toDoubleOrNull() ?: 0.0, totalBruto, paidSoFarNow, change) 
                        }, textColor)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PDVDetailsSection(sessions: List<PlaySession>, total: Double, alreadyPaid: Double, cardBg: Color, textColor: Color) {
    val beigeColor = Color(0xFFF5F5DC) 
    val contentColor = Color.Black 
    
    Text("VALORES DETALHADOS", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = beigeColor), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            val unpaid = sessions.filter { !it.isPaid }
            val paid = sessions.filter { it.isPaid }

            unpaid.groupBy { it.toyName to it.toyPrice }.forEach { (key, list) ->
                val unitPrice = key.second
                val subTotal = list.sumOf { it.calculateCurrentProportionalValue() }
                val qty = if (unitPrice > 0) subTotal / unitPrice else 1.0
                val qtyDisplay = if (qty % 1.0 == 0.0) qty.toInt().toString() else "%.1f".format(qty).replace(".", ",")
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                    Text("${key.first.uppercase()} ${qtyDisplay}X R$ %.2f".format(unitPrice), 
                        modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("R$ %.2f".format(subTotal), color = contentColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }

            paid.groupBy { it.toyName to it.toyPrice }.forEach { (key, list) ->
                val unitPrice = key.second
                val subTotal = list.sumOf { it.totalValueAccumulated }
                val qty = if (unitPrice > 0) subTotal / unitPrice else 1.0
                val qtyDisplay = if (qty % 1.0 == 0.0) qty.toInt().toString() else "%.1f".format(qty).replace(".", ",")
                
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { 
                    Text("${key.first.uppercase()} ${qtyDisplay}X R$ %.2f".format(unitPrice),
                        modifier = Modifier.weight(1f), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("- R$ %.2f".format(subTotal), color = contentColor, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = contentColor.copy(alpha = 0.2f))
            
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TOTAL BRUTO", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("R$ %.2f".format(total), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (alreadyPaid > 0) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VALORES PAGOS (ANTECIPADO)", color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("- R$ %.2f".format(alreadyPaid), color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { 
                Text("SALDO A PAGAR", modifier = Modifier.weight(1f), color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Black)
                val saldo = (total - alreadyPaid).coerceAtLeast(0.0)
                Text("R$ %.2f".format(saldo), color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
fun PDVPaymentSection(c: String, onC: (String) -> Unit, p: String, onP: (String) -> Unit, crd: String, onCr: (String) -> Unit, t: Double, paid: Double, r: Double, ch: Double, onConfirm: () -> Unit, txt: Color) {
    Text("PAGAMENTO ATUAL", color = IntenseGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(12.dp))
    PDVInput("DINHEIRO", c, onC, Icons.Default.Payments)
    Spacer(Modifier.height(12.dp)); PDVInput("PIX", p, onP, Icons.Default.QrCode)
    Spacer(Modifier.height(12.dp)); PDVInput("CARTÃO", crd, onCr, Icons.Default.CreditCard)
    Spacer(Modifier.height(24.dp))
    Column(modifier = Modifier.fillMaxWidth().background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(12.dp)).padding(16.dp)) {
        PDVRow("VALOR PAGO AGORA", "R$ %.2f".format(paid), txt)
        if (r > 0.01) PDVRow("RESTANTE", "R$ %.2f".format(r), Color.Red)
        if (ch > 0.01) PDVRow("TROCO", "R$ %.2f".format(ch), IntenseGreen)
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onConfirm, enabled = paid >= t - 0.01, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(28.dp)) { Text("FINALIZAR PAGAMENTO", fontWeight = FontWeight.Black, fontSize = 16.sp) }
}

@Composable
fun PDVInput(l: String, v: String, onV: (String) -> Unit, i: androidx.compose.ui.graphics.vector.ImageVector) {
    OutlinedTextField(value = v, onValueChange = { if (it.all { char -> char.isDigit() || char == '.' || char == ',' }) onV(it.replace(",", ".")) }, label = { Text(l, fontSize = 12.sp) }, leadingIcon = { Icon(i, null, tint = IntenseGreen, modifier = Modifier.size(20.dp)) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = IntenseGreen, unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f), focusedLabelColor = IntenseGreen), singleLine = true)
}

@Composable
fun PDVRow(l: String, v: String, c: Color) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(l, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.copy(alpha = 0.7f)); Text(v, fontSize = 18.sp, fontWeight = FontWeight.Black, color = c) } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveTimerItemUI(
    session: PlaySession, allToys: List<Toy>, activeSessions: List<PlaySession>,
    printerMac: String, printerSize: String, logoBase64: String?, appName: String,
    isLightMode: Boolean, onFinish: (PlaySession) -> Unit, onContinue: (PlaySession) -> Unit,
    onToyChanged: (PlaySession) -> Unit, onPauseToggle: (Boolean, Boolean) -> Unit, onPaidToggle: (Boolean) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    var showSwapPicker by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf<Toy?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var forceOriginalTimer by rememberSaveable { mutableStateOf(false) }
    val isExpired = session.remainingSeconds <= 0L
    val isLowTime = session.remainingSeconds > 0 && session.remainingSeconds < 60L
    val currentVal = session.calculateCurrentProportionalValue()
    val highlightStyle = getHighlightStyle(isLightMode)
    val density = LocalDensity.current
    var paymentClicks by remember { mutableIntStateOf(0) }
    val cashPlayer = remember { MediaPlayer.create(context, R.raw.caixa) }

    val swipeState = remember { AnchoredDraggableState<DragAnchors>(initialValue = DragAnchors.Center, positionalThreshold = { distance: Float -> distance * 0.5f }, velocityThreshold = { with(density) { 100.dp.toPx() } }, snapAnimationSpec = spring(), decayAnimationSpec = exponentialDecay()) }
    SideEffect { swipeState.updateAnchors(DraggableAnchors { DragAnchors.Start at -with(density) { 150.dp.toPx() }; DragAnchors.Center at 0f; DragAnchors.End at with(density) { 150.dp.toPx() } }) }
    val currentOffset = try { swipeState.requireOffset() } catch (_: Exception) { 0f }
    LaunchedEffect(session.isPaid) { if (!session.isPaid) forceOriginalTimer = false }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))) {
        val bgColorActionLeft = if (session.isPaid) Color(0xFF2E7D32) else IntenseGreen
        val bgColorActionRight = if (forceOriginalTimer) Color(0xFF2E7D32) else IntenseGreen
        Box(Modifier.fillMaxSize().background(when { currentOffset < 0 -> bgColorActionLeft; currentOffset > 0 -> bgColorActionRight; else -> Color.Transparent }).padding(horizontal = 16.dp), contentAlignment = if (currentOffset < 0) Alignment.CenterEnd else Alignment.CenterStart) {
            if (currentOffset < 0) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxHeight().width(130.dp)) {
                    if (!session.isPaid) {
                        Text("CONFIRMAR (3X)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { paymentClicks++; if (paymentClicks >= 3) { try { cashPlayer?.start() } catch (_: Exception) {}; onPaidToggle(true); paymentClicks = 0; scope.launch { swipeState.animateTo(DragAnchors.Center) } } }.padding(8.dp)) { repeat(3) { index -> Icon(Icons.Default.MonetizationOn, null, tint = if (index < paymentClicks) Color.Black else Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(28.dp)) } }
                    } else {
                        Text("PAGO ANTECIPADO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPaidToggle(false); scope.launch { swipeState.animateTo(DragAnchors.Center) } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.height(32.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("EXCLUIR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            } else if (currentOffset > 0) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(if (forceOriginalTimer) Icons.Default.MonetizationOn else Icons.Default.Timer, null, tint = Color.Black); Spacer(Modifier.width(8.dp)); Text(if (forceOriginalTimer) "VER PAGAMENTO" else "VER CRONÔMETRO", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp) } }
        }
        val shouldShowPaidUI = session.isPaid && !forceOriginalTimer
        Card(
            modifier = Modifier.offset { IntOffset(x = currentOffset.roundToInt(), y = 0) }.anchoredDraggable(swipeState, orientation = Orientation.Horizontal).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = { showOptions = true }) }.border(width = if(session.isPaid) 2.dp else 1.dp, brush = if(session.isPaid) Brush.linearGradient(listOf(IntenseGreen, Color.Yellow)) else Brush.linearGradient(listOf(if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow, if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow)), shape = RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = if(shouldShowPaidUI) { if(isLightMode) Color(0xFFE8F5E9) else Color(0xFF0A1F0C) } else { if(isLightMode) Color(0xFFF0F0F0) else Color.Black }), shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.personName.uppercase(), color = if(session.isPaused) Color.Gray else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = highlightStyle)
                    if (shouldShowPaidUI) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { Icon(Icons.Default.CheckCircle, null, tint = IntenseGreen, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("PAGAMENTO ANTECIPADO", color = IntenseGreen, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }; Text("R$ %.2f".format(currentVal), color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                    else { Text(session.toyName, color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal); Text("VALOR: R$ %.2f".format(currentVal), color = if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp); if(session.isPaused) Text(if(session.isToyReleased) "PAUSADO (BRINQUEDO LIBERADO)" else "PAUSADO", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold); if (session.isPaid && forceOriginalTimer) { Text("PAGAMENTO CONFIRMADO", color = IntenseGreen, fontSize = 9.sp, fontWeight = FontWeight.Black) } }
                }
                if (!shouldShowPaidUI) { val min = session.remainingSeconds / 60; val sec = session.remainingSeconds % 60; Text("%02d:%02d".format(min, sec), color = if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 28.sp, style = if(!isExpired && !session.isPaused && !isLowTime) highlightStyle else TextStyle.Default) } else { MoneyAnimation(isLightMode) }
            }
        }
    }
    LaunchedEffect(swipeState.currentValue) { if (swipeState.currentValue == DragAnchors.Center) paymentClicks = 0; if (swipeState.currentValue == DragAnchors.End) { forceOriginalTimer = !forceOriginalTimer; scope.launch { swipeState.animateTo(DragAnchors.Center) } } }
    if (showPauseDialog) { AlertDialog(onDismissRequest = { showPauseDialog = false }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A) , title = { Text("LIBERAR BRINQUEDO?", color = IntenseGreen, fontWeight = FontWeight.Bold) }, text = { Text("Deseja liberar o brinquedo '${session.toyName}'?", color = if(isLightMode) Color.Black else Color.White) }, confirmButton = { TextButton(onClick = { onPauseToggle(true, true); showPauseDialog = false; showOptions = false }) { Text("SIM", color = IntenseGreen, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { onPauseToggle(true, false); showPauseDialog = false; showOptions = false }) { Text("NÃO", color = if(isLightMode) Color.Black else Color.Gray, fontWeight = FontWeight.Bold) } }) }
    if (showResetDialog != null) { val newToy = showResetDialog!!; AlertDialog(onDismissRequest = { showResetDialog = null }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A), title = { Text("REINICIAR?", color = IntenseGreen, fontWeight = FontWeight.Bold) }, text = { Text("Reiniciar para ${newToy.timeMinutes} min?", color = if(isLightMode) Color.Black else Color.White) }, confirmButton = { TextButton(onClick = { val earned = (session.elapsedSecondsInCurrentCycle.toDouble() / (session.toyTimeMinutes * 60.0).coerceAtLeast(1.0)) * session.toyPrice; onToyChanged(session.copy(toyName = newToy.name, toyPrice = newToy.price, toyTimeMinutes = newToy.timeMinutes, totalValueAccumulated = session.totalValueAccumulated + earned, elapsedSecondsInCurrentCycle = 0, remainingSeconds = (newToy.timeMinutes * 60).toLong(), isToyReleased = false)); showResetDialog = null }) { Text("SIM", color = IntenseGreen, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { val earned = (session.elapsedSecondsInCurrentCycle.toDouble() / (session.toyTimeMinutes * 60.0).coerceAtLeast(1.0)) * session.toyPrice; onToyChanged(session.copy(toyName = newToy.name, toyPrice = newToy.price, toyTimeMinutes = newToy.timeMinutes, totalValueAccumulated = session.totalValueAccumulated + earned, isToyReleased = false)); showResetDialog = null }) { Text("NÃO", color = if(isLightMode) Color.Black else Color.Gray, fontWeight = FontWeight.Bold) } }) }
    if (showOptions) { AlertDialog(onDismissRequest = { showOptions = false }, containerColor = if(isLightMode) Color.White else Color(0xFF212121), title = { Text("OPÇÕES", color = IntenseGreen, fontWeight = FontWeight.Black, style = highlightStyle) }, text = { Text("Ações para ${session.personName}:", color = if(isLightMode) Color.Black else Color.White) }, confirmButton = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { if (session.isPaused) { onPauseToggle(false, false); showOptions = false } else { showPauseDialog = true } }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(25.dp)) { Text(if(session.isPaused) "RETOMAR" else "PAUSAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Button(onClick = { showSwapPicker = true; showOptions = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Blue), shape = RoundedCornerShape(25.dp)) { Text("TROCAR", fontSize = 11.sp, fontWeight = FontWeight.Bold) } } }, dismissButton = { TextButton(onClick = { onFinish(session); showOptions = false }) { Text("ENCERRAR", color = Color.Red, fontWeight = FontWeight.ExtraBold) } }) }
    if (showSwapPicker) { AlertDialog(onDismissRequest = { showSwapPicker = false }, containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A), title = { Text("TROCAR BRINQUEDO", color = IntenseGreen, style = highlightStyle) }, text = { LazyColumn { items(allToys) { toy -> val isOccupied = !toy.isAlwaysFree && activeSessions.any { it.toyName == toy.name && !it.isFinished && !it.isToyReleased }; Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOccupied) { showResetDialog = toy; showSwapPicker = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(50.dp).background(if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF222222), RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            val bitmap = base64ToBitmap(toy.imageBase64)
            if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(Icons.Default.Toys, null, tint = IntenseGreen, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(toy.name.uppercase(), color = if(isOccupied) Color.Red else if(isLightMode) Color.Black else Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); if(isOccupied) Text("OCUPADO", color = Color.Red, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Black); Text("R$ %.2f".format(toy.price), color = if(isOccupied) Color.Red else IntenseGreen, fontWeight = FontWeight.Bold) } } } }, confirmButton = { TextButton(onClick = { showSwapPicker = false }) { Text("VOLTAR", color = IntenseGreen, fontWeight = FontWeight.Bold) } }) }
}

@Composable
fun MoneyAnimation(isLightMode: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "money")
    val scale by infiniteTransition.animateFloat(initialValue = 0.85f, targetValue = 1.2f, animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "scale")
    val alpha by infiniteTransition.animateFloat(initialValue = 0.5f, targetValue = 1.0f, animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "alpha")
    val rotation by infiniteTransition.animateFloat(initialValue = -10f, targetValue = 10f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "rotation")
    val tint = if (isLightMode) Color(0xFF1B5E20) else IntenseGreen
    Box(modifier = Modifier.size(60.dp).graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation), contentAlignment = Alignment.Center) { Box(modifier = Modifier.size(40.dp).graphicsLayer(scaleX = 1.5f, scaleY = 1.5f, alpha = alpha * 0.3f).background(tint, CircleShape)); Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = null, tint = tint, modifier = Modifier.fillMaxSize()) }
}
