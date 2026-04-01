package com.example.gerenciadordeparquinho.ui.screens

import android.app.DatePickerDialog
import android.media.MediaPlayer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.gerenciadordeparquinho.R
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.example.gerenciadordeparquinho.data.model.Toy
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Enum para os estados de arrastar (Swipe)
enum class DragAnchors {
    Start,
    Center,
    End
}

@Composable
fun HomeScreen(
    appName: String,
    isSoundEnabled: Boolean = true,
    printerMac: String = "",
    printerSize: String = "58mm",
    logoBase64: String? = null,
    isLightMode: Boolean = false,
    autoPrintEntrance: Boolean = false,
    onAutoPrintEntranceChange: (Boolean) -> Unit = {},
    autoPrintExit: Boolean = false,
    onAutoPrintExitChange: (Boolean) -> Unit = {},
    autoPrintSDR: Boolean = false,
    onAutoPrintSDRChange: (Boolean) -> Unit = {},
    autoPrintScannerSummary: Boolean = false,
    onAutoPrintScannerSummaryChange: (Boolean) -> Unit = {},
    isAdmin: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryColor = if (isLightMode) Color.DarkGray else Color.Gray
    val highlightStyle = getHighlightStyle(isLightMode)
    val buttonBorder = if (isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null

    var childName by rememberSaveable { mutableStateOf("") }
    var selectedToy by remember { mutableStateOf<Toy?>(null) }
    var showToyPicker by remember { mutableStateOf(false) }
    var showTicketsDialog by remember { mutableStateOf(false) }
    var showDuplicateNameDialog by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    
    val toys by db.toyDao().getAllToys().collectAsState(initial = emptyList())
    val activeSessionsFromDb by db.sessionDao().getActiveSessions().collectAsState(initial = emptyList())
    val mediaPlayer = remember { MediaPlayer.create(context, R.raw.admin_alert) }
    val clickPlayer = remember { MediaPlayer.create(context, R.raw.click) }

    // ALERTA SONORO, CONTAGEM REGRESSIVA E IMPRESSÃO AUTOMÁTICA DE SAÍDA (ESTÁVEL)
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
                            db.sessionDao().insertSession(session.copy(
                                remainingSeconds = newRemaining,
                                elapsedSecondsInCurrentCycle = session.elapsedSecondsInCurrentCycle + diffSeconds,
                                lastUpdateTimestamp = now
                            ))
                        }
                    } else {
                        // Tempo esgotado: Alerta Sonoro
                        if (isSoundEnabled) {
                            try { if (!mediaPlayer.isPlaying) mediaPlayer.start() } catch (_: Exception) {}
                        }
                        
                        // Impressão Automática de Saída
                        if (autoPrintExit && !session.notified && printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printEntranceTicket(printerMac, session, printerSize, logoBase64, appName)
                            db.sessionDao().insertSession(session.copy(notified = true, lastUpdateTimestamp = now))
                        } else {
                            db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now))
                        }
                    }
                } else if (session.isPaused) {
                    db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(appName.uppercase(), color = IntenseGreen, fontSize = 22.sp, fontWeight = FontWeight.Black, style = highlightStyle)
            Row {
                IconButton(onClick = { showScanner = true }) {
                    Icon(Icons.Default.QrCodeScanner, null, tint = IntenseGreen, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { showTicketsDialog = true }) {
                    Icon(Icons.Default.ConfirmationNumber, null, tint = IntenseGreen, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("NOME DA CRIANÇA", color = secondaryColor, fontWeight = FontWeight.Bold) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = textColor, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen,
                unfocusedBorderColor = secondaryColor,
                focusedLabelColor = IntenseGreen,
                cursorColor = IntenseGreen
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (isLightMode) Color(0xFFF0F0F0) else Color(0xFF1A1A1A)).clickable { showToyPicker = true }.border(1.dp, if (selectedToy != null) IntenseGreen else secondaryColor, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            Text(selectedToy?.name?.uppercase() ?: "SELECIONAR BRINQUEDO", color = if (selectedToy != null) textColor else secondaryColor, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ArrowDropDown, null, tint = IntenseGreen, modifier = Modifier.align(Alignment.CenterEnd))
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
                
                val exists = activeSessionsFromDb.any { it.personName.equals(childName.trim(), ignoreCase = true) }
                if (exists) {
                    showDuplicateNameDialog = true
                } else {
                    val t = selectedToy!!
                    val newSession = PlaySession(
                        personName = childName,
                        toyName = t.name,
                        toyPrice = t.price,
                        toyTimeMinutes = t.timeMinutes,
                        remainingSeconds = (t.timeMinutes * 60).toLong(),
                        lastUpdateTimestamp = System.currentTimeMillis()
                    )
                    scope.launch { 
                        db.sessionDao().insertSession(newSession)
                        
                        if (autoPrintEntrance && printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printEntranceTicket(printerMac, newSession, printerSize, logoBase64, appName)
                        }
                        if (autoPrintSDR && printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printSTRQRCode(printerMac, newSession, printerSize)
                        }
                    }
                    childName = ""; selectedToy = null
                }
            },
            enabled = childName.isNotBlank() && selectedToy != null,
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth().height(54.dp).graphicsLayer(scaleX = scale, scaleY = scale),
            colors = ButtonDefaults.buttonColors(containerColor = if (childName.isNotBlank() && selectedToy != null) IntenseGreen else Color(0xFF333333)),
            shape = RoundedCornerShape(27.dp),
            border = if(childName.isNotBlank() && selectedToy != null) buttonBorder else null
        ) {
            Text("INICIAR TEMPO", fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("CRONÔMETROS ATIVOS", color = IntenseGreen, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, style = highlightStyle)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(activeSessionsFromDb, key = { it.id }) { session ->
                ActiveTimerItemUI(
                    session = session,
                    allToys = toys,
                    activeSessions = activeSessionsFromDb,
                    printerMac = printerMac,
                    printerSize = printerSize,
                    logoBase64 = logoBase64,
                    appName = appName,
                    isLightMode = isLightMode,
                    onFinish = { s -> 
                        scope.launch { 
                            val finalSession = s.copy(
                                isFinished = true, 
                                endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                                totalValueAccumulated = s.calculateCurrentProportionalValue()
                            )
                            db.sessionDao().insertSession(finalSession)
                        }
                    },
                    onContinue = { s ->
                        scope.launch {
                            val updated = s.copy(
                                totalValueAccumulated = s.totalValueAccumulated + s.toyPrice,
                                elapsedSecondsInCurrentCycle = 0,
                                remainingSeconds = (s.toyTimeMinutes * 60).toLong(),
                                notified = false,
                                lastUpdateTimestamp = System.currentTimeMillis()
                            )
                            db.sessionDao().insertSession(updated)
                        }
                    },
                    onToyChanged = { updated -> scope.launch { db.sessionDao().insertSession(updated.copy(lastUpdateTimestamp = System.currentTimeMillis())) } },
                    onPauseToggle = { paused, releaseToy -> 
                        scope.launch { 
                            db.sessionDao().insertSession(session.copy(
                                isPaused = paused, 
                                isToyReleased = releaseToy,
                                lastUpdateTimestamp = System.currentTimeMillis()
                            )) 
                        } 
                    },
                    onPaidToggle = { paid ->
                        scope.launch {
                            db.sessionDao().insertSession(session.copy(isPaid = paid))
                        }
                    }
                )
            }
        }
    }

    // DIÁLOGO DE TEMPO ESGOTADO (NÍVEL RAIZ PARA PERSISTÊNCIA)
    val expiredSession = activeSessionsFromDb.find { it.remainingSeconds <= 0 && !it.isFinished && !it.isPaused }
    if (expiredSession != null) {
        val currentVal = expiredSession.calculateCurrentProportionalValue()
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("TEMPO ESGOTADO!", color = Color.Red, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
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
                            scope.launch {
                                val updated = expiredSession.copy(
                                    totalValueAccumulated = expiredSession.totalValueAccumulated + expiredSession.toyPrice,
                                    elapsedSecondsInCurrentCycle = 0,
                                    remainingSeconds = (expiredSession.toyTimeMinutes * 60).toLong(),
                                    notified = false,
                                    lastUpdateTimestamp = System.currentTimeMillis()
                                )
                                db.sessionDao().insertSession(updated)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(26.dp),
                        border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
                    ) {
                        Text("CONTINUAR (MAIS ${expiredSession.toyTimeMinutes} MIN)", fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { 
                            try { if (mediaPlayer.isPlaying) { mediaPlayer.pause(); mediaPlayer.seekTo(0) } } catch (_: Exception) {}
                            scope.launch {
                                val finalSession = expiredSession.copy(
                                    isFinished = true, 
                                    endTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                                    totalValueAccumulated = currentVal
                                )
                                db.sessionDao().insertSession(finalSession)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
                        shape = RoundedCornerShape(26.dp),
                        border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
                    ) {
                        Text("ENCERRAR E SALVAR", fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }
        )
    }

    if (showDuplicateNameDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateNameDialog = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("NOME EM USO!", color = Color.Red, fontWeight = FontWeight.Bold) },
            text = { Text("Já existe um tempo aberto para '$childName'. Por favor, adicione um complemento.", color = if(isLightMode) Color.Black else Color.White) },
            confirmButton = {
                TextButton(onClick = { showDuplicateNameDialog = false }) {
                    Text("ENTENDI", color = IntenseGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showTicketsDialog) {
        var selectedDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
        val ticketsOfDay by db.sessionDao().getSessionsByDate(selectedDate).collectAsState(initial = emptyList())
        var expandedChildName by remember { mutableStateOf<String?>(null) }

        // AGRUPAMENTO DOS TICKETS POR NOME
        val groupedTickets = remember(ticketsOfDay) {
            ticketsOfDay.groupBy { it.personName.trim().uppercase() }
        }

        AlertDialog(
            onDismissRequest = { showTicketsDialog = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TICKETS", color = IntenseGreen, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, style = highlightStyle)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(context, { _, y, m, d ->
                            val calSet = Calendar.getInstance(); calSet.set(y, m, d)
                            selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calSet.time)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) {
                        Icon(Icons.Default.CalendarToday, null, tint = IntenseGreen, modifier = Modifier.size(22.dp))
                    }
                }
            },
            text = {
                Column {
                    LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                        groupedTickets.forEach { (name, sessions) ->
                            item {
                                val totalValue = sessions.sumOf { it.totalValueAccumulated }
                                val isExpanded = expandedChildName == name
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { 
                                        expandedChildName = if (isExpanded) null else name 
                                    },
                                    colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF5F5F5) else Color(0xFF222222)),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.1f)) else null
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(if(isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = IntenseGreen, modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text(name, color = textColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                            }
                                            IconButton(onClick = { 
                                                if(printerMac.isNotEmpty()) BluetoothPrinterHelper.printChildSummary(printerMac, sessions.first().personName, sessions.first().date, sessions, totalValue, printerSize, logoBase64, appName) 
                                            }) { Icon(Icons.Default.Print, null, tint = IntenseGreen) }
                                        }
                                        
                                        if (isExpanded) {
                                            Spacer(Modifier.height(8.dp))
                                            sessions.forEach { s ->
                                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text(s.toyName.uppercase(), color = secondaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text("R$ %.2f".format(s.totalValueAccumulated), color = if(s.isPaid) IntenseGreen else Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                                }
                                            }
                                            
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = textColor.copy(alpha = 0.1f))
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("TOTAL DO DIA:", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                                Text("R$ %.2f".format(totalValue), color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    val diaTotal = ticketsOfDay.sumOf { it.totalValueAccumulated }
                    Spacer(Modifier.height(16.dp))
                    Text("TOTAL GERAL: R$ %.2f".format(diaTotal), color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
                LazyColumn {
                    items(toys) { toy ->
                        val isOccupied = !toy.isAlwaysFree && activeSessionsFromDb.any { 
                            it.toyName == toy.name && !it.isFinished && !it.isToyReleased 
                        }
                        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOccupied) { selectedToy = toy; showToyPicker = false }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(toy.name.uppercase(), color = if(isOccupied) Color.Red else textColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if(isOccupied) Text("OCUPADO", color = Color.Red, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Black)
                            Text("R$ %.2f".format(toy.price), color = if(isOccupied) Color.Red else IntenseGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showToyPicker = false }) { Text("FECHAR", color = IntenseGreen, fontWeight = FontWeight.Bold) } }
        )
    }

    if (showScanner) {
        QRScannerScreen(
            onClose = { showScanner = false },
            onScanResult = { qrData -> 
                showScanner = false
                // Lógica de processamento do resultado do scan aqui
            },
            autoPrint = autoPrintScannerSummary,
            onAutoPrintChange = onAutoPrintScannerSummaryChange,
            printerMac = printerMac,
            printerSize = printerSize,
            logoBase64 = logoBase64,
            appName = appName,
            isLightMode = isLightMode
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActiveTimerItemUI(
    session: PlaySession,
    allToys: List<Toy>,
    activeSessions: List<PlaySession>,
    printerMac: String,
    printerSize: String,
    logoBase64: String?,
    appName: String,
    isLightMode: Boolean,
    onFinish: (PlaySession) -> Unit,
    onContinue: (PlaySession) -> Unit,
    onToyChanged: (PlaySession) -> Unit,
    onPauseToggle: (Boolean, Boolean) -> Unit,
    onPaidToggle: (Boolean) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    var showSwapPicker by remember { mutableStateOf(false) }
    var showPauseDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf<Toy?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // NOVO ESTADO: Controla se forçamos a visualização do cronômetro original mesmo estando pago
    var forceOriginalTimer by rememberSaveable { mutableStateOf(false) }
    
    val isExpired = session.remainingSeconds <= 0L
    val isLowTime = session.remainingSeconds > 0 && session.remainingSeconds < 60L
    val currentVal = session.calculateCurrentProportionalValue()
    val highlightStyle = getHighlightStyle(isLightMode)
    val density = LocalDensity.current

    // Contador local para os 3 cliques de confirmação
    var paymentClicks by remember { mutableIntStateOf(0) }
    val cashPlayer = remember { MediaPlayer.create(context, R.raw.caixa) }

    // ESTADO DE SWIPE (DESLIZAR) CORRIGIDO PARA NOVA API
    val swipeState = remember {
        AnchoredDraggableState<DragAnchors>(
            initialValue = DragAnchors.Center,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = exponentialDecay()
        )
    }

    // Atualização dos âncoras (necessário para compilar sem erro)
    SideEffect {
        swipeState.updateAnchors(
            DraggableAnchors {
                DragAnchors.Start at -with(density) { 150.dp.toPx() }
                DragAnchors.Center at 0f
                DragAnchors.End at with(density) { 150.dp.toPx() }
            }
        )
    }
    
    val currentOffset = try { swipeState.requireOffset() } catch (_: Exception) { 0f }

    // Se o pagamento for cancelado externamente, resetamos a visualização forçada
    LaunchedEffect(session.isPaid) {
        if (!session.isPaid) forceOriginalTimer = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        // FUNDO: ÁREA DE AÇÃO REVELADA
        val bgColorActionLeft = if (session.isPaid) Color(0xFF2E7D32) else IntenseGreen
        val bgColorActionRight = if (forceOriginalTimer) Color(0xFF2E7D32) else IntenseGreen

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        currentOffset < 0 -> bgColorActionLeft
                        currentOffset > 0 -> bgColorActionRight
                        else -> Color.Transparent
                    }
                )
                .padding(horizontal = 16.dp),
            contentAlignment = if (currentOffset < 0) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            if (currentOffset < 0) {
                // ÁREA DE PAGAMENTO/EXCLUSÃO
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxHeight().width(130.dp)
                ) {
                    if (!session.isPaid) {
                        Text("CONFIRMAR (3X)", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    paymentClicks++
                                    if (paymentClicks >= 3) {
                                        try { cashPlayer?.start() } catch (_: Exception) {}
                                        onPaidToggle(true)
                                        paymentClicks = 0
                                        scope.launch { swipeState.animateTo(DragAnchors.Center) }
                                    }
                                }
                                .padding(8.dp)
                        ) {
                            repeat(3) { index ->
                                Icon(Icons.Default.MonetizationOn, null, tint = if (index < paymentClicks) Color.Black else Color.Black.copy(alpha = 0.3f), modifier = Modifier.size(28.dp))
                            }
                        }
                    } else {
                        Text("PAGO ANTECIPADO", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                onPaidToggle(false)
                                scope.launch { swipeState.animateTo(DragAnchors.Center) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("EXCLUIR", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (currentOffset > 0) {
                // ÁREA DE VOLTAR (MOSTRAR O CRONÔMETRO OU PAGAMENTO)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (forceOriginalTimer) Icons.Default.MonetizationOn else Icons.Default.Timer, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (forceOriginalTimer) "VER PAGAMENTO" else "VER CRONÔMETRO",
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Determina se mostramos a visualização de "Pago" ou a visualização "Original"
        val shouldShowPaidUI = session.isPaid && !forceOriginalTimer

        // CONTEÚDO DO CARD
        Card(
            modifier = Modifier
                .offset { IntOffset(x = currentOffset.roundToInt(), y = 0) }
                .anchoredDraggable(swipeState, orientation = Orientation.Horizontal)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { showOptions = true }) }
                .border(
                    width = if(session.isPaid) 2.dp else 1.dp,
                    brush = if(session.isPaid) Brush.linearGradient(listOf(IntenseGreen, Color.Yellow)) else Brush.linearGradient(listOf(if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow, if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow)),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = if(shouldShowPaidUI) {
                    if(isLightMode) Color(0xFFE8F5E9) else Color(0xFF0A1F0C)
                } else {
                    if(isLightMode) Color(0xFFF0F0F0) else Color.Black
                }
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.personName.uppercase(), color = if(session.isPaused) Color.Gray else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = highlightStyle)
                    
                    if (shouldShowPaidUI) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Icon(Icons.Default.CheckCircle, null, tint = IntenseGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PAGAMENTO ANTECIPADO", color = IntenseGreen, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                        }
                        Text("R$ %.2f".format(currentVal), color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    } else {
                        // CRONÔMETRO ORIGINAL (Mesmo se estiver pago, mas a visualização original for forçada)
                        Text(session.toyName, color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                        Text("VALOR: R$ %.2f".format(currentVal), color = if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else if(isLightMode) Color.Black else Color.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        if(session.isPaused) Text(if(session.isToyReleased) "PAUSADO (BRINQUEDO LIBERADO)" else "PAUSADO", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        
                        // Pequeno aviso se estiver pago mas mostrando o cronômetro
                        if (session.isPaid && forceOriginalTimer) {
                            Text("PAGAMENTO CONFIRMADO", color = IntenseGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                
                if (!shouldShowPaidUI) {
                    Spacer(Modifier.width(8.dp))
                    val min = session.remainingSeconds / 60
                    val sec = session.remainingSeconds % 60
                    Text("%02d:%02d".format(min, sec), color = if(session.isPaused) Color.Gray else if(isExpired || isLowTime) Color.Red else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 28.sp, style = if(!isExpired && !session.isPaused && !isLowTime) highlightStyle else TextStyle.Default)
                } else {
                    MoneyAnimation(isLightMode)
                }
            }
        }
    }

    // Lógica de detecção de fim de movimento
    LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue == DragAnchors.Center) {
            paymentClicks = 0
        }
        // Ao atingir o final do deslize para a direita, alternamos a visualização e voltamos ao centro
        if (swipeState.currentValue == DragAnchors.End) {
            forceOriginalTimer = !forceOriginalTimer
            scope.launch { swipeState.animateTo(DragAnchors.Center) }
        }
    }

    if (showPauseDialog) {
        AlertDialog(
            onDismissRequest = { showPauseDialog = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("LIBERAR BRINQUEDO?", color = IntenseGreen, fontWeight = FontWeight.Bold) },
            text = { Text("Deseja liberar o brinquedo '${session.toyName}' para outra criança enquanto o tempo está pausado?", color = if(isLightMode) Color.Black else Color.White) },
            confirmButton = {
                TextButton(onClick = { 
                    onPauseToggle(true, true)
                    showPauseDialog = false
                    showOptions = false 
                }) {
                    Text("SIM (LIBERAR)", color = IntenseGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    onPauseToggle(true, false)
                    showPauseDialog = false
                    showOptions = false 
                }) {
                    Text("NÃO (RESERVAR)", color = if(isLightMode) Color.Black else Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showResetDialog != null) {
        val newToy = showResetDialog!!
        AlertDialog(
            onDismissRequest = { showResetDialog = null },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("REINICIAR CRONÔMETRO?", color = IntenseGreen, fontWeight = FontWeight.Bold) },
            text = { Text("Deseja reiniciar o tempo para os ${newToy.timeMinutes} minutos originais do novo brinquedo?", color = if(isLightMode) Color.Black else Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    val earned = (session.elapsedSecondsInCurrentCycle.toDouble() / (session.toyTimeMinutes * 60.0).coerceAtLeast(1.0)) * session.toyPrice
                    onToyChanged(session.copy(
                        toyName = newToy.name, 
                        toyPrice = newToy.price, 
                        toyTimeMinutes = newToy.timeMinutes, 
                        totalValueAccumulated = session.totalValueAccumulated + earned,
                        elapsedSecondsInCurrentCycle = 0,
                        remainingSeconds = (newToy.timeMinutes * 60).toLong(),
                        isToyReleased = false
                    ))
                    showResetDialog = null
                }) {
                    Text("SIM (REINICIAR)", color = IntenseGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    val earned = (session.elapsedSecondsInCurrentCycle.toDouble() / (session.toyTimeMinutes * 60.0).coerceAtLeast(1.0)) * session.toyPrice
                    onToyChanged(session.copy(
                        toyName = newToy.name, 
                        toyPrice = newToy.price, 
                        toyTimeMinutes = newToy.timeMinutes, 
                        totalValueAccumulated = session.totalValueAccumulated + earned,
                        isToyReleased = false
                    ))
                    showResetDialog = null
                }) {
                    Text("NÃO (MANTER)", color = if(isLightMode) Color.Black else Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF212121),
            title = { Text("OPÇÕES", color = IntenseGreen, fontWeight = FontWeight.Black, style = highlightStyle) },
            text = { Text("Ações para ${session.personName}:", color = if(isLightMode) Color.Black else Color.White, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal) },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            if (session.isPaused) {
                                onPauseToggle(false, false)
                                showOptions = false
                            } else {
                                showPauseDialog = true
                            }
                        }, 
                        modifier = Modifier.weight(1f).height(50.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), 
                        shape = RoundedCornerShape(25.dp), 
                        border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
                    ) {
                        Text(if(session.isPaused) "RETOMAR" else "PAUSAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { 
                            showSwapPicker = true
                            showOptions = false 
                        }, 
                        modifier = Modifier.weight(1f).height(50.dp), 
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Blue), 
                        shape = RoundedCornerShape(25.dp), 
                        border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
                    ) {
                        Text("TROCAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { 
                TextButton(onClick = { 
                    onFinish(session)
                    showOptions = false 
                }) { 
                    Text("ENCERRAR AGORA", color = Color.Red, fontWeight = FontWeight.ExtraBold) 
                } 
            }
        )
    }

    if (showSwapPicker) {
        AlertDialog(
            onDismissRequest = { showSwapPicker = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("TROCAR BRINQUEDO", color = IntenseGreen, style = highlightStyle) },
            text = {
                LazyColumn {
                    items(allToys) { toy ->
                        val isOccupied = !toy.isAlwaysFree && activeSessions.any { it.toyName == toy.name && !it.isFinished && !it.isToyReleased }
                        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOccupied) {
                            showResetDialog = toy
                            showSwapPicker = false
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(toy.name.uppercase(), color = if(isOccupied) Color.Red else if(isLightMode) Color.Black else Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                            if(isOccupied) Text("OCUPADO", color = Color.Red, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Black)
                            Text("R$ %.2f".format(toy.price), color = if(isOccupied) Color.Red else IntenseGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { 
                TextButton(onClick = { showSwapPicker = false }) { 
                    Text("VOLTAR", color = IntenseGreen, fontWeight = FontWeight.Bold) 
                } 
            }
        )
    }
}

@Composable
fun MoneyAnimation(isLightMode: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "money")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    val tint = if (isLightMode) Color(0xFF1B5E20) else IntenseGreen

    Box(
        modifier = Modifier
            .size(60.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, rotationZ = rotation),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer(scaleX = 1.5f, scaleY = 1.5f, alpha = alpha * 0.3f)
                .background(tint, CircleShape)
        )
        Icon(
            imageVector = Icons.Default.MonetizationOn,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize()
        )
    }
}
