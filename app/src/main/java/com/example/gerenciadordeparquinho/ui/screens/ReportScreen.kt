package com.example.gerenciadordeparquinho.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gerenciadordeparquinho.data.model.PlaySession
import com.example.gerenciadordeparquinho.data.repository.AppDatabase
import com.example.gerenciadordeparquinho.ui.theme.IntenseGreen
import com.example.gerenciadordeparquinho.ui.theme.getHighlightStyle
import com.example.gerenciadordeparquinho.utils.BluetoothPrinterHelper
import com.example.gerenciadordeparquinho.utils.normalizeName
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportScreen(
    printerMessage: String = "",
    printerMac: String = "",
    printerSize: String = "58mm",
    logoBase64: String? = null,
    onSearchClick: () -> Unit = {},
    isLightMode: Boolean = false,
    isAdmin: Boolean = false
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()
    val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
    
    val sessionsToday by db.sessionDao().getSessionsByDate(today).collectAsState(initial = emptyList())
    val totalApurado = sessionsToday.sumOf { it.totalValueAccumulated }
    
    var isTotalVisible by remember { mutableStateOf(false) }
    
    val bgColor = if (isLightMode) Color.White else Color.Black
    val textColor = if (isLightMode) Color.Black else Color.White
    val secondaryColor = if (isLightMode) Color.DarkGray else Color.Gray
    val highlightStyle = getHighlightStyle(isLightMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RELATÓRIO DO DIA",
                color = IntenseGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                style = highlightStyle
            )

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = if (isLightMode) Color.Black else IntenseGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = IntenseGreen),
            shape = RoundedCornerShape(20.dp),
            border = if (isLightMode) BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.4f)) else null
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("TOTAL APURADO", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        if (isTotalVisible) "R$ %.2f".format(totalApurado) else "****",
                        color = Color.Black,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = { isTotalVisible = !isTotalVisible }) {
                    Icon(
                        imageVector = if (isTotalVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (sessionsToday.isEmpty()) {
                Text("Nenhum registro hoje", color = secondaryColor, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp), fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
            }
            sessionsToday.forEach { session ->
                ReportItemUI(
                    session = session,
                    textColor = textColor,
                    secondaryColor = secondaryColor,
                    isLightMode = isLightMode,
                    isAdmin = isAdmin,
                    onPrint = {
                        if (printerMac.isNotEmpty()) {
                            BluetoothPrinterHelper.printEntranceTicket(
                                context = context,
                                macAddress = printerMac,
                                session = session,
                                size = printerSize,
                                logoBase64 = logoBase64,
                                customMessage = printerMessage
                            )
                        } else {
                            Toast.makeText(context, "Configure a impressora!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = {
                        scope.launch { db.sessionDao().deleteSession(session) }
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { /* Lógica PDF */ },
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(27.dp),
                border = if (isLightMode) BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.4f)) else null
            ) {
                Text("PDF / WHATS", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }

            Button(
                onClick = { 
                    if (printerMac.isNotEmpty()) {
                        BluetoothPrinterHelper.printReport(
                            context = context,
                            macAddress = printerMac,
                            history = sessionsToday,
                            total = totalApurado,
                            size = printerSize,
                            logoBase64 = logoBase64,
                            customMessage = printerMessage
                        )
                    } else {
                        Toast.makeText(context, "Configure a impressora!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IntenseGreen, contentColor = Color.Black),
                shape = RoundedCornerShape(27.dp),
                border = if (isLightMode) BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.4f)) else null
            ) {
                Text("IMPRIMIR", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ReportItemUI(
    session: PlaySession, 
    textColor: Color, 
    secondaryColor: Color, 
    isLightMode: Boolean, 
    isAdmin: Boolean,
    onPrint: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(session.personName.normalizeName(), color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Início: ${session.startTime} | Valor: R$ %.2f".format(session.totalValueAccumulated), color = secondaryColor, fontSize = 13.sp, fontWeight = if(isLightMode) FontWeight.Bold else FontWeight.Normal)
                    if (session.isPaid) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PAGO", 
                            color = IntenseGreen, 
                            fontWeight = FontWeight.Black, 
                            fontSize = 10.sp, 
                            modifier = Modifier
                                .border(1.dp, IntenseGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onPrint) {
                    Icon(Icons.Default.Print, null, tint = IntenseGreen, modifier = Modifier.size(24.dp))
                }
                if (isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = secondaryColor.copy(alpha = 0.3f), thickness = 0.5.dp)
    }
}
