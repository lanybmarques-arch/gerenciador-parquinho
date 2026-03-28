package com.example.gerenciadordeparquinho.ui.screens

import android.app.DatePickerDialog
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

@Composable
fun HomeScreen(
    appName: String,
    isSoundEnabled: Boolean = true,
    printerMac: String = "",
    printerSize: String = "58mm",
    logoBase64: String? = null,
    isLightMode: Boolean = false
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
    var printTicketAutomatic by rememberSaveable { mutableStateOf(false) }
    var selectedToy by remember { mutableStateOf<Toy?>(null) }
    var showToyPicker by remember { mutableStateOf(false) }
    var showTicketsDialog by remember { mutableStateOf(false) }
    
    val toys by db.toyDao().getAllToys().collectAsState(initial = emptyList())
    val activeSessionsFromDb by db.sessionDao().getActiveSessions().collectAsState(initial = emptyList())
    val mediaPlayer = remember { MediaPlayer.create(context, R.raw.admin_alert) }

    LaunchedEffect(activeSessionsFromDb) {
        while (true) {
            delay(1000)
            val now = System.currentTimeMillis()
            activeSessionsFromDb.forEach { session ->
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
                        if (isSoundEnabled) {
                            try { if (mediaPlayer?.isPlaying == false) mediaPlayer.start() } catch (e: Exception) {}
                        }
                        db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now))
                    }
                } else if (session.isPaused) {
                    db.sessionDao().insertSession(session.copy(lastUpdateTimestamp = now))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(bgColor).padding(horizontal = 20.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CONTROLE", color = IntenseGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = highlightStyle)
            IconButton(onClick = { showTicketsDialog = true }) { 
                Icon(
                    imageVector = Icons.Default.Print, 
                    contentDescription = null, 
                    tint = if (isLightMode) Color.Black else IntenseGreen, 
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = childName,
            onValueChange = { childName = it },
            label = { Text("Nome da Criança", color = if(isLightMode) Color.Black else IntenseGreen, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IntenseGreen, 
                unfocusedBorderColor = if(isLightMode) Color.Black else IntenseGreen, 
                focusedTextColor = textColor, 
                unfocusedTextColor = textColor, 
                cursorColor = IntenseGreen
            ),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { showToyPicker = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
            shape = RoundedCornerShape(27.dp),
            border = buttonBorder
        ) {
            Text(selectedToy?.name?.uppercase() ?: "SELECIONAR BRINQUEDO", fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = printTicketAutomatic, onCheckedChange = { printTicketAutomatic = it }, colors = CheckboxDefaults.colors(checkedColor = IntenseGreen, checkmarkColor = Color.Black))
            Text("Imprimir Ticket Automático", color = textColor, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
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
                    if (printTicketAutomatic && printerMac.isNotEmpty()) {
                        BluetoothPrinterHelper.printEntranceTicket(printerMac, newSession, printerSize, logoBase64)
                    }
                }
                childName = ""; selectedToy = null
            },
            enabled = childName.isNotBlank() && selectedToy != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
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
                    onPauseToggle = { paused -> scope.launch { db.sessionDao().insertSession(session.copy(isPaused = paused, lastUpdateTimestamp = System.currentTimeMillis())) } }
                )
            }
        }
    }

    if (showTicketsDialog) {
        var selectedDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
        val ticketsOfDay by db.sessionDao().getSessionsByDate(selectedDate).collectAsState(initial = emptyList())

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
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(ticketsOfDay) { session ->
                            Column {
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.personName.uppercase(), color = textColor, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(session.toyName.uppercase(), color = secondaryColor, fontSize = 11.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                                    }
                                    Row {
                                        IconButton(onClick = { 
                                            if(printerMac.isNotEmpty()) BluetoothPrinterHelper.printEntranceTicket(printerMac, session, printerSize, logoBase64) 
                                        }) { Icon(Icons.Default.Print, null, tint = IntenseGreen) }
                                        IconButton(onClick = { scope.launch { db.sessionDao().deleteSession(session) } }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                                    }
                                }
                                HorizontalDivider(color = secondaryColor.copy(alpha = 0.5f))
                            }
                        }
                    }
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
                        val isOccupied = !toy.isAlwaysFree && activeSessionsFromDb.any { it.toyName == toy.name && !it.isFinished }
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
}

@Composable
fun ActiveTimerItemUI(
    session: PlaySession,
    allToys: List<Toy>,
    activeSessions: List<PlaySession>,
    printerMac: String,
    printerSize: String,
    logoBase64: String?,
    isLightMode: Boolean,
    onFinish: (PlaySession) -> Unit,
    onContinue: (PlaySession) -> Unit,
    onToyChanged: (PlaySession) -> Unit,
    onPauseToggle: (Boolean) -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    var showSwapPicker by remember { mutableStateOf(false) }
    val isExpired = session.remainingSeconds == 0L
    val currentVal = session.calculateCurrentProportionalValue()
    val highlightStyle = getHighlightStyle(isLightMode)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).pointerInput(Unit) { detectTapGestures(onDoubleTap = { showOptions = true }) }.border(1.dp, if(session.isPaused) Color.Gray else if(isExpired) Color.Red else if(isLightMode) Color.Black else Color.Yellow, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = if(isLightMode) Color(0xFFF0F0F0) else Color.Transparent),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.personName.uppercase(), color = if(session.isPaused) Color.Gray else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = highlightStyle)
                Text(session.toyName, color = if(isLightMode) Color.Black else Color.White, fontSize = 14.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                Text("VALOR: R$ %.2f".format(currentVal), color = if(session.isPaused) Color.Gray else if(isLightMode) Color.Black else Color.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                if(session.isPaused) Text("PAUSADO", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            val min = session.remainingSeconds / 60
            val sec = session.remainingSeconds % 60
            Text("%02d:%02d".format(min, sec), color = if(session.isPaused) Color.Gray else if(isExpired) Color.Red else IntenseGreen, fontWeight = FontWeight.Black, fontSize = 28.sp, style = if(!isExpired && !session.isPaused) highlightStyle else TextStyle.Default)
        }
    }

    if (isExpired && !session.isFinished && !session.isPaused) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            containerColor = if(isLightMode) Color.White else Color(0xFF1A1A1A),
            title = { Text("TEMPO ESGOTADO!", color = Color.Red, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(session.personName.uppercase(), color = if(isLightMode) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("VALOR A PAGAR:", color = if(isLightMode) Color.DarkGray else Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Black)
                    Text("R$ %.2f".format(currentVal), color = IntenseGreen, fontWeight = FontWeight.Black, fontSize = 48.sp, style = highlightStyle)
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onContinue(session) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                        shape = RoundedCornerShape(26.dp),
                        border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null
                    ) {
                        Text("CONTINUAR (MAIS ${session.toyTimeMinutes} MIN)", fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { onFinish(session) },
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

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            containerColor = if(isLightMode) Color.White else Color(0xFF212121),
            title = { Text("OPÇÕES", color = IntenseGreen, fontWeight = FontWeight.Black, style = highlightStyle) },
            text = { Text("Ações para ${session.personName}:", color = if(isLightMode) Color.Black else Color.White, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal) },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPauseToggle(!session.isPaused); showOptions = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black), shape = RoundedCornerShape(25.dp), border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null) {
                        Text(if(session.isPaused) "RETOMAR" else "PAUSAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { showSwapPicker = true; showOptions = false }, modifier = Modifier.weight(1f).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Blue), shape = RoundedCornerShape(25.dp), border = if(isLightMode) BorderStroke(1.dp, Color.Black.copy(alpha = 0.5f)) else null) {
                        Text("TROCAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = { 
                TextButton(onClick = { onFinish(session); showOptions = false }) { 
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
                        val isOccupied = !toy.isAlwaysFree && activeSessions.any { it.toyName == toy.name && !it.isFinished }
                        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOccupied) {
                            val earned = (session.elapsedSecondsInCurrentCycle.toDouble() / (session.toyTimeMinutes * 60.0).coerceAtLeast(1.0)) * session.toyPrice
                            onToyChanged(session.copy(
                                toyName = toy.name, 
                                toyPrice = toy.price, 
                                toyTimeMinutes = toy.timeMinutes, 
                                totalValueAccumulated = session.totalValueAccumulated + earned,
                                elapsedSecondsInCurrentCycle = 0,
                                remainingSeconds = (toy.timeMinutes * 60).toLong()
                            ))
                            showSwapPicker = false
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(toy.name.uppercase(), color = if(isOccupied) Color.Red else if(isLightMode) Color.Black else Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); 
                            if(isOccupied) Text("OCUPADO", color = Color.Red, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp), fontWeight = FontWeight.Black)
                            Text("R$ %.2f".format(toy.price), color = if(isOccupied) Color.Red else IntenseGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSwapPicker = false }) { Text("VOLTAR", color = IntenseGreen, fontWeight = FontWeight.Bold) } }
        )
    }
}
